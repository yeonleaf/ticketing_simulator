package com.ticketing.domain.simulation;

import com.ticketing.domain.audience.AudienceDistributionStrategy;
import com.ticketing.domain.seat.SeatSettingStrategy;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "simulations")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Simulation {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatSettingStrategy seatSettingStrategy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LockStrategy lockStrategy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AudienceDistributionStrategy audienceDistributionStrategy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SimStatus status;

    private Instant startedAt;

    private Instant finishedAt;

    private double totalTps;

    private long avgResponseMs;

    private int duplicateHoldCount;

    @Column(columnDefinition = "TEXT")
    private String failReason;

    private int fullySatisfiedCount;
    private int partiallySatisfiedCount;
    private int unsatisfiedCount;

    @Builder
    public Simulation(String name, int maxRow, int maxCol, int audienceCount,
                      SeatSettingStrategy seatSettingStrategy, LockStrategy lockStrategy,
                      AudienceDistributionStrategy audienceDistributionStrategy) {
        this.name = name;
        this.maxRow = maxRow;
        this.maxCol = maxCol;
        this.audienceCount = audienceCount;
        this.seatSettingStrategy = seatSettingStrategy;
        this.lockStrategy = lockStrategy;
        this.audienceDistributionStrategy = audienceDistributionStrategy;
        this.status = SimStatus.READY;
    }

    public Simulation(SimulationRequest request) {
        this.name = request.getName();
        this.maxRow = request.getMaxRow();
        this.maxCol = request.getMaxCol();
        this.audienceCount = request.getAudienceCount();
        this.seatSettingStrategy = request.getSeatSettingStrategy();
        this.lockStrategy = request.getLockStrategy();
        this.audienceDistributionStrategy = request.getAudienceDistributionStrategy();
        this.status = SimStatus.READY;
    }


    public void start() {
        this.status = SimStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void finish(double totalTps, long avgResponseMs, int duplicateHoldCount, int fullySatisfiedCount, int partiallySatisfiedCount, int unsatisfiedCount) {
        this.status = SimStatus.DONE;
        this.finishedAt = Instant.now();
        this.totalTps = totalTps;
        this.avgResponseMs = avgResponseMs;
        this.duplicateHoldCount = duplicateHoldCount;
        this.fullySatisfiedCount = fullySatisfiedCount;
        this.partiallySatisfiedCount = partiallySatisfiedCount;
        this.unsatisfiedCount = unsatisfiedCount;
    }

    public void fail(String failReason) {
        this.status = SimStatus.FAIL;
        this.finishedAt = Instant.now();
        this.failReason = failReason;
    }

    public void interrupt() {
        this.status = SimStatus.INTERRUPTED;
        this.finishedAt = Instant.now();
    }
}
