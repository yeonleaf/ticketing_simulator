package com.ticketing.domain.seat;

public interface SeatLockService {
    SeatHoldResult hold(int seatNo, Long audienceId);
}