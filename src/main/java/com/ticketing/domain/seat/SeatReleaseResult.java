package com.ticketing.domain.seat;

public enum SeatReleaseResult {
    SUCCESS,
    NOT_HELD_BY_YOU,
    SEAT_NOT_FOUND,
    AUDIENCE_NOT_FOUND,
    LOCK_TIMEOUT,
    LOCK_CONFLICT,
    FAIL
}
