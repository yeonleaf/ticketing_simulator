package com.ticketing.domain.seat;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "seats")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long simulationId;

    @Column(name = "seat_row", nullable = false)
    private int row;

    @Column(name = "seat_col", nullable = false)
    private int col;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus seatStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatGrade seatGrade;

    @Column(nullable = false)
    private int hotScore;

    private Long holderId;

    @Version
    private Long version;

    @Builder
    public Seat(Long simulationId, int row, int col,
                SeatStatus seatStatus, SeatGrade seatGrade, int hotScore) {
        this.simulationId = simulationId;
        this.row = row;
        this.col = col;
        this.seatStatus = seatStatus;
        this.seatGrade = seatGrade;
        this.hotScore = hotScore;
    }

    public void hold(Long audienceId) {
        this.seatStatus = SeatStatus.HELD;
        this.holderId = audienceId;
    }

    public void release() {
        this.seatStatus = SeatStatus.AVAILABLE;
        this.holderId = null;
    }

    public boolean isAvailable() {
        return this.seatStatus == SeatStatus.AVAILABLE;
    }
}
