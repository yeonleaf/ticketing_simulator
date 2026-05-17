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
        log.debug("[Redis Hold 시작] seatId={}, audienceId={}", seatId, audienceId);
        RLock lock = redissonClient.getLock("seat:lock:" + seatId);
        try {
            log.debug("[Redis Hold] 분산 락 획득 시도 - seatId={}", seatId);
            boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("[Redis Hold 실패] 락 타임아웃 - seatId={}", seatId);
                return SeatHoldResult.LOCK_TIMEOUT;
            }

            try {
                log.debug("[Redis Hold] 분산 락 획득 성공, 내부 트랜잭션 시작 - seatId={}", seatId);
                SeatHoldResultWrapper resultWrapper = internalService.doHold(seatId, audienceId);

//                if (resultWrapper.getSeatHoldResult() == SeatHoldResult.SUCCESS) {
//                    // 캐시에서 hold된 좌석 제거
//                    String key = "seats:available:" + resultWrapper.getSimulationId();
//                    String cached = redisTemplate.opsForValue().get(key);
//                    if (cached != null) {
//                        try {
//                            List<SeatResponse> availableSeats = objectMapper.readValue(cached, new TypeReference<List<SeatResponse>>() {});
//                            List<SeatResponse> updatedSeats = availableSeats.stream()
//                                    .filter(s -> !s.getId().equals(seatId))
//                                    .collect(Collectors.toList());
//                            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(updatedSeats));
//                        } catch (JsonProcessingException e) {
//                            log.warn("캐시 업데이트 실패 (seatId={})", seatId, e);
//                            // 캐시 업데이트 실패 시 캐시 삭제
//                            redisTemplate.delete(key);
//                        }
//                    }
//                }

                log.debug("[Redis Hold] 내부 트랜잭션 완료, 분산 락 해제 - seatId={}, result={}", seatId, resultWrapper.getSeatHoldResult());
                return resultWrapper.getSeatHoldResult();
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();  // 커밋 이후에 락 해제
                    log.debug("[Redis Hold] 분산 락 해제 완료 - seatId={}", seatId);
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

    @Override
    public SeatReleaseResult release(Long seatId, Long audienceId) {
        log.debug("[Redis Release 시작] seatId={}, audienceId={}", seatId, audienceId);
        RLock lock = redissonClient.getLock("seat:lock:" + seatId);
        try {
            log.debug("[Redis Release] 분산 락 획득 시도 - seatId={}", seatId);
            boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("[Redis Release 실패] 락 타임아웃 - seatId={}", seatId);
                return SeatReleaseResult.LOCK_TIMEOUT;
            }

            try {
                log.debug("[Redis Release] 분산 락 획득 성공, 내부 트랜잭션 시작 - seatId={}", seatId);
                SeatReleaseResultWrapper resultWrapper = internalService.doRelease(seatId, audienceId);
                log.debug("[Redis Release] 내부 트랜잭션 완료, 분산 락 해제 - seatId={}, result={}", seatId, resultWrapper.getSeatReleaseResult());
                return resultWrapper.getSeatReleaseResult();
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();  // 커밋 이후에 락 해제
                    log.debug("[Redis Release] 분산 락 해제 완료 - seatId={}", seatId);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SeatReleaseResult.LOCK_TIMEOUT;
        } catch (Exception e) {
            log.error("Redisson 락 처리 중 예상치 못한 에러 (seatId={})", seatId, e);
            return SeatReleaseResult.FAIL;
        }
    }
}