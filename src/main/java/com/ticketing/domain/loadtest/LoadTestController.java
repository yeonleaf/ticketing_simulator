package com.ticketing.domain.loadtest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class LoadTestController {

    private final LoadTestService loadTestService;

    /**
     * POST /api/load-tests
     * 기존 Show를 기반으로 관객 수만 바꿔 LoadTest 생성 + 비동기 실행. loadTestId 즉시 반환.
     */
    @PostMapping("/api/load-tests")
    public ResponseEntity<Map<String, Long>> createAndRun(@RequestBody LoadTestRequest request) {
        LoadTest lt = loadTestService.createLoadTest(
                request.showId(), request.startAudience(), request.endAudience(), request.step());
        loadTestService.runLoadTest(lt.getId());
        return ResponseEntity.ok(Map.of("loadTestId", lt.getId()));
    }

    /**
     * GET /api/load-tests/{loadTestId}/report
     * audience_count별 × 전략별 시뮬레이션 결과 반환
     */
    @GetMapping("/api/load-tests/{loadTestId}/report")
    public ResponseEntity<LoadTestReportDto> getReport(@PathVariable Long loadTestId) {
        return ResponseEntity.ok(loadTestService.getReport(loadTestId));
    }

    public record LoadTestRequest(Long showId, int startAudience, int endAudience, int step) {}
}
