package com.ticketing.domain.seat;

import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceRepository;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OptimisticSeatLockService implements SeatLockService {

    private final SeatRepository seatRepository;
    private final AudienceRepository audienceRepository;

    @Transactional
    public SeatHoldResult hold(int seatNo, Long audienceId) {
        try {
            Audience audience = audienceRepository.findById(audienceId)
                    .orElse(null);
            if (audience == null) return SeatHoldResult.FAIL;

            Seat seat = seatRepository.findByNo(seatNo)
                    .orElse(null);
            if (seat == null || !seat.isAvailable()) return SeatHoldResult.FAIL;

            seat.hold(audienceId);
            seatRepository.save(seat);  // @Version 충돌 시 예외

            audience.getAcquiredSeatNos().add(seatNo);
            audienceRepository.save(audience);  // @Version 충돌 시 예외

            return SeatHoldResult.SUCCESS;
        } catch (ObjectOptimisticLockingFailureException e) {
            return SeatHoldResult.DUPLICATE;
        } catch (Exception e) {
            return SeatHoldResult.FAIL;
        }
    }
}
