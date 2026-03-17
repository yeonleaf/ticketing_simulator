package com.ticketing.domain.seat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatService {

    private final SeatRepository seatRepository;

    public List<Seat> getSeatsByShow(Long showId) {
        return seatRepository.findAllByShowId(showId);
    }

    /**
     * 좌석 선점 시도 (여기서 락 경합 발생)
     * 성공: seatStatus=HELD, holderId 세팅, holdExpireAt 세팅
     * 실패: 이미 HELD 상태면 실패 응답
     */
    @Transactional
    public Seat holdSeat(int seatNo, Long audienceId) {
        // TODO: 락 전략 분기 로직 구현
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
