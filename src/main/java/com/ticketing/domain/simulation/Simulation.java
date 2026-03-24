package com.ticketing.domain.simulation;

import com.ticketing.domain.audience.AudienceDistributionStrategy;
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
    private Long showId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LockStrategy lockStrategy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AudienceDistributionStrategy audienceDistributionStrategy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SimStatus status;

    private Instant startedAt;

    private Instant finishedAt;

    private double totalTps;

    private long avgResponseMs;

    private int duplicateHoldCount;

    private String failReason;

    private int fullySatisfiedCount;
    private int partiallySatisfiedCount;
    private int unsatisfiedCount;

    @Builder
    public Simulation(Long showId, LockStrategy lockStrategy,
                      AudienceDistributionStrategy audienceDistributionStrategy) {
        this.showId = showId;
        this.lockStrategy = lockStrategy;
        this.audienceDistributionStrategy = audienceDistributionStrategy;
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
}
