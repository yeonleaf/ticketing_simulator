package com.ticketing.domain.audience;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Getter
public class AudienceResponse {

    private Long id;

    private Long simulationId;

    private int seatCnt;

    private Duration seatClickWaitJitter;

    private SeatPreferenceStrategy strategy;

    private Set<Long> preferredSeatIds = new HashSet<>();

    private Set<Long> acquiredSeatIds = new HashSet<>();

    public AudienceResponse(Audience audience) {
        this.id = audience.getId();
        this.simulationId = audience.getSimulationId();
        this.seatCnt = audience.getSeatCnt();
        this.seatClickWaitJitter = audience.getSeatClickWaitJitter();
        this.strategy = audience.getStrategy();
        preferredSeatIds.addAll(audience.getPreferredSeatIds());
        acquiredSeatIds.addAll(audience.getAcquiredSeatIds());
    }
}
