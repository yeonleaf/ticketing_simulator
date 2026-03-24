package com.ticketing.domain.show;

import com.ticketing.domain.seat.Seat;
import com.ticketing.domain.seat.SeatResponse;
import com.ticketing.domain.seat.SeatSettingStrategy;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ShowResponse {

    private final Long id;
    private final String name;
    private final int maxRow;
    private final int maxCol;
    private final int audienceCount;
    private final SeatSettingStrategy seatSettingStrategy;

    public ShowResponse(Show show) {
        this.id = show.getId();
        this.name = show.getName();
        this.maxRow = show.getMaxRow();
        this.maxCol = show.getMaxCol();
        this.audienceCount = show.getAudienceCount();
        this.seatSettingStrategy = show.getSeatSettingStrategy();
    }
}
