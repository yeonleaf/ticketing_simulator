package com.ticketing.domain.seat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OptimisticSeatLockService implements SeatLockService {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final SeatRepository seatRepository;
    private final AudienceRepository audienceRepository;
    private final PlatformTransactionManager txManager;

    private TransactionTemplate transactionTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    void init() {
        transactionTemplate = new TransactionTemplate(txManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public SeatHoldResult hold(Long seatId, Long audienceId) {
        try {
            // TransactionTemplate: 예외 발생 시 트랜잭션을 rollback 후 re-throw
            // → 트랜잭션 경계 밖에서 예외를 catch하므로 UnexpectedRollbackException 없음
            SeatHoldResultWrapper resultWrapper = transactionTemplate.execute(status -> {
                Audience audience = audienceRepository.findById(audienceId)
                        .orElse(null);
                if (audience == null) return new SeatHoldResultWrapper(SeatHoldResult.AUDIENCE_NOT_FOUND, null);

                Seat seat = seatRepository.findById(seatId)
                        .orElse(null);
                if (seat == null) return new SeatHoldResultWrapper(SeatHoldResult.SEAT_NOT_FOUND, null);
                if (!seat.isAvailable())
                    return new SeatHoldResultWrapper(SeatHoldResult.ALREADY_HELD, seat.getSimulationId());

                int affected = seatRepository.updateIfVersionMatches(
                        seat.getId(),
                        seat.getVersion(),
                        SeatStatus.HELD.name(),
                        audienceId
                );

                log.info("UPDATE attempt: seatId={}, version={}, status={}, affected={}",
                        seat.getId(), seat.getVersion(), SeatStatus.HELD.name(), affected);

                if (affected == 0) {
                    return new SeatHoldResultWrapper(SeatHoldResult.LOCK_CONFLICT, seat.getSimulationId());
                }

                audienceRepository.insertAcquiredSeat(audienceId, seat.getId());
                return new SeatHoldResultWrapper(SeatHoldResult.SUCCESS, seat.getSimulationId());
            });

            // 캐시에서 hold된 좌석 제거
            assert resultWrapper != null;

            if (resultWrapper.getSeatHoldResult() == SeatHoldResult.SUCCESS) {
                String key = "seats:available:" + resultWrapper.getSimulationId();
                String cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    try {
                        List<SeatResponse> availableSeats = objectMapper.readValue(cached, new TypeReference<List<SeatResponse>>() {
                        });
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
            }

            return resultWrapper.getSeatHoldResult();
        } catch (ObjectOptimisticLockingFailureException | PessimisticLockingFailureException op) {
            return SeatHoldResult.LOCK_CONFLICT;
        } catch (Exception e) {
            log.error("좌석 선점 중 예상치 못한 에러 발생 (seatId={})", seatId, e);
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
