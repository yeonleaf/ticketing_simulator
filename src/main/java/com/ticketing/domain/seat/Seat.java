package com.ticketing.domain.seat;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    private int no;

    @Column(nullable = false)
    private Long showId;

    @Column(nullable = false)
    private int row;

    @Column(nullable = false)
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

    private Instant holdExpireAt;

    @Builder
    public Seat(int no, Long showId, int row, int col,
                SeatStatus seatStatus, SeatGrade seatGrade, int hotScore) {
        this.no = no;
        this.showId = showId;
        this.row = row;
        this.col = col;
        this.seatStatus = seatStatus;
        this.seatGrade = seatGrade;
        this.hotScore = hotScore;
    }

    public void hold(Long audienceId, Instant expireAt) {
        this.seatStatus = SeatStatus.HELD;
        this.holderId = audienceId;
        this.holdExpireAt = expireAt;
    }

    public void release() {
        this.seatStatus = SeatStatus.AVAILABLE;
        this.holderId = null;
        this.holdExpireAt = null;
    }

    public boolean isAvailable() {
        return this.seatStatus == SeatStatus.AVAILABLE;
    }
}
