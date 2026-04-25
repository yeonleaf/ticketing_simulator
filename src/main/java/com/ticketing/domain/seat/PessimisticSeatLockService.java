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
            SeatHoldResult result = internalService.doHold(seatId, audienceId);
            log.info("hold 결과: seatId={}, audienceId={}, result={}", seatId, audienceId, result);
            return result;
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
}
