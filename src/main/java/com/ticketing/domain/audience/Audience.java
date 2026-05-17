package com.ticketing.domain.audience;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "audiences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Audience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long simulationId;

    @Column(nullable = false)
    private int seatCnt;

    @Column(nullable = false)
    private Duration seatClickWaitJitter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatPreferenceStrategy strategy;

    @ElementCollection
    @CollectionTable(name = "audience_preferred_seats", joinColumns = @JoinColumn(name = "audience_id"))
    @Column(name = "seat_id")
    private Set<Long> preferredSeatIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "audience_acquired_seats", joinColumns = @JoinColumn(name = "audience_id"))
    @Column(name = "seat_id")
    private Set<Long> acquiredSeatIds = new HashSet<>();

    @Column(nullable = false)
    private Long version = 0L;

    @Builder
    public Audience(Long simulationId, boolean isRealUser, int seatCnt,
                    Duration seatClickWaitJitter, SeatPreferenceStrategy strategy) {
        this.simulationId = simulationId;
        this.seatCnt = seatCnt;
        this.seatClickWaitJitter = seatClickWaitJitter;
        this.strategy = strategy;
    }

    public void setPreferredSeatIds(List<Long> seatIds) {
        this.preferredSeatIds = new HashSet<>(seatIds);
    }

    public void addAcquiredSeat(Long seatId) {
        this.acquiredSeatIds.add(seatId);
    }
    public void releaseAcquiredSeat(Long seatId) {
        this.acquiredSeatIds.remove(seatId);
    }
}
