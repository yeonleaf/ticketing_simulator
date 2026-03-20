package com.ticketing.domain.simulation;

import com.ticketing.domain.audience.AudienceDistributionStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    /**
     * POST /api/shows/{showId}/simulations
     * Simulation 생성 (READY 상태) + 가상 Audience (audienceCount - 1)명 생성
     */
    @PostMapping("/api/shows/{showId}/simulations")
    public ResponseEntity<Simulation> createSimulation(
            @PathVariable Long showId,
            @RequestBody CreateSimulationRequest request) {
        return ResponseEntity.ok(simulationService.createSimulation(
                showId, request.lockStrategy(), request.audienceDistributionStrategy()));
    }

    /**
     * POST /api/simulations/{id}/start
     * 시뮬레이션 실행 (READY → RUNNING → DONE)
     */
    @PostMapping("/api/simulations/{id}/start")
    public ResponseEntity<Void> startSimulation(@PathVariable Long id) {
        simulationService.startSimulation(id);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/simulations/{id}/report
     * 시뮬레이션 결과 리포트 반환
     */
    @GetMapping("/api/simulations/{id}/report")
    public ResponseEntity<Simulation> getSimulationReport(@PathVariable Long id) {
        return ResponseEntity.ok(simulationService.getSimulation(id));
    }

    public record CreateSimulationRequest(
            LockStrategy lockStrategy,
            AudienceDistributionStrategy audienceDistributionStrategy) {}
}
