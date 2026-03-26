package com.ticketing.domain.loadtest;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "load_tests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoadTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sourceShowId;
    private int startAudience;
    private int endAudience;
    private int step;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoadTestStatus status;

    private Instant createdAt;
    private Instant finishedAt;
    @Column(columnDefinition = "TEXT")
    private String failReason;

    @Builder
    public LoadTest(Long sourceShowId, int startAudience, int endAudience, int step) {
        this.sourceShowId = sourceShowId;
        this.startAudience = startAudience;
        this.endAudience = endAudience;
        this.step = step;
        this.status = LoadTestStatus.READY;
        this.createdAt = Instant.now();
    }

    public void start() {
        this.status = LoadTestStatus.RUNNING;
    }

    public void finish() {
        this.status = LoadTestStatus.DONE;
        this.finishedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status = LoadTestStatus.FAIL;
        this.failReason = reason;
        this.finishedAt = Instant.now();
    }
}
