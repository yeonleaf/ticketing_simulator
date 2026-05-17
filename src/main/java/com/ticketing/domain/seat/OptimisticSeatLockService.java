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
        log.debug("[Optimistic Hold 시작] seatId={}, audienceId={}", seatId, audienceId);
        try {
            // TransactionTemplate: 예외 발생 시 트랜잭션을 rollback 후 re-throw
            // → 트랜잭션 경계 밖에서 예외를 catch하므로 UnexpectedRollbackException 없음
            SeatHoldResultWrapper resultWrapper = transactionTemplate.execute(status -> {
                log.debug("[Optimistic Hold Tx 시작] seatId={}, audienceId={}", seatId, audienceId);
                Audience audience = audienceRepository.findById(audienceId)
                        .orElse(null);
                if (audience == null) {
                    log.warn("[Optimistic Hold 실패] Audience not found - audienceId={}", audienceId);
                    return new SeatHoldResultWrapper(SeatHoldResult.AUDIENCE_NOT_FOUND, null);
                }

                Seat seat = seatRepository.findById(seatId)
                        .orElse(null);
                if (seat == null) {
                    log.warn("[Optimistic Hold 실패] Seat not found - seatId={}", seatId);
                    return new SeatHoldResultWrapper(SeatHoldResult.SEAT_NOT_FOUND, null);
                }
                if (!seat.isAvailable()) {
                    log.debug("[Optimistic Hold 실패] 이미 선점된 좌석 - seatId={}", seatId);
                    return new SeatHoldResultWrapper(SeatHoldResult.ALREADY_HELD, seat.getSimulationId());
                }

                int affected = seatRepository.updateIfVersionMatches(
                        seat.getId(),
                        seat.getVersion(),
                        SeatStatus.HELD.name(),
                        audienceId
                );

                log.debug("[Optimistic Hold] UPDATE 시도: seatId={}, version={}, affected={}",
                        seat.getId(), seat.getVersion(), affected);

                if (affected == 0) {
                    log.debug("[Optimistic Hold 실패] 버전 충돌 - seatId={}", seatId);
                    return new SeatHoldResultWrapper(SeatHoldResult.LOCK_CONFLICT, seat.getSimulationId());
                }

                audienceRepository.insertAcquiredSeat(audienceId, seat.getId());
                log.debug("[Optimistic Hold Tx 완료] seatId={}, audienceId={}", seatId, audienceId);
                return new SeatHoldResultWrapper(SeatHoldResult.SUCCESS, seat.getSimulationId());
            });

            // 캐시에서 hold된 좌석 제거
            assert resultWrapper != null;

//            if (resultWrapper.getSeatHoldResult() == SeatHoldResult.SUCCESS) {
//                String key = "seats:available:" + resultWrapper.getSimulationId();
//                String cached = redisTemplate.opsForValue().get(key);
//                if (cached != null) {
//                    try {
//                        List<SeatResponse> availableSeats = objectMapper.readValue(cached, new TypeReference<List<SeatResponse>>() {
//                        });
//                        List<SeatResponse> updatedSeats = availableSeats.stream()
//                                .filter(s -> !s.getId().equals(seatId))
//                                .collect(Collectors.toList());
//                        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(updatedSeats));
//                    } catch (JsonProcessingException e) {
//                        log.warn("캐시 업데이트 실패 (seatId={})", seatId, e);
//                        // 캐시 업데이트 실패 시 캐시 삭제
//                        redisTemplate.delete(key);
//                    }
//                }
//            }

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

    @Override
    public SeatReleaseResult release(Long seatId, Long audienceId) {
        log.debug("[Optimistic Release 시작] seatId={}, audienceId={}", seatId, audienceId);
        try {
            SeatReleaseResultWrapper resultWrapper = transactionTemplate.execute(status -> {
                log.debug("[Optimistic Release Tx 시작] seatId={}, audienceId={}", seatId, audienceId);
                Audience audience = audienceRepository.findById(audienceId)
                        .orElse(null);
                if (audience == null) {
                    log.warn("[Optimistic Release 실패] Audience not found - audienceId={}", audienceId);
                    return new SeatReleaseResultWrapper(SeatReleaseResult.AUDIENCE_NOT_FOUND, null);
                }

                Seat seat = seatRepository.findById(seatId)
                        .orElse(null);
                if (seat == null) {
                    log.warn("[Optimistic Release 실패] Seat not found - seatId={}", seatId);
                    return new SeatReleaseResultWrapper(SeatReleaseResult.SEAT_NOT_FOUND, null);
                }
                int affected = seatRepository.updateIfVersionMatches(
                        seat.getId(),
                        seat.getVersion(),
                        SeatStatus.AVAILABLE.name(),
                        audienceId
                );
                log.debug("[Optimistic Release] UPDATE 시도: seatId={}, version={}, affected={}",
                        seat.getId(), seat.getVersion(), affected);

                if (affected == 0) {
                    log.debug("[Optimistic Release 실패] 버전 충돌 - seatId={}", seatId);
                    return new SeatReleaseResultWrapper(SeatReleaseResult.LOCK_CONFLICT, seat.getSimulationId());
                }

                audienceRepository.deleteAcquiredSeat(audienceId, seat.getId());
                log.debug("[Optimistic Release Tx 완료] seatId={}, audienceId={}", seatId, audienceId);
                return new SeatReleaseResultWrapper(SeatReleaseResult.SUCCESS, seat.getSimulationId());
            });

            assert resultWrapper != null;
            return resultWrapper.getSeatReleaseResult();
        } catch (ObjectOptimisticLockingFailureException | PessimisticLockingFailureException op) {
            return SeatReleaseResult.LOCK_CONFLICT;
        } catch (Exception e) {
            log.error("좌석 해제 중 예상치 못한 에러 발생 (seatId={})", seatId, e);
            return SeatReleaseResult.FAIL;
        }
    }
}
