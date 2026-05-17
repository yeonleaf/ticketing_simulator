package com.ticketing.domain.simulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceRepository;
import com.ticketing.domain.audience.AudienceResponse;
import com.ticketing.domain.seat.Seat;
import com.ticketing.domain.seat.SeatRepository;
import com.ticketing.domain.seat.SeatResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.AssignPublicIp;
import software.amazon.awssdk.services.ecs.model.KeyValuePair;
import software.amazon.awssdk.services.ecs.model.LaunchType;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    private final RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/api/simulations")
    public ResponseEntity<SimulationResponse> createSimulation(@ModelAttribute SimulationRequest request) {
        // deploy-k6.yml은 실행되어서는 안됨
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

    public record FinishRequest(int duplicateHoldCount,
                                long holdsTotal,
                                long holdsSuccess,
                                long lockConflict,
                                long lockTimeout,
                                Long totalTps,
                                Long avgResponseMs,
                                Long p90ResponseMs,
                                Long p95ResponseMs,
                                long userFullSuccess,
                                long userRollback,
                                long userTotalFail,
                                long seatsRolledBack,
                                long releaseSuccess,
                                long releaseFail
    ) {}

    @GetMapping("/api/simulations/{id}/seats/available")
    public ResponseEntity<List<SeatResponse>> getSeatsAvailable(@PathVariable Long id) {
        String key = "seats:available:" + id;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return ResponseEntity.ok(deserialize(cached));
        }
        List<SeatResponse> seats = simulationService.findEmptySeatsBySimulationId(id);
        redisTemplate.opsForValue().set(key, serialize(seats), Duration.ofSeconds(2));
        return ResponseEntity.ok(seats);
    }
    private String serialize(List<SeatResponse> seats) {
        try {
            return objectMapper.writeValueAsString(seats);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("직렬화 실패", e);
        }
    }

    private List<SeatResponse> deserialize(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<SeatResponse>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("역직렬화 실패", e);
        }
    }

    @PostMapping("/api/simulations/{id}/fail")
    public ResponseEntity<Void> failSimulation(@PathVariable Long id, @RequestBody FailRequest request) {
        simulationService.failSimulation(id, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/simulations/{id}/interrupt")
    public ResponseEntity<Void> interruptSimulation(@PathVariable Long id) {
        simulationService.interruptSimulation(id);
        return ResponseEntity.ok().build();
    }

    public record FailRequest(String message) {}

    @GetMapping("/api/simulations/export")
    public ResponseEntity<byte[]> exportSimulations(@RequestParam List<Long> ids) throws IOException {
        List<Simulation> simulations = ids.stream()
                .map(id -> simulationService.getSimulation(id))
                .collect(Collectors.toList());

        byte[] bytes = buildExcel(simulations);

        String filename = URLEncoder.encode("simulation_export.xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    private static final String[] HEADERS = {
            "ID", "이름", "락 전략", "스레드", "관객 분포", "좌석 배치",
            "좌석 수", "관객 수", "상태",
            "TPS", "평균 응답(ms)", "P90(ms)", "P95(ms)",
            "Hold 총 시도", "Hold 성공", "중복 선점", "Lock 충돌", "Lock 타임아웃",
            "완전 만족", "부분 만족", "미충족",
            "시작 시각", "종료 시각"
    };

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"));

    private byte[] buildExcel(List<Simulation> simulations) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("simulations");

            // 헤더 행
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // 데이터 행
            int rowNum = 1;
            for (Simulation s : simulations) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;
                row.createCell(col++).setCellValue(s.getId());
                row.createCell(col++).setCellValue(s.getName());
                row.createCell(col++).setCellValue(s.getLockStrategy().name());
                row.createCell(col++).setCellValue(s.isVirtualThread() ? "Virtual" : "Platform");
                row.createCell(col++).setCellValue(s.getAudienceDistributionStrategy().name());
                row.createCell(col++).setCellValue(s.getSeatSettingStrategy().name());
                row.createCell(col++).setCellValue(s.getMaxRow() + "×" + s.getMaxCol());
                row.createCell(col++).setCellValue(s.getAudienceCount());
                row.createCell(col++).setCellValue(s.getStatus().name());
                row.createCell(col++).setCellValue(s.getTotalTps());
                row.createCell(col++).setCellValue(s.getAvgResponseMs());
                row.createCell(col++).setCellValue(s.getP90ResponseMs());
                row.createCell(col++).setCellValue(s.getP95ResponseMs());
                row.createCell(col++).setCellValue(s.getHoldsTotal());
                row.createCell(col++).setCellValue(s.getHoldsSuccess());
                row.createCell(col++).setCellValue(s.getDuplicateHoldCount());
                row.createCell(col++).setCellValue(s.getLockConflict());
                row.createCell(col++).setCellValue(s.getLockTimeout());
                row.createCell(col++).setCellValue(s.getFullySatisfiedCount());
                row.createCell(col++).setCellValue(s.getPartiallySatisfiedCount());
                row.createCell(col++).setCellValue(s.getUnsatisfiedCount());
                row.createCell(col++).setCellValue(s.getStartedAt() != null ? DT_FMT.format(s.getStartedAt()) : "");
                row.createCell(col).setCellValue(s.getFinishedAt() != null ? DT_FMT.format(s.getFinishedAt()) : "");
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            wb.write(out);
            return out.toByteArray();
        }
    }
}