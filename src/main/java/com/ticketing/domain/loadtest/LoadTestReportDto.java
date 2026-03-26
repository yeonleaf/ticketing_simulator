package com.ticketing.domain.loadtest;

import com.ticketing.domain.audience.AudienceDistributionStrategy;
import com.ticketing.domain.simulation.LockStrategy;
import com.ticketing.domain.simulation.SimStatus;
import com.ticketing.domain.simulation.ThreadStrategy;

import java.util.List;

public record LoadTestReportDto(
        Long loadTestId,
        Long sourceShowId,
        String sourceShowName,
        LoadTestStatus status,
        int startAudience,
        int endAudience,
        int step,
        List<AudienceResult> results
) {
    public record AudienceResult(
            int audienceCount,
            List<SimulationResult> simulations
    ) {}

    public record SimulationResult(
            Long simulationId,
            LockStrategy lockStrategy,
            ThreadStrategy threadStrategy,
            AudienceDistributionStrategy audienceDistributionStrategy,
            SimStatus status,
            double totalTps,
            long avgResponseMs,
            int duplicateHoldCount,
            int fullySatisfiedCount,
            int partiallySatisfiedCount,
            int unsatisfiedCount
    ) {}
}
