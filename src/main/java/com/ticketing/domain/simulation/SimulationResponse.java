package com.ticketing.domain.simulation;

import com.ticketing.domain.audience.AudienceDistributionStrategy;
import com.ticketing.domain.seat.Seat;
import com.ticketing.domain.seat.SeatResponse;
import com.ticketing.domain.seat.SeatSettingStrategy;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
public class SimulationResponse {

    private Long id;
    private Long showId;
    private List<SeatResponse> seatResponses = new ArrayList<>();
    private LockStrategy lockStrategy;
    private AudienceDistributionStrategy audienceDistributionStrategy;
    private SimStatus status;
    private Instant startedAt;
    private Instant finishedAt;
    private double totalTps;
    private long avgResponseMs;
    private int duplicateHoldCount;
    private int fullySatisfiedCount;
    private int partiallySatisfiedCount;
    private int unsatisfiedCount;

    public SimulationResponse(Simulation simulation, List<Seat> seats) {
        this.id = simulation.getId();
        this.showId = simulation.getShowId();
        this.lockStrategy = simulation.getLockStrategy();
        this.audienceDistributionStrategy = simulation.getAudienceDistributionStrategy();
        this.status = simulation.getStatus();
        this.startedAt = simulation.getStartedAt();
        this.finishedAt = simulation.getFinishedAt();
        this.totalTps = simulation.getTotalTps();
        this.avgResponseMs = simulation.getAvgResponseMs();
        this.duplicateHoldCount = simulation.getDuplicateHoldCount();
        this.fullySatisfiedCount = simulation.getFullySatisfiedCount();
        this.partiallySatisfiedCount = simulation.getPartiallySatisfiedCount();
        this.unsatisfiedCount = simulation.getUnsatisfiedCount();
        seats.forEach(seat -> seatResponses.add(new SeatResponse(seat)));
    }
}
