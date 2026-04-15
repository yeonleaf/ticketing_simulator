package com.ticketing.domain.seat;

import com.ticketing.domain.simulation.Simulation;
import com.ticketing.domain.simulation.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SeatController {

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
        Simulation simulation = simulationService.getSimulation(request.simulationId());
        SeatLockService seatService = switch (simulation.getLockStrategy()) {
            case PESSIMISTIC -> pessimisticSeatLockService;
            case OPTIMISTIC -> optimisticSeatLockService;
            case REDIS_REDISSON -> redisSeatLockService;
        };
        return ResponseEntity.ok(seatService.hold(seatId, request.audienceId()));
    }

    public record HoldRequest(Long audienceId, Long simulationId) {}
}
