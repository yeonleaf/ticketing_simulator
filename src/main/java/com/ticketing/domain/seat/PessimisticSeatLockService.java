package com.ticketing.domain.seat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PessimisticSeatLockService implements SeatLockService {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final SeatRepository seatRepository;
    private final AudienceRepository audienceRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PessimisticSeatLockInternalService internalService;

    @Override
    public SeatHoldResult hold(Long seatId, Long audienceId) {
        try {
            SeatHoldResultWrapper resultWrapper = internalService.doHold(seatId, audienceId);

//            // 5. 캐시에서 hold된 좌석 제거
//            String key = "seats:available:" + resultWrapper.getSimulationId();
//            String cached = redisTemplate.opsForValue().get(key);
//            if (cached != null) {
//                try {
//                    List<SeatResponse> availableSeats = objectMapper.readValue(cached, new TypeReference<List<SeatResponse>>() {});
//                    List<SeatResponse> updatedSeats = availableSeats.stream()
//                            .filter(s -> !s.getId().equals(seatId))
//                            .collect(Collectors.toList());
//                    redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(updatedSeats));
//                } catch (JsonProcessingException e) {
//                    log.warn("캐시 업데이트 실패 (seatId={})", seatId, e);
//                    // 캐시 업데이트 실패 시 캐시 삭제
//                    redisTemplate.delete(key);
//                }
//            }

            log.info("hold 결과: seatId={}, audienceId={}, result={}", seatId, audienceId, resultWrapper.getSeatHoldResult());

            return resultWrapper.getSeatHoldResult();
        } catch (PessimisticLockingFailureException e) {
            log.warn("락 타임아웃: seatId={}, audienceId={}", seatId, audienceId);
            return SeatHoldResult.LOCK_TIMEOUT;
        } catch (Exception e) {
            log.error("hold 실패: seatId={}, audienceId={}", seatId, audienceId, e);
            return SeatHoldResult.FAIL;
        }
    }
    @Override
    public List<SeatResponse> getAllSeatsBySimulationId(Long simulationId) {
        List<SeatResponse> seatResponses = new ArrayList<>();
        seatRepository.findAllBySimulationId(simulationId).forEach(e -> seatResponses.add(new SeatResponse(e)));
        return seatResponses;
    }

    @Override
    public SeatReleaseResult release(Long seatId, Long audienceId) {
        try {
            SeatReleaseResultWrapper resultWrapper = internalService.doRelease(seatId, audienceId);

//            // 5. 캐시에서 hold된 좌석 제거
//            String key = "seats:available:" + resultWrapper.getSimulationId();
//            String cached = redisTemplate.opsForValue().get(key);
//            if (cached != null) {
//                try {
//                    List<SeatResponse> availableSeats = objectMapper.readValue(cached, new TypeReference<List<SeatResponse>>() {});
//                    List<SeatResponse> updatedSeats = availableSeats.stream()
//                            .filter(s -> !s.getId().equals(seatId))
//                            .collect(Collectors.toList());
//                    redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(updatedSeats));
//                } catch (JsonProcessingException e) {
//                    log.warn("캐시 업데이트 실패 (seatId={})", seatId, e);
//                    // 캐시 업데이트 실패 시 캐시 삭제
//                    redisTemplate.delete(key);
//                }
//            }

            log.info("release 결과: seatId={}, audienceId={}, result={}", seatId, audienceId, resultWrapper.getSeatReleaseResult());

            return resultWrapper.getSeatReleaseResult();
        } catch (PessimisticLockingFailureException e) {
            log.warn("release 락 타임아웃: seatId={}, audienceId={}", seatId, audienceId);
            return SeatReleaseResult.LOCK_TIMEOUT;
        } catch (Exception e) {
            log.error("release 실패: seatId={}, audienceId={}", seatId, audienceId, e);
            return SeatReleaseResult.FAIL;
        }
    }
}
