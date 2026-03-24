package com.ticketing.domain.seat;


import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisSeatLockService implements SeatLockService {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final RedissonClient redissonClient;
    private final RedisSeatLockInternalService internalService;

    @Override
    public SeatHoldResult hold(int seatNo, Long audienceId) {
        RLock lock = redissonClient.getLock("seat:lock:" + seatNo);
        try {
            boolean acquired = lock.tryLock(5, -1, TimeUnit.SECONDS);
            if (!acquired) return SeatHoldResult.LOCK_TIMEOUT;

            try {
                return internalService.doHold(seatNo, audienceId);
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();  // 커밋 이후에 락 해제
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SeatHoldResult.LOCK_TIMEOUT;
        } catch (Exception e) {
            log.error("Redisson 락 처리 중 예상치 못한 에러 (seatNo={})", seatNo, e);
            return SeatHoldResult.FAIL;
        }
    }

    @Override
    public List<SeatResponse> getAllSeatsBySimulationId(Long simulationId) {
        return internalService.getAllSeatsBySimulationId(simulationId);
    }
}