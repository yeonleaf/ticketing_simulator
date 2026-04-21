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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.AssignPublicIp;
import software.amazon.awssdk.services.ecs.model.KeyValuePair;
import software.amazon.awssdk.services.ecs.model.LaunchType;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Arrays;

@RestController
@RequiredArgsConstructor
public class SimulationController {

    private final SeatRepository seatRepository;
    private final AudienceRepository audienceRepository;
    private final SimulationService simulationService;
    @Value("${k6.base_url}")
    private String k6_base_url;

    @Value("${k6.cluster}")
    private String cluster;

    @Value("${k6.subnet}")
    private String subnet;

    @Value("${k6.task-definition}")
    private String k6TaskDefinition;

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

        EcsClient ecs = EcsClient.builder().region(Region.AP_SOUTHEAST_2).build();

        RunTaskRequest request = RunTaskRequest.builder()
                .cluster(cluster)
                .taskDefinition(k6TaskDefinition)
                .launchType(LaunchType.FARGATE)
                .networkConfiguration(n -> n.awsvpcConfiguration(v -> v
                        .subnets(Arrays.asList(subnet.split(",")))
                        .assignPublicIp(AssignPublicIp.ENABLED)))
                .overrides(o -> o.containerOverrides(c -> c
                        .name("k6")
                        .environment(
                                KeyValuePair.builder().name("SIM_ID").value(String.valueOf(simulationId)).build(),
                                KeyValuePair.builder().name("BASE_URL").value(k6_base_url).build(),
                                KeyValuePair.builder().name("TOT_VUS").value(String.valueOf(simulation.getAudienceCount())).build()
                        )))
                .build();
        ecs.runTask(request);

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