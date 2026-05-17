package com.ticketing.domain.simulation;

import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceDistributionStrategy;
import com.ticketing.domain.audience.AudienceRepository;
import com.ticketing.domain.audience.AudienceService;
import com.ticketing.domain.seat.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SimulationService {

    @Value("${spring.threads.virtual.enabled:false}")
    private boolean virtualThreadEnabled;

    private final Logger log = LoggerFactory.getLogger(getClass());
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
    public SimulationResponse createSimulation(SimulationRequest request) {
        log.info("=== [Simulation 생성 시작] ===");
        log.info("요청 정보: maxRow={}, maxCol={}, audienceCount={}, lockStrategy={}, seatSettingStrategy={}, audienceDistribution={}",
                request.getMaxRow(), request.getMaxCol(), request.getAudienceCount(),
                request.getLockStrategy(), request.getSeatSettingStrategy(), request.getAudienceDistributionStrategy());

        // Simulation 저장
        Simulation simulation = new Simulation(request);
        simulation.setVirtualThread(virtualThreadEnabled);
        if (simulation.getName() == null || simulation.getName().isBlank()) {
            String threadLabel = virtualThreadEnabled ? "VIRTUAL" : "PLATFORM";
            simulation.setName(simulation.getAudienceCount() + "VUS"
                    + "_" + simulation.getSeatSettingStrategy().name()
                    + "_" + simulation.getAudienceDistributionStrategy().name()
                    + "_" + simulation.getLockStrategy().name()
                    + "_" + threadLabel);
        }
        simulationRepository.save(simulation);

        // 좌석 생성 (simulation.getId()로 저장해야 시뮬레이션별 조회 가능)
        List<Seat> seats = simulation.getSeatSettingStrategy().generateSeats(simulation.getId(), simulation.getMaxRow(), simulation.getMaxCol());
        seats.forEach(seatRepository::save);  // save 후 각 Seat에 id 부여됨

        // 가상 Audience 생성
        int virtualAudienceCount = simulation.getAudienceCount();
        List<Audience> audiences = simulation.getAudienceDistributionStrategy().distribute(
                virtualAudienceCount, simulation.getId());
        audiences.forEach(audience -> {
            List<Long> preferredIds = audience.getStrategy()
                    .selectPreferred(seats, audience.getSeatCnt(), simulation.getMaxRow(), simulation.getMaxCol())
                    .stream().map(Seat::getId).toList();
            audience.setPreferredSeatIds(preferredIds);
        });
        audienceRepository.saveAll(audiences);

        log.info("=== [Simulation 생성 완료] simulationId={}, 좌석 {}개, 관객 {}명 ===",
                simulation.getId(), seats.size(), audiences.size());
        return new SimulationResponse(simulation, audiences, seats);
    }

    public List<Simulation> getAllSimulations() {
        return simulationRepository.findAll();
    }

    public Simulation getSimulation(Long simulationId) {
        return simulationRepository.findById(simulationId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simulationId));
    }

    public SimulationResponse startSimulation(Long simulationId) {
        log.info("=== [Simulation 시작] simulationId={} ===", simulationId);
        Simulation simulation = simulationRepository.findById(simulationId).orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simulationId));
        simulation.start();
        List<Audience> audiences = audienceRepository.findAllBySimulationId(simulationId);
        List<Seat> seats = seatRepository.findAllBySimulationId(simulationId);
        Simulation updatedSimulation = simulationRepository.save(simulation);
        log.info("=== [Simulation 시작 완료] simulationId={}, status={} ===", simulationId, updatedSimulation.getStatus());
        return new SimulationResponse(updatedSimulation, audiences, seats);
    }
    
    public SimulationResponse finishSimulation(Long simulationId, SimulationController.FinishRequest request) {
        log.info("=== [Simulation 종료 시작] simulationId={} ===", simulationId);
        log.info("메트릭 집계: holdsTotal={}, holdsSuccess={}, lockConflict={}, lockTimeout={}, userFullSuccess={}, userRollback={}, releaseSuccess={}",
                request.holdsTotal(), request.holdsSuccess(), request.lockConflict(), request.lockTimeout(),
                request.userFullSuccess(), request.userRollback(), request.releaseSuccess());

        simulationRepository.findById(simulationId).orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simulationId));
        List<Audience> audiences = audienceService.getAudiencesBySimulationId(simulationId);

        // 원하는 좌석을 전부 얻은 관객 수
        int fullySatisfiedCount = (int) audiences.stream()
                .filter(a -> !a.getPreferredSeatIds().isEmpty())
                .filter(a -> a.getAcquiredSeatIds().containsAll(a.getPreferredSeatIds()))
                .count();

        // 최소 1석이라도 얻은 관객 수
        int partiallySatisfiedCount = (int) audiences.stream()
                .filter(a -> !a.getAcquiredSeatIds().isEmpty())
                .count();

        // 아무것도 못 얻은 관객 수
        int unsatisfiedCount = (int) audiences.stream()
                .filter(a -> a.getAcquiredSeatIds().isEmpty())
                .count();

        List<Seat> seats = seatRepository.findAllBySimulationId(simulationId);

        log.info("관객 만족도 집계: 완전만족={}, 부분만족={}, 미충족={}", fullySatisfiedCount, partiallySatisfiedCount, unsatisfiedCount);

        Simulation finishedSimulation = simulationStatusService.updateSimulationStatusFinish(simulationId, request.totalTps(), request.avgResponseMs(), request.p90ResponseMs() != null ? request.p90ResponseMs() : 0L, request.p95ResponseMs() != null ? request.p95ResponseMs() : 0L, request.duplicateHoldCount(), request.holdsTotal(), request.holdsSuccess(), request.lockConflict(), request.lockTimeout(), fullySatisfiedCount, partiallySatisfiedCount, unsatisfiedCount, (int) request.userFullSuccess(), (int) request.userRollback(), (int) request.userTotalFail(), (int) request.seatsRolledBack(), (int) request.releaseSuccess(), (int) request.releaseFail());

        log.info("=== [Simulation 종료 완료] simulationId={}, status={} ===", simulationId, finishedSimulation.getStatus());
        return new SimulationResponse(finishedSimulation, audiences, seats);

    }

    public void  failSimulation(Long simulationId, SimulationController.FailRequest request) {
        simulationStatusService.updateSimulationStatusFail(simulationId, request.message());
    }

    public void interruptSimulation(Long simulationId) {
        Simulation simulation = simulationRepository.findById(simulationId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simulationId));
        simulation.interrupt();
        simulationRepository.save(simulation);
    }

    record RequestResult(SeatHoldResult holdResult, long responseMs) {}

    public List<SeatResponse> findEmptySeatsBySimulationId(Long simulationId) {
        return seatRepository.findEmptySeatsBySimulationId(simulationId).stream().map(SeatResponse::new).collect(Collectors.toList());
    }
 }
