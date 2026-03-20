package com.ticketing.domain.seat;

import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RedisSeatLockInternalService {

    private final SeatRepository seatRepository;
    private final AudienceRepository audienceRepository;

    @Transactional
    public SeatHoldResult doHold(int seatNo, Long audienceId) {
        Audience audience = audienceRepository.findById(audienceId)
                .orElse(null);
        if (audience == null) return SeatHoldResult.FAIL;

        Seat seat = seatRepository.findByNo(seatNo)
                .orElse(null);
        if (seat == null) return SeatHoldResult.FAIL;
        if (!seat.isAvailable()) return SeatHoldResult.DUPLICATE;

        seat.hold(audienceId);
        seatRepository.save(seat);

        audience.getAcquiredSeatNos().add(seatNo);
        audienceRepository.save(audience);

        return SeatHoldResult.SUCCESS;
    }
}
