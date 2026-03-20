package com.ticketing.domain.seat;


import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisSeatLockService implements SeatLockService {

    private final RedissonClient redissonClient;
    private final RedisSeatLockInternalService internalService;

    @Override
    public SeatHoldResult hold(int seatNo, Long audienceId) {
        RLock lock = redissonClient.getLock("seat:lock:" + seatNo);
        try {
            boolean acquired = lock.tryLock(5, 5, TimeUnit.SECONDS);
            if (!acquired) return SeatHoldResult.FAIL;

            try {
                return internalService.doHold(seatNo, audienceId);
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();  // 커밋 이후에 락 해제
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SeatHoldResult.FAIL;
        }
    }
}