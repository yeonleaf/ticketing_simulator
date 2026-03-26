package com.ticketing.domain.simulation;

import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceDistributionStrategy;
import com.ticketing.domain.audience.AudienceRepository;
import com.ticketing.domain.audience.AudienceService;
import com.ticketing.domain.seat.*;
import com.ticketing.domain.show.Show;
import com.ticketing.domain.show.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Transactional
public class SimulationService {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final OptimisticSeatLockService optimisticSeatLockService;
    private final PessimisticSeatLockService pessimisticSeatLockService;
    private final RedisSeatLockService redisSeatLockService;
    private final SimulationRepository simulationRepository;
    private final AudienceRepository audienceRepository;
    private final AudienceService audienceService;
    private final SimulationStatusService simulationStatusService;

    /**
     * Simulation 생성 (READY 상태) + 가상 Audience (audienceCount - 1)명 생성
     */
    @Transactional
    public Simulation createSimulation(Long showId, LockStrategy lockStrategy, ThreadStrategy threadStrategy,
                                       AudienceDistributionStrategy audienceDistributionStrategy) {
        // show 조회
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));

        // Simulation 저장
        Simulation simulation = simulationRepository.save(Simulation.builder()
                .showId(showId)
                .lockStrategy(lockStrategy)
                .threadStrategy(threadStrategy)
                .audienceDistributionStrategy(audienceDistributionStrategy)
                .build());

        // 좌석 생성 (simulation.getId()로 저장해야 시뮬레이션별 조회 가능)
        List<Seat> seats = show.getSeatSettingStrategy().generateSeats(simulation.getId(), show.getMaxRow(), show.getMaxCol());
        seats.forEach(seatRepository::save);  // save 후 각 Seat에 id 부여됨

        // 가상 Audience 생성
        int virtualAudienceCount = show.getAudienceCount();
        List<Audience> audiences = audienceDistributionStrategy.distribute(
                virtualAudienceCount, simulation.getId());
        audiences.forEach(audience -> {
            List<Long> preferredIds = audience.getStrategy()
                    .selectPreferred(seats, audience.getSeatCnt(), show.getMaxRow(), show.getMaxCol())
                    .stream().map(Seat::getId).toList();
            audience.setPreferredSeatIds(preferredIds);
        });
        audienceRepository.saveAll(audiences);

        return simulation;
    }

    /**
     * 시뮬레이션 실행 (READY → RUNNING)
     * 가상 관객들이 jitter 간격으로 /seats/{seatId}/hold API를 동시 호출
     * 완료 후 DONE으로 전환, 메트릭 집계
     */
    @Async
    public void startSimulation(Long simulationId) {
        doRunSimulation(simulationId);
    }

    public void runSimulationSync(Long simulationId) {
        doRunSimulation(simulationId);
    }

    private void doRunSimulation(Long simulationId) {
        try {
            Simulation simulation = simulationStatusService.updateSimulationStatusStart(simulationId);

            SeatLockService seatLockService = switch (simulation.getLockStrategy()) {
                case PESSIMISTIC -> pessimisticSeatLockService;
                case OPTIMISTIC -> optimisticSeatLockService;
                case REDIS_REDISSON -> redisSeatLockService;
            };

            // 가상 관객 소환
            List<Audience> audiences = audienceRepository.findAllBySimulationId(simulationId);
            if (audiences.isEmpty()) {
                throw new RuntimeException("가상 관객이 생성되지 않았습니다.");
            }

            // audiences들이 선호하는 좌석에 대해서 차례대로 hold를 시도한다.
            List<RequestResult> results = Collections.synchronizedList(new ArrayList<>());

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(audiences.size());

            List<Future<?>> futures = new ArrayList<>();

            try (
                    ExecutorService executor = switch (simulation.getThreadStrategy()) {
                        case VIRTUAL -> Executors.newVirtualThreadPerTaskExecutor();
                        case PLATFORM -> Executors.newFixedThreadPool(audiences.size());
                    }
                ) {
                for (Audience audience : audiences) {
                    Future<?> future = executor.submit(() -> {
                        try {
                            startLatch.await();
                            for (Long preferredSeatId : audience.getPreferredSeatIds()) {
                                Thread.sleep(audience.getSeatClickWaitJitter());
                                long start = System.currentTimeMillis();
                                SeatHoldResult result = seatLockService.hold(preferredSeatId, audience.getId());
                                if (result != null) {
                                    log.info("[Audience {}] seatId={} : {}", audience.getId(), preferredSeatId, result);
                                }
                                long responseMs = System.currentTimeMillis() - start;
                                results.add(new RequestResult(result, responseMs));
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            endLatch.countDown();
                        }
                    });
                    futures.add(future);
                }
                startLatch.countDown();
                endLatch.await();

                for (Future<?> future : futures) {
                    future.get();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }

            // 집계
            Instant finishedAt = Instant.now();
            long totalDurationMs = Duration.between(simulation.getStartedAt(), finishedAt).toMillis();
            double totalTps = results.size() / (totalDurationMs / 1000.0);

            long avgResponseMs = (long) results.stream()
                    .mapToLong(RequestResult::responseMs)
                    .average()
                    .orElse(0);

            int duplicateHoldCount = (int) results.stream()
                    .filter(r -> r.holdResult() == SeatHoldResult.ALREADY_HELD)
                    .count();

            // 락 서비스가 별도 트랜잭션으로 acquiredSeatIds를 DB에 저장했으므로 재조회
            List<Audience> freshAudiences = audienceService.getAudiencesBySimulationId(simulationId);

            // 원하는 좌석을 전부 얻은 관객 수
            int fullySatisfiedCount = (int) freshAudiences.stream()
                    .filter(a -> !a.getPreferredSeatIds().isEmpty())
                    .filter(a -> a.getAcquiredSeatIds().containsAll(a.getPreferredSeatIds()))
                    .count();

            // 최소 1석이라도 얻은 관객 수
            int partiallySatisfiedCount = (int) freshAudiences.stream()
                    .filter(a -> !a.getAcquiredSeatIds().isEmpty())
                    .count();

            // 아무것도 못 얻은 관객 수
            int unsatisfiedCount = (int) freshAudiences.stream()
                    .filter(a -> a.getAcquiredSeatIds().isEmpty())
                    .count();

            // 뒷정리
            simulationStatusService.updateSimulationStatusFinish(simulationId, totalTps, avgResponseMs, duplicateHoldCount, fullySatisfiedCount, partiallySatisfiedCount, unsatisfiedCount);
        } catch (Exception e) {
            simulationStatusService.updateSimulationStatusFail(simulationId, e.getMessage());
            throw e;
        }
    }

    public List<Simulation> getAllSimulations() {
        return simulationRepository.findAll();
    }

    public Simulation getSimulation(Long simulationId) {
        return simulationRepository.findById(simulationId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simulationId));
    }

    public List<Simulation> getSimulationsByShowId(Long showId) {
        return simulationRepository.findByShowId(showId);
    }

    public List<Simulation> getDoneSimulationsByShowId(Long showId) {
        return simulationRepository.findByShowIdAndStatus(showId, SimStatus.DONE);
    }

    record RequestResult(SeatHoldResult holdResult, long responseMs) {}

}
