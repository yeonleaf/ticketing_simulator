package com.ticketing.domain.seat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RedisSeatLockInternalService {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final SeatRepository seatRepository;
    private final AudienceRepository audienceRepository;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeatHoldResultWrapper doHold(Long seatId, Long audienceId) {
        Audience audience = audienceRepository.findById(audienceId)
                .orElse(null);
        if (audience == null) return new SeatHoldResultWrapper(SeatHoldResult.FAIL, null);

        Seat seat = seatRepository.findById(seatId)
                .orElse(null);
        if (seat == null) return new SeatHoldResultWrapper(SeatHoldResult.FAIL, null);
        if (!seat.isAvailable()) return new SeatHoldResultWrapper(SeatHoldResult.ALREADY_HELD, seat.getSimulationId());

        seat.hold(audienceId);
        seatRepository.save(seat);

        audience.addAcquiredSeat(seatId);
        audienceRepository.save(audience);

        return new SeatHoldResultWrapper(SeatHoldResult.SUCCESS, seat.getSimulationId());
    }

    public List<SeatResponse> getAllSeatsBySimulationId(Long simulationId) {
        List<SeatResponse> seatResponses = new ArrayList<>();
        seatRepository.findAllBySimulationId(simulationId).forEach(e -> seatResponses.add(new SeatResponse(e)));
        return seatResponses;
    }
}
