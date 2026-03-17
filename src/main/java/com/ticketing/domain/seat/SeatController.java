package com.ticketing.domain.seat;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    /**
     * GET /api/shows/{showId}/seats
     * 해당 공연의 전체 좌석 목록 (상태, 등급, hotScore 포함)
     */
    @GetMapping("/api/shows/{showId}/seats")
    public ResponseEntity<List<Seat>> getSeatsByShow(@PathVariable Long showId) {
        return ResponseEntity.ok(seatService.getSeatsByShow(showId));
    }

    /**
     * POST /api/seats/{seatNo}/hold
     * 좌석 선점 시도 (여기서 락 경합 발생)
     */
    @PostMapping("/api/seats/{seatNo}/hold")
    public ResponseEntity<Seat> holdSeat(@PathVariable int seatNo, @RequestBody HoldRequest request) {
        return ResponseEntity.ok(seatService.holdSeat(seatNo, request.audienceId()));
    }

    public record HoldRequest(Long audienceId) {}
}
