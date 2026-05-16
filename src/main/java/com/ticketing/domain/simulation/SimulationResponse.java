package com.ticketing.domain.simulation;

import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceDistributionStrategy;
import com.ticketing.domain.audience.AudienceResponse;
import com.ticketing.domain.seat.Seat;
import com.ticketing.domain.seat.SeatResponse;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
public class SimulationResponse {

    private Long id;
    private List<SeatResponse> seatResponses = new ArrayList<>();
    private List<AudienceResponse> audienceResponses = new ArrayList<>();
    private LockStrategy lockStrategy;
    private AudienceDistributionStrategy audienceDistributionStrategy;
    private SimStatus status;
    private Instant startedAt;
    private Instant finishedAt;
    private double totalTps;
    private long avgResponseMs;
    private long p90ResponseMs;
    private long p95ResponseMs;
    private int duplicateHoldCount;
    private long holdsTotal;
    private long holdsSuccess;
    private long lockConflict;
    private long lockTimeout;
    private int fullySatisfiedCount;
    private int partiallySatisfiedCount;
    private int unsatisfiedCount;
    private boolean virtualThread;

    public SimulationResponse(Simulation simulation, List<Audience> audiences, List<Seat> seats) {
        this.id = simulation.getId();
        this.lockStrategy = simulation.getLockStrategy();
        this.audienceDistributionStrategy = simulation.getAudienceDistributionStrategy();
        this.status = simulation.getStatus();
        this.startedAt = simulation.getStartedAt();
        this.finishedAt = simulation.getFinishedAt();
        this.totalTps = simulation.getTotalTps();
        this.avgResponseMs = simulation.getAvgResponseMs();
        this.p90ResponseMs = simulation.getP90ResponseMs();
        this.p95ResponseMs = simulation.getP95ResponseMs();
        this.duplicateHoldCount = simulation.getDuplicateHoldCount();
        this.holdsTotal = simulation.getHoldsTotal();
        this.holdsSuccess = simulation.getHoldsSuccess();
        this.lockConflict = simulation.getLockConflict();
        this.lockTimeout = simulation.getLockTimeout();
        this.fullySatisfiedCount = simulation.getFullySatisfiedCount();
        this.partiallySatisfiedCount = simulation.getPartiallySatisfiedCount();
        this.unsatisfiedCount = simulation.getUnsatisfiedCount();
        this.virtualThread = simulation.isVirtualThread();
        seats.forEach(seat -> seatResponses.add(new SeatResponse(seat)));
        audiences.forEach(audience -> audienceResponses.add(new AudienceResponse(audience)));
    }
}
