package com.ticketing.domain.seat;

import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceRepository;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OptimisticSeatLockService implements SeatLockService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final SeatRepository seatRepository;
    private final AudienceRepository audienceRepository;

    @Transactional
    public SeatHoldResult hold(int seatNo, Long audienceId) {
        try {
            Audience audience = audienceRepository.findById(audienceId)
                    .orElse(null);
            if (audience == null) return SeatHoldResult.AUDIENCE_NOT_FOUND;

            Seat seat = seatRepository.findByNo(seatNo)
                    .orElse(null);
            if (seat == null) return SeatHoldResult.SEAT_NOT_FOUND;
            if (!seat.isAvailable()) return SeatHoldResult.ALREADY_HELD;

            seat.hold(audienceId);
            seatRepository.save(seat);  // @Version 충돌 시 예외

            audience.getAcquiredSeatNos().add(seatNo);
            audienceRepository.save(audience);  // @Version 충돌 시 예외

            return SeatHoldResult.SUCCESS;
        } catch (ObjectOptimisticLockingFailureException e) {
            return SeatHoldResult.LOCK_CONFLICT;
        } catch (Exception e) {
            log.error("좌석 선점 중 예상치 못한 에러 발생 (seatNo={})", seatNo, e);
            return SeatHoldResult.FAIL;
        }
    }

    @Override
    public List<SeatResponse> getAllSeatsBySimulationId(Long simulationId) {
        List<SeatResponse> seatResponses = new ArrayList<>();
        seatRepository.findAllBySimulationId(simulationId).forEach(e -> seatResponses.add(new SeatResponse(e)));
        return seatResponses;
    }
}
