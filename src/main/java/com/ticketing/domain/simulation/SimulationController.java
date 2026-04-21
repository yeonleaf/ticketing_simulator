package com.ticketing.domain.simulation;

import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceRepository;
import com.ticketing.domain.audience.AudienceResponse;
import com.ticketing.domain.seat.Seat;
import com.ticketing.domain.seat.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class SimulationController {

    private final SeatRepository seatRepository;
    private final AudienceRepository audienceRepository;
    private final SimulationService simulationService;
    @Value("${k6.base_url}")
    private String k6_base_url;

    @PostMapping("/api/simulations")
    public ResponseEntity<SimulationResponse> createSimulation(@ModelAttribute SimulationRequest request) {
        return ResponseEntity.ok(simulationService.createSimulation(request));
    }

    @GetMapping("/api/simulations")
    public ResponseEntity<List<Simulation>> getAllSimulations() {
        return ResponseEntity.ok(simulationService.getAllSimulations());
    }

    @GetMapping("/api/simulations/{id}")
    public ResponseEntity<SimulationResponse> getSimulation(@PathVariable("id") Long simulationId) {
        Simulation simulation = simulationService.getSimulation(simulationId);
        List<Seat> seats = seatRepository.findAllBySimulationId(simulationId);
        List<Audience> audiences = audienceRepository.findAllBySimulationId(simulationId);
        return ResponseEntity.ok(new SimulationResponse(simulation, audiences, seats));
    }

    @PostMapping("/api/simulations/{id}/start")
    public ResponseEntity<SimulationResponse> startSimulation(@PathVariable("id") Long simulationId) throws IOException {
        Simulation simulation = simulationService.getSimulation(simulationId);

        ClassPathResource scriptResource = new ClassPathResource("scripts/script.js");
        Path tempScript = Files.createTempFile("k6-script", ".js");
        Files.copy(scriptResource.getInputStream(), tempScript, StandardCopyOption.REPLACE_EXISTING);
        ProcessBuilder pb = new ProcessBuilder(
                "k6", "run",
                "--env", "SIM_ID=" + simulationId,
                "--env", "BASE_URL=" + k6_base_url,
                "--env", "TOT_VUS=" + simulation.getAudienceCount(),
                tempScript.toAbsolutePath().toString()
        );
        pb.inheritIO();
        pb.start();
        return ResponseEntity.ok(simulationService.startSimulation(simulationId));
    }

    @PostMapping("/api/simulations/{id}/finish")
    public ResponseEntity<SimulationResponse> finishSimulation(@PathVariable("id") Long simulationId, @RequestBody FinishRequest request) {
        return ResponseEntity.ok(simulationService.finishSimulation(simulationId, request));
    }

    /**
     * GET /api/simulations/{id}/report
     * 시뮬레이션 결과 리포트 반환
     */
    @GetMapping("/api/simulations/{id}/report")
    public ResponseEntity<Simulation> getSimulationReport(@PathVariable Long id) {
        return ResponseEntity.ok(simulationService.getSimulation(id));
    }

    public record FinishRequest(int duplicateHoldCount, Long totalTps, Long avgResponseMs) {}
}