package com.ticketing.domain.show;

import com.ticketing.domain.seat.Seat;
import com.ticketing.domain.seat.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;

    /**
     * Show 생성 + Seat 일괄 생성 (strategy 기반 grade, hotScore 자동 계산)
     */
    @Transactional
    public Show createShow(Show show) {
        // TODO: Show 저장 후 Seat 일괄 생성
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Show getShow(Long showId) {
        return showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));
    }

    public List<Show> getAllShows() {
        return showRepository.findAll();
    }

    private List<Seat> generateSeats(Show show) {
        // TODO: SeatSettingStrategy 기반으로 모든 좌석 생성
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
