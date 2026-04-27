package com.ticketing.domain.seat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
public class SeatHoldResultWrapper {
    private SeatHoldResult seatHoldResult;
    private Long simulationId;
}
