package com.ticketing.domain.simulation;

import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceDistributionStrategy;
import com.ticketing.domain.audience.AudienceRepository;
import com.ticketing.domain.payment.Payment;
import com.ticketing.domain.payment.PaymentRepository;
import com.ticketing.domain.seat.Seat;
import com.ticketing.domain.seat.SeatController;
import com.ticketing.domain.seat.SeatHoldResult;
import com.ticketing.domain.seat.SeatRepository;
import com.ticketing.domain.show.Show;
import com.ticketing.domain.show.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimulationService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final SimulationRepository simulationRepository;
    private final AudienceRepository audienceRepository;
    private final RestClient restClient;
    private final PaymentRepository paymentRepository;
    /**
     * Simulation 생성 (READY 상태) + 가상 Audience (audienceCount - 1)명 생성
     */
    @Transactional
    public Simulation createSimulation(Long showId, LockStrategy lockStrategy,
                                       AudienceDistributionStrategy audienceDistributionStrategy) {
        // show 조회
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));

        // Simulation 저장
        Simulation simulation = simulationRepository.save(Simulation.builder()
                .showId(showId)
                .lockStrategy(lockStrategy)
                .audienceDistributionStrategy(audienceDistributionStrategy)
                .build());

        // 좌석 조회
        List<Seat> seats = seatRepository.findAllByShowId(showId);

        // 가상 Audience 생성 (실제 유저 1명 제외)
        int virtualAudienceCount = show.getAudienceCount() - 1;
        List<Audience> audiences = audienceDistributionStrategy.distribute(
                virtualAudienceCount, simulation.getId());
        audienceRepository.saveAll(audiences);

        return simulation;
    }

    /**
     * 시뮬레이션 실행 (READY → RUNNING)
     * 가상 관객들이 jitter 간격으로 /seats/{seatNo}/hold API를 동시 호출
     * 완료 후 DONE으로 전환, 메트릭 집계
     */
    public void startSimulation(Long simulationId) {
        // TODO: 가상 관객 동시 요청 로직 (Virtual Threads 활용)
        Simulation simulation = simulationRepository.findById(simulationId).orElse(null);
        if (simulation == null) {
            throw new RuntimeException("잘못된 시뮬레이션 ID입니다.");
        }
        simulation.start();
        simulationRepository.save(simulation);

        // 가상 관객 소환
        List<Audience> audiences = audienceRepository.findAllBySimulationId(simulationId);
        if (audiences.isEmpty()) {
            throw new RuntimeException("가상 관객이 생성되지 않았습니다.");
        }

        // audiences들이 선호하는 좌석에 대해서 차례대로 hold를 시도한다.
        // hold가 끝난 후 획득한 좌석에 대해 결제 요청을 보낸다.

        List<RequestResult> results = Collections.synchronizedList(new ArrayList<>());

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(audiences.size());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Audience audience : audiences) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (Integer preferredSeatNo : audience.getPreferredSeatNos()) {
                            Thread.sleep(audience.getSeatClickWaitJitter());
                            long start = System.currentTimeMillis();
                            SeatHoldResult result = restClient.post()
                                    .uri("/api/seats/{seatNo}/hold", preferredSeatNo)
                                    .body(new SeatController.HoldRequest(audience.getId(), simulation.getId()))
                                    .retrieve()
                                    .body(SeatHoldResult.class);
                            long responseMs = System.currentTimeMillis() - start;
                            results.add(new RequestResult(result, responseMs));
                            if (result == SeatHoldResult.SUCCESS) {
                                audience.getAcquiredSeatNos().add(preferredSeatNo);
                            }
                        }
                        audienceRepository.save(audience);
                        if (!audience.getAcquiredSeatNos().isEmpty()) {
                            paymentRepository.save(new Payment(audience.getId(), audience.getAcquiredSeatNos()));
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        endLatch.countDown(); // 여기
                    }
                });
            }
            startLatch.countDown();
            endLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Instant finishedAt = Instant.now();
        long totalDurationMs = Duration.between(simulation.getStartedAt(), finishedAt).toMillis();
        double totalTps = results.size() / (totalDurationMs / 1000.0);

        long avgResponseMs = (long) results.stream()
                .mapToLong(RequestResult::responseMs)
                .average()
                .orElse(0);

        int duplicateHoldCount = (int) results.stream()
                .filter(r -> r.holdResult() == SeatHoldResult.DUPLICATE)
                .count();

        simulation.finish(totalTps, avgResponseMs, duplicateHoldCount);
        simulationRepository.save(simulation);

    }

    public Simulation getSimulation(Long simulationId) {
        return simulationRepository.findById(simulationId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simulationId));
    }

    public List<Audience> getSimulationAudiences(Long simulationId) {
        return audienceRepository.findAllBySimulationId(simulationId);
    }

    record RequestResult(SeatHoldResult holdResult, long responseMs) {}

}
