package com.ticketing.domain.seat;

public enum SeatHoldResult {
    SUCCESS,
    ALREADY_HELD,
    LOCK_TIMEOUT,
    LOCK_CONFLICT,
    SEAT_NOT_FOUND,
    AUDIENCE_NOT_FOUND,
    FAIL
}
