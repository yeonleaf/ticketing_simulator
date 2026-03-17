package com.ticketing.domain.simulation;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "simulations")
@Getter
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
    private SimStatus status;

    private Instant startedAt;

    private Instant finishedAt;

    private double totalTps;

    private long avgResponseMs;

    private int duplicateHoldCount;

    @Builder
    public Simulation(Long showId, LockStrategy lockStrategy) {
        this.showId = showId;
        this.lockStrategy = lockStrategy;
        this.status = SimStatus.READY;
    }

    public void start() {
        this.status = SimStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void finish(double totalTps, long avgResponseMs, int duplicateHoldCount) {
        this.status = SimStatus.DONE;
        this.finishedAt = Instant.now();
        this.totalTps = totalTps;
        this.avgResponseMs = avgResponseMs;
        this.duplicateHoldCount = duplicateHoldCount;
    }
}
