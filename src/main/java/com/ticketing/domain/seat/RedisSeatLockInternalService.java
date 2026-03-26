package com.ticketing.domain.seat;

import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisSeatLockInternalService {

    private final SeatRepository seatRepository;
    private final AudienceRepository audienceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeatHoldResult doHold(Long seatId, Long audienceId) {
        Audience audience = audienceRepository.findById(audienceId)
                .orElse(null);
        if (audience == null) return SeatHoldResult.FAIL;

        Seat seat = seatRepository.findById(seatId)
                .orElse(null);
        if (seat == null) return SeatHoldResult.FAIL;
        if (!seat.isAvailable()) return SeatHoldResult.ALREADY_HELD;

        seat.hold(audienceId);
        seatRepository.save(seat);

        audience.addAcquiredSeat(seatId);
        audienceRepository.save(audience);

        return SeatHoldResult.SUCCESS;
    }

    public List<SeatResponse> getAllSeatsBySimulationId(Long simulationId) {
        List<SeatResponse> seatResponses = new ArrayList<>();
        seatRepository.findAllBySimulationId(simulationId).forEach(e -> seatResponses.add(new SeatResponse(e)));
        return seatResponses;
    }
}
