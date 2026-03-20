package com.ticketing.domain.seat;

import com.esotericsoftware.kryo.util.Null;
import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceRepository;
import com.ticketing.domain.show.Show;
import com.ticketing.domain.show.ShowRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PessimisticSeatLockService implements SeatLockService {

    private final SeatRepository seatRepository;
    private final AudienceRepository audienceRepository;

    @Transactional
    @Override
    public SeatHoldResult hold(int seatNo, Long audienceId) {
        // 1. audience 유효성 검사
        Audience audience = audienceRepository.findById(audienceId)
                .orElse(null);
        if (audience == null) {
            return SeatHoldResult.FAIL;
        }

        // 2. 좌석 락 획득
        Seat seat = seatRepository.findByNoForUpdate(seatNo)
                .orElse(null);
        if (seat == null) {
            return SeatHoldResult.FAIL;
        }
        if (!seat.isAvailable()) {
            return SeatHoldResult.DUPLICATE;
        }

        // 3. 좌석 선점
        seat.hold(audienceId);
        seatRepository.save(seat);

        // 4. audience 결과 업데이트
        audience.getAcquiredSeatNos().add(seatNo);
        audienceRepository.save(audience);

        return SeatHoldResult.SUCCESS;
    }
}
