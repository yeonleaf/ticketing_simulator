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
        log.debug("[Redis Hold Tx 시작] seatId={}, audienceId={}", seatId, audienceId);

        Audience audience = audienceRepository.findById(audienceId)
                .orElse(null);
        if (audience == null) {
            log.warn("[Redis Hold 실패] Audience not found - audienceId={}", audienceId);
            return new SeatHoldResultWrapper(SeatHoldResult.FAIL, null);
        }

        Seat seat = seatRepository.findById(seatId)
                .orElse(null);
        if (seat == null) {
            log.warn("[Redis Hold 실패] Seat not found - seatId={}", seatId);
            return new SeatHoldResultWrapper(SeatHoldResult.FAIL, null);
        }
        if (!seat.isAvailable()) {
            log.debug("[Redis Hold 실패] 이미 선점된 좌석 - seatId={}", seatId);
            return new SeatHoldResultWrapper(SeatHoldResult.ALREADY_HELD, seat.getSimulationId());
        }

        seat.hold(audienceId);
        seatRepository.save(seat);
        log.debug("[Redis Hold] 좌석 선점 완료 - seatId={}, audienceId={}", seatId, audienceId);

        audience.addAcquiredSeat(seatId);
        audienceRepository.save(audience);

        log.debug("[Redis Hold Tx 완료] seatId={}, audienceId={}", seatId, audienceId);
        return new SeatHoldResultWrapper(SeatHoldResult.SUCCESS, seat.getSimulationId());
    }

    public List<SeatResponse> getAllSeatsBySimulationId(Long simulationId) {
        List<SeatResponse> seatResponses = new ArrayList<>();
        seatRepository.findAllBySimulationId(simulationId).forEach(e -> seatResponses.add(new SeatResponse(e)));
        return seatResponses;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeatReleaseResultWrapper doRelease(Long seatId, Long audienceId) {
        log.debug("[Redis Release Tx 시작] seatId={}, audienceId={}", seatId, audienceId);

        Audience audience = audienceRepository.findById(audienceId)
                .orElse(null);
        if (audience == null) {
            log.warn("[Redis Release 실패] Audience not found - audienceId={}", audienceId);
            return new SeatReleaseResultWrapper(SeatReleaseResult.AUDIENCE_NOT_FOUND, null);
        }

        Seat seat = seatRepository.findById(seatId)
                .orElse(null);
        if (seat == null) {
            log.warn("[Redis Release 실패] Seat not found - seatId={}", seatId);
            return new SeatReleaseResultWrapper(SeatReleaseResult.SEAT_NOT_FOUND, null);
        }
        if (seat.isAvailable() || !seat.getHolderId().equals(audienceId)) {
            log.warn("[Redis Release 실패] 본인이 선점한 좌석이 아님 - seatId={}, audienceId={}, currentHolderId={}",
                    seatId, audienceId, seat.getHolderId());
            return new SeatReleaseResultWrapper(SeatReleaseResult.NOT_HELD_BY_YOU, seat.getSimulationId());
        }

        seat.release();
        log.debug("[Redis Release] 좌석 해제 완료 - seatId={}, audienceId={}", seatId, audienceId);

        audience.releaseAcquiredSeat(seatId);

        log.debug("[Redis Release Tx 완료] seatId={}, audienceId={}", seatId, audienceId);
        return new SeatReleaseResultWrapper(SeatReleaseResult.SUCCESS, seat.getSimulationId());
    }
}
