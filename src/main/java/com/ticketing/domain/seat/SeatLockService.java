package com.ticketing.domain.seat;

import java.util.List;

public interface SeatLockService {
    SeatHoldResult hold(Long seatId, Long audienceId);
    List<SeatResponse> getAllSeatsBySimulationId(Long simulationId);
    SeatReleaseResult release(Long seatId, Long audienceId);
}
