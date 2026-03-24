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
    public ShowResponse createShow(Show show) {
        // Show 저장
        show = showRepository.save(show);

        return new ShowResponse(show);
    }

    public Show getShow(Long showId) {
        return showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));
    }

    public List<Show> getAllShows() {
        return showRepository.findAll();
    }

    private List<Seat> generateSeats(Show show) {
        return show.getSeatSettingStrategy().generateSeats(show.getId(), show.getMaxRow(), show.getMaxCol());
    }
}
