package com.ticketing.domain.seat;

import com.ticketing.domain.simulation.Simulation;
import com.ticketing.domain.simulation.SimulationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SeatController {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final PessimisticSeatLockService pessimisticSeatLockService;
    private final OptimisticSeatLockService optimisticSeatLockService;
    private final RedisSeatLockService redisSeatLockService;
    private final SimulationService simulationService;

    /**
     * GET /api/shows/{showId}/seats
     * 해당 공연의 전체 좌석 목록 (상태, 등급, hotScore 포함)
     */
    @GetMapping("/api/simulations/{simulationId}/seats")
    public ResponseEntity<List<SeatResponse>> getSeatsBySimulationId(
                                                             @PathVariable Long simulationId) {
        Simulation simulation = simulationService.getSimulation(simulationId);
        SeatLockService seatService = switch (simulation.getLockStrategy()) {
            case PESSIMISTIC -> pessimisticSeatLockService;
            case OPTIMISTIC -> optimisticSeatLockService;
            case REDIS_REDISSON -> redisSeatLockService;
        };
        return ResponseEntity.ok(seatService.getAllSeatsBySimulationId(simulationId));
    }

    /**
     * POST /api/seats/{seatId}/hold
     * 좌석 선점 시도 (여기서 락 경합 발생)
     */
    @PostMapping("/api/seats/{seatId}/hold")
    public ResponseEntity<SeatHoldResult> holdSeat(@PathVariable Long seatId, @RequestBody HoldRequest request) {
        log.debug("[Hold 요청] seatId={}, audienceId={}, simulationId={}", seatId, request.audienceId(), request.simulationId());
        Simulation simulation = simulationService.getSimulation(request.simulationId());
        SeatLockService seatService = switch (simulation.getLockStrategy()) {
            case PESSIMISTIC -> pessimisticSeatLockService;
            case OPTIMISTIC -> optimisticSeatLockService;
            case REDIS_REDISSON -> redisSeatLockService;
        };
        SeatHoldResult result = seatService.hold(seatId, request.audienceId());
        log.info("[Hold 결과] seatId={}, audienceId={}, result={}", seatId, request.audienceId(), result);
        return ResponseEntity.ok(result);
    }

    public record HoldRequest(Long audienceId, Long simulationId) {}

    @PostMapping("/api/seats/{seatId}/release")
    public ResponseEntity<SeatReleaseResult> releaseSeat(@PathVariable Long seatId, @RequestBody ReleaseRequest request) {
        log.debug("[Release 요청] seatId={}, audienceId={}, simulationId={}", seatId, request.audienceId, request.simulationId());
        Simulation simulation = simulationService.getSimulation(request.simulationId());
        SeatLockService seatService = switch (simulation.getLockStrategy()) {
            case PESSIMISTIC -> pessimisticSeatLockService;
            case OPTIMISTIC -> optimisticSeatLockService;
            case REDIS_REDISSON -> redisSeatLockService;
        };
        SeatReleaseResult result = seatService.release(seatId, request.audienceId);
        log.info("[Release 결과] seatId={}, audienceId={}, result={}", seatId, request.audienceId, result);
        return ResponseEntity.ok(result);
    }
    public record ReleaseRequest(Long audienceId, Long simulationId) {}

}
