package com.ticketing.domain.seat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
public class SeatResponse {
    private Long id;
    private Long simulationId;
    private int row;
    private int col;
    private SeatStatus seatStatus;
    private SeatGrade seatGrade;
    private int hotScore;
    private Long holderId;

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
