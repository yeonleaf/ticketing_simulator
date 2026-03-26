package com.ticketing.domain.simulation;

import com.ticketing.domain.seat.Seat;
import com.ticketing.domain.seat.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SimulationController {

    private final SeatRepository seatRepository;
    private final SimulationService simulationService;

    @GetMapping("/api/simulations")
    public ResponseEntity<List<Simulation>> getAllSimulations() {
        return ResponseEntity.ok(simulationService.getAllSimulations());
    }

    @GetMapping("/api/simulations/compare")
    public ResponseEntity<List<Simulation>> getCompare(@RequestParam Long showId) {
        return ResponseEntity.ok(simulationService.getDoneSimulationsByShowId(showId));
    }

    @GetMapping("/api/simulations/{id}")
    public ResponseEntity<SimulationResponse> getSimulation(@PathVariable Long id) {
        Simulation simulation = simulationService.getSimulation(id);
        List<Seat> seats = seatRepository.findAllBySimulationId(id);
        return ResponseEntity.ok(new SimulationResponse(simulation, seats));
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
}