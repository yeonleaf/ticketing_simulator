package com.ticketing.domain.seat;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SeatReleaseResultWrapper {
    private SeatReleaseResult seatReleaseResult;
    private Long simulationId;
}
