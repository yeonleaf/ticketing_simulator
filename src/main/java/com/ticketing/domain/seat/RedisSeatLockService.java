package com.ticketing.domain.seat;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RedisSeatLockService implements SeatLockService {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final RedissonClient redissonClient;
    private final RedisSeatLockInternalService internalService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SeatHoldResult hold(Long seatId, Long audienceId) {
        RLock lock = redissonClient.getLock("seat:lock:" + seatId);
        try {
            boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!acquired) return SeatHoldResult.LOCK_TIMEOUT;

            try {
                SeatHoldResultWrapper resultWrapper = internalService.doHold(seatId, audienceId);

                // 캐시에서 hold된 좌석 제거
                String key = "seats:available:" + resultWrapper.getSimulationId();
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

                return resultWrapper.getSeatHoldResult();
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();  // 커밋 이후에 락 해제
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SeatHoldResult.LOCK_TIMEOUT;
        } catch (Exception e) {
            log.error("Redisson 락 처리 중 예상치 못한 에러 (seatId={})", seatId, e);
            return SeatHoldResult.FAIL;
        }
    }

    @Override
    public List<SeatResponse> getAllSeatsBySimulationId(Long simulationId) {
        return internalService.getAllSeatsBySimulationId(simulationId);
    }
}