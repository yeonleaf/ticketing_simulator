package com.ticketing.domain.seat;

import lombok.Getter;

@Getter
public class SeatResponse {
    private final Long id;
    private final Long simulationId;
    private final int row;
    private final int col;
    private final SeatStatus seatStatus;
    private final SeatGrade seatGrade;
    private final int hotScore;
    private final Long holderId;

    public SeatResponse(Seat seat) {
        this.id = seat.getId();
        this.simulationId = seat.getSimulationId();
        this.row = seat.getRow();
        this.col = seat.getCol();
        this.seatStatus = seat.getSeatStatus();
        this.seatGrade = seat.getSeatGrade();
        this.hotScore = seat.getHotScore();
        this.holderId = seat.getHolderId();
    }
}
