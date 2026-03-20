package com.ticketing.domain.show;

import com.ticketing.domain.seat.SeatSettingStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;

    /**
     * POST /api/shows
     * Show 생성 + Seat 일괄 생성 (strategy 기반으로 grade, hotScore 자동 계산)
     */
    @PostMapping
    public ResponseEntity<Show> createShow(@RequestBody CreateShowRequest request) {
        Show show = Show.builder()
                .name(request.name())
                .maxRow(request.maxRow())
                .maxCol(request.maxCol())
                .audienceCount(request.audienceCount())
                .seatSettingStrategy(request.seatSettingStrategy())
                .build();
        return ResponseEntity.ok(showService.createShow(show));
    }

    @GetMapping
    public ResponseEntity<List<Show>> getAllShows() {
        return ResponseEntity.ok(showService.getAllShows());
    }

    @GetMapping("/{showId}")
    public ResponseEntity<Show> getShow(@PathVariable Long showId) {
        return ResponseEntity.ok(showService.getShow(showId));
    }

    public record CreateShowRequest(
            String name,
            int maxRow,
            int maxCol,
            int audienceCount,
            long seatHoldingTimeSeconds,
            SeatSettingStrategy seatSettingStrategy
    ) {}
}
