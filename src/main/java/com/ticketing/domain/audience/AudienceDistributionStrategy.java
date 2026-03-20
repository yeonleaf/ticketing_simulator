package com.ticketing.domain.audience;

import java.time.Duration;
import java.util.*;

import static com.ticketing.domain.audience.SeatPreferenceStrategy.*;

public enum AudienceDistributionStrategy {

    MUSICAL_HEAVY_FRONT(Map.ofEntries(
            Map.entry(HotScoreFirst, 30),
            Map.entry(FrontCenter,   25),
            Map.entry(GradeVIP,      15),
            Map.entry(CenterCenter,   8),
            Map.entry(FrontRight,     5),
            Map.entry(FrontLeft,      5),
            Map.entry(HotScoreLast,   5),
            Map.entry(BehindCenter,   4),
            Map.entry(GradeR,         3)
    )),

    CONCERT_RUSH(Map.ofEntries(
            Map.entry(HotScoreFirst, 50),
            Map.entry(FrontCenter,   20),
            Map.entry(GradeVIP,      15),
            Map.entry(CenterCenter,   4),
            Map.entry(FrontRight,     3),
            Map.entry(FrontLeft,      3),
            Map.entry(BehindCenter,   2),
            Map.entry(HotScoreLast,   2),
            Map.entry(GradeR,         1)
    )),

    BALANCED(Map.ofEntries(
            Map.entry(HotScoreFirst, 12),
            Map.entry(FrontCenter,   12),
            Map.entry(CenterCenter,  12),
            Map.entry(BehindCenter,  12),
            Map.entry(HotScoreLast,  12),
            Map.entry(GradeVIP,      10),
            Map.entry(FrontRight,    10),
            Map.entry(FrontLeft,     10),
            Map.entry(GradeR,        10)
    )),

    // 9개 전략: HotScoreFirst=12, 나머지 8개=11 → 합 100
    UNIFORM(Map.ofEntries(
            Map.entry(HotScoreFirst, 12),
            Map.entry(FrontCenter,   11),
            Map.entry(CenterCenter,  11),
            Map.entry(BehindCenter,  11),
            Map.entry(HotScoreLast,  11),
            Map.entry(GradeVIP,      11),
            Map.entry(FrontRight,    11),
            Map.entry(FrontLeft,     11),
            Map.entry(GradeR,        11)
    ));

    // ── 관객 생성 기본값 상수 ─────────────────────────────────────────
    private static final double SINGLE_SEAT_PROBABILITY = 0.7;  // 70% → 1석
    private static final long   JITTER_MIN_MS           = 500;  // jitter 하한 (포함)
    private static final long   JITTER_MAX_MS           = 3000; // jitter 상한 (미포함)

    private final Map<SeatPreferenceStrategy, Integer> weights;

    AudienceDistributionStrategy(Map<SeatPreferenceStrategy, Integer> weights) {
        this.weights = weights;
    }

    public Map<SeatPreferenceStrategy, Integer> getWeights() {
        return weights;
    }

    // ── 핵심 메서드 ───────────────────────────────────────────────────

    public List<Audience> distribute(int totalCount, long simulationId) {
        return distribute(totalCount, simulationId, new Random());
    }

    public List<Audience> distribute(int totalCount, long simulationId, Random random) {
        if (totalCount == 0) {
            return Collections.emptyList();
        }

        // Step 1: 비율 → 인원수 (Math.round 반올림)
        Map<SeatPreferenceStrategy, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<SeatPreferenceStrategy, Integer> entry : weights.entrySet()) {
            int count = (int) Math.round(totalCount * entry.getValue() / 100.0);
            counts.put(entry.getKey(), count);
        }

        // Step 2: 반올림 오차 보정 — 가장 가중치가 높은 전략에서 차이만큼 가감
        int sum = counts.values().stream().mapToInt(Integer::intValue).sum();
        int diff = totalCount - sum;
        if (diff != 0) {
            SeatPreferenceStrategy topStrategy = weights.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElseThrow();
            counts.put(topStrategy, Math.max(0, counts.get(topStrategy) + diff));
        }

        // Step 3: Audience 객체 생성
        List<Audience> audiences = new ArrayList<>(totalCount);
        for (Map.Entry<SeatPreferenceStrategy, Integer> entry : counts.entrySet()) {
            SeatPreferenceStrategy strategy = entry.getKey();
            int cnt = entry.getValue();
            for (int i = 0; i < cnt; i++) {
                audiences.add(buildVirtualAudience(simulationId, strategy, random));
            }
        }

        // Step 4: 셔플 (전략별로 뭉치지 않도록)
        Collections.shuffle(audiences, random);

        return audiences;
    }

    private Audience buildVirtualAudience(long simulationId, SeatPreferenceStrategy strategy, Random random) {
        int seatCnt = random.nextDouble() < SINGLE_SEAT_PROBABILITY ? 1 : 2;
        Duration jitter = Duration.ofMillis(random.nextLong(JITTER_MIN_MS, JITTER_MAX_MS));
        return Audience.builder()
                .simulationId(simulationId)
                .isRealUser(false)
                .seatCnt(seatCnt)
                .seatClickWaitJitter(jitter)
                .strategy(strategy)
                .build();
    }
}
