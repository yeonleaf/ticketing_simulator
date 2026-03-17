package com.ticketing.domain.show;

import com.ticketing.domain.seat.SeatSettingStrategy;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;

@Entity
@Table(name = "shows")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int maxRow;

    @Column(nullable = false)
    private int maxCol;

    @Column(nullable = false)
    private int audienceCount;

    @Column(nullable = false)
    private Duration seatHoldingTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatSettingStrategy seatSettingStrategy;

    @Builder
    public Show(String name, int maxRow, int maxCol, int audienceCount,
                Duration seatHoldingTime, SeatSettingStrategy seatSettingStrategy) {
        this.name = name;
        this.maxRow = maxRow;
        this.maxCol = maxCol;
        this.audienceCount = audienceCount;
        this.seatHoldingTime = seatHoldingTime;
        this.seatSettingStrategy = seatSettingStrategy;
    }
}
