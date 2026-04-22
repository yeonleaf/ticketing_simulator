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
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        // 캐시에서 hold된 좌석 제거
        String key = "seats:available:" + seat.getSimulationId();
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                List<SeatResponse> availableSeats = objectMapper.readValue(cached, new TypeReference<List<SeatResponse>>() {});
                List<SeatResponse> updatedSeats = availableSeats.stream()
                        .filter(s -> !s.getId().equals(seatId))
                        .collect(Collectors.toList());
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(updatedSeats));
            } catch (JsonProcessingException e) {
                log.warn("캐시 업데이트 실패 (seatId={})", seatId, e);
                // 캐시 업데이트 실패 시 캐시 삭제
                redisTemplate.delete(key);
            }
        }

        return SeatHoldResult.SUCCESS;
    }

    public List<SeatResponse> getAllSeatsBySimulationId(Long simulationId) {
        List<SeatResponse> seatResponses = new ArrayList<>();
        seatRepository.findAllBySimulationId(simulationId).forEach(e -> seatResponses.add(new SeatResponse(e)));
        return seatResponses;
    }
}
