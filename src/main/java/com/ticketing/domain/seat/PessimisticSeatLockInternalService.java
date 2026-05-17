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

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PessimisticSeatLockInternalService {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final AudienceRepository audienceRepository;
    private final SeatRepository seatRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeatHoldResultWrapper doHold(Long seatId, Long audienceId) {
        log.debug("[Hold Tx 시작] seatId={}, audienceId={}", seatId, audienceId);

        // 1. audience 유효성 검사
        Audience audience = audienceRepository.findById(audienceId)
                .orElse(null);
        if (audience == null) {
            log.warn("[Hold 실패] Audience not found - audienceId={}", audienceId);
            return new SeatHoldResultWrapper(SeatHoldResult.AUDIENCE_NOT_FOUND, null);
        }

        // 2. 좌석 락 획득
        log.debug("[Hold] 좌석 락 획득 시도 - seatId={}", seatId);
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElse(null);

        if (seat == null) {
            log.warn("[Hold 실패] Seat not found - seatId={}", seatId);
            return new SeatHoldResultWrapper(SeatHoldResult.SEAT_NOT_FOUND, null);
        }
        if (!seat.isAvailable()) {
            log.debug("[Hold 실패] 이미 선점된 좌석 - seatId={}, currentHolderId={}", seatId, seat.getHolderId());
            return new SeatHoldResultWrapper(SeatHoldResult.ALREADY_HELD, seat.getSimulationId());
        }

        // 3. 좌석 선점
        seat.hold(audienceId);
        log.debug("[Hold] 좌석 선점 완료 - seatId={}, audienceId={}", seatId, audienceId);

        // 4. audience 결과 업데이트
        audience.addAcquiredSeat(seatId);

        log.debug("[Hold Tx 완료] seatId={}, audienceId={}", seatId, audienceId);
        return new SeatHoldResultWrapper(SeatHoldResult.SUCCESS, seat.getSimulationId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeatReleaseResultWrapper doRelease(Long seatId, Long audienceId) {
        log.debug("[Release Tx 시작] seatId={}, audienceId={}", seatId, audienceId);

        // 1. audience 유효성 검사
        Audience audience = audienceRepository.findById(audienceId)
                .orElse(null);
        if (audience == null) {
            log.warn("[Release 실패] Audience not found - audienceId={}", audienceId);
            return new SeatReleaseResultWrapper(SeatReleaseResult.AUDIENCE_NOT_FOUND, null);
        }

        // 2. 좌석 락 획득
        log.debug("[Release] 좌석 락 획득 시도 - seatId={}", seatId);
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElse(null);

        if (seat == null) {
            log.warn("[Release 실패] Seat not found - seatId={}", seatId);
            return new SeatReleaseResultWrapper(SeatReleaseResult.SEAT_NOT_FOUND, null);
        }
        if (seat.isAvailable() || !audienceId.equals(seat.getHolderId())) {
            log.warn("[Release 실패] 본인이 선점한 좌석이 아님 - seatId={}, audienceId={}, currentHolderId={}",
                    seatId, audienceId, seat.getHolderId());
            return new SeatReleaseResultWrapper(SeatReleaseResult.NOT_HELD_BY_YOU, seat.getSimulationId());
        }

        // 3. 좌석 해제
        seat.release();
        log.debug("[Release] 좌석 해제 완료 - seatId={}, audienceId={}", seatId, audienceId);

        // 4. audience 결과 업데이트
        audience.releaseAcquiredSeat(seatId);

        log.debug("[Release Tx 완료] seatId={}, audienceId={}", seatId, audienceId);
        return new SeatReleaseResultWrapper(SeatReleaseResult.SUCCESS, seat.getSimulationId());
    }

}
