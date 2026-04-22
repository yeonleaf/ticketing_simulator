package com.ticketing.domain.simulation;

import com.ticketing.domain.audience.AudienceDistributionStrategy;
import com.ticketing.domain.seat.SeatSettingStrategy;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimulationRequest {

    private String name;

    private int maxRow;

    private int maxCol;

    private int audienceCount;

    private SeatSettingStrategy seatSettingStrategy;

    private LockStrategy lockStrategy;

    private AudienceDistributionStrategy audienceDistributionStrategy;

}
