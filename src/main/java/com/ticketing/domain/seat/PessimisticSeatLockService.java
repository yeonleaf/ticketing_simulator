package com.ticketing.domain.seat;

import com.esotericsoftware.kryo.util.Null;
import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceRepository;
import com.ticketing.domain.show.Show;
import com.ticketing.domain.show.ShowRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PessimisticSeatLockService implements SeatLockService {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final SeatRepository seatRepository;
    private final AudienceRepository audienceRepository;

    @Transactional
    @Override
    public SeatHoldResult hold(int seatNo, Long audienceId) {
        // 1. audience 유효성 검사
        Audience audience = audienceRepository.findById(audienceId)
                .orElse(null);
        if (audience == null) {
            return SeatHoldResult.AUDIENCE_NOT_FOUND;
        }

        // 2. 좌석 락 획득
        Seat seat;

        try {
            seat = seatRepository.findByNoForUpdate(seatNo)
                    .orElse(null);

        } catch (PessimisticLockingFailureException pe) {
            return SeatHoldResult.LOCK_TIMEOUT;
        } catch (Exception e) {
            log.error("예상치 못한 에러 (seatNo={})", seatNo, e);
            return SeatHoldResult.FAIL;
        }

        if (seat == null) {
            return SeatHoldResult.SEAT_NOT_FOUND;
        }
        if (!seat.isAvailable()) {
            return SeatHoldResult.ALREADY_HELD;
        }

        // 3. 좌석 선점
        seat.hold(audienceId);
        seatRepository.save(seat);

        // 4. audience 결과 업데이트
        audience.getAcquiredSeatNos().add(seatNo);
        audienceRepository.save(audience);

        return SeatHoldResult.SUCCESS;
    }

    @Override
    public List<SeatResponse> getAllSeatsBySimulationId(Long simulationId) {
        List<SeatResponse> seatResponses = new ArrayList<>();
        seatRepository.findAllBySimulationId(simulationId).forEach(e -> seatResponses.add(new SeatResponse(e)));
        return seatResponses;
    }
}
