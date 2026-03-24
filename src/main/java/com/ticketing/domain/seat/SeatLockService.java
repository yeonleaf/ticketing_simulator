package com.ticketing.domain.seat;

import java.util.List;

public interface SeatLockService {
    SeatHoldResult hold(int seatNo, Long audienceId);
    List<SeatResponse> getAllSeatsBySimulationId(Long simulationId);
}