package com.ticketing.domain.audience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static com.ticketing.domain.audience.AudienceDistributionStrategy.*;
import static com.ticketing.domain.audience.SeatPreferenceStrategy.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("AudienceDistributionStrategy 관객 배분 로직 테스트")
class AudienceDistributionStrategyTest {

    private static final long SIM_ID = 42L;

    /** 전략별 인원수 Map으로 변환 */
    private Map<SeatPreferenceStrategy, Long> countByStrategy(List<Audience> audiences) {
        return audiences.stream()
                .collect(Collectors.groupingBy(Audience::getStrategy, Collectors.counting()));
    }

    /** 전략 순서 리스트 추출 */
    private List<SeatPreferenceStrategy> strategyOrder(List<Audience> audiences) {
        return audiences.stream()
                .map(Audience::getStrategy)
                .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════════════════
    // 1. MUSICAL_HEAVY_FRONT — 비율 정확성
    // 100명은 정수 비율이라 반올림 오차 없음
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("MUSICAL_HEAVY_FRONT — 비율 정확성")
    class MusicalHeavyFrontTest {

        @Test
        @DisplayName("100명_배분_시_각_전략별_인원이_가중치와_정확히_일치한다")
        void 백명_배분_시_각_전략별_인원이_가중치와_정확히_일치한다() {
            List<Audience> result = MUSICAL_HEAVY_FRONT.distribute(100, SIM_ID, new Random(42));
            Map<SeatPreferenceStrategy, Long> counts = countByStrategy(result);

            assertThat(result).hasSize(100);
            assertThat(counts.get(HotScoreFirst)).isEqualTo(30L);
            assertThat(counts.get(FrontCenter)).isEqualTo(25L);
            assertThat(counts.get(GradeVIP)).isEqualTo(15L);
            assertThat(counts.get(CenterCenter)).isEqualTo(8L);
            assertThat(counts.get(FrontRight)).isEqualTo(5L);
            assertThat(counts.get(FrontLeft)).isEqualTo(5L);
            assertThat(counts.get(HotScoreLast)).isEqualTo(5L);
            assertThat(counts.get(BehindCenter)).isEqualTo(4L);
            assertThat(counts.get(GradeR)).isEqualTo(3L);
        }

        @Test
        @DisplayName("100명_배분_시_전체_합이_정확히_100이다")
        void 백명_배분_시_전체_합이_정확히_백이다() {
            List<Audience> result = MUSICAL_HEAVY_FRONT.distribute(100, SIM_ID, new Random(42));

            assertThat(result).hasSize(100);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. CONCERT_RUSH — 쏠림 분포
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("CONCERT_RUSH — 쏠림 분포")
    class ConcertRushTest {

        @Test
        @DisplayName("HotScoreFirst가_50명으로_가장_많다")
        void HotScoreFirst가_50명으로_가장_많다() {
            List<Audience> result = CONCERT_RUSH.distribute(100, SIM_ID, new Random(42));
            Map<SeatPreferenceStrategy, Long> counts = countByStrategy(result);

            long max = Collections.max(counts.values());
            assertThat(counts.get(HotScoreFirst)).isEqualTo(50L).isEqualTo(max);
        }

        @Test
        @DisplayName("상위_3개_전략이_정확히_85명이다")
        void 상위_3개_전략이_정확히_85명이다() {
            List<Audience> result = CONCERT_RUSH.distribute(100, SIM_ID, new Random(42));
            Map<SeatPreferenceStrategy, Long> counts = countByStrategy(result);

            long top3 = counts.get(HotScoreFirst) + counts.get(FrontCenter) + counts.get(GradeVIP);
            assertThat(top3).isEqualTo(85L);
        }

        @Test
        @DisplayName("GradeR이_1명으로_가장_적다")
        void GradeR이_1명으로_가장_적다() {
            List<Audience> result = CONCERT_RUSH.distribute(100, SIM_ID, new Random(42));
            Map<SeatPreferenceStrategy, Long> counts = countByStrategy(result);

            long min = Collections.min(counts.values());
            assertThat(counts.get(GradeR)).isEqualTo(1L).isEqualTo(min);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. BALANCED — 균등 분포
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("BALANCED — 균등 분포")
    class BalancedTest {

        @Test
        @DisplayName("최다_전략과_최소_전략의_차이가_2_이하이다")
        void 최다_전략과_최소_전략의_차이가_2_이하이다() {
            List<Audience> result = BALANCED.distribute(100, SIM_ID, new Random(42));
            Map<SeatPreferenceStrategy, Long> counts = countByStrategy(result);

            long max = Collections.max(counts.values());
            long min = Collections.min(counts.values());
            assertThat(max - min).isLessThanOrEqualTo(2L);
        }

        @Test
        @DisplayName("모든_전략에_최소_10명_이상_배분된다")
        void 모든_전략에_최소_10명_이상_배분된다() {
            List<Audience> result = BALANCED.distribute(100, SIM_ID, new Random(42));
            Map<SeatPreferenceStrategy, Long> counts = countByStrategy(result);

            assertThat(result).hasSize(100);
            // 9개 전략 모두 맵에 존재 + 10명 이상
            assertThat(counts).hasSize(9);
            assertThat(counts.values()).allSatisfy(c -> assertThat(c).isGreaterThanOrEqualTo(10L));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 4. UNIFORM — 완전 균등
    // 99명: sum=100(보정전) → 보정(-1) → 모든 전략 11명
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("UNIFORM — 완전 균등 (99명)")
    class UniformTest {

        @Test
        @DisplayName("99명_배분_시_전체_합이_정확히_99이다")
        void 구십구명_배분_시_전체_합이_정확히_99이다() {
            List<Audience> result = UNIFORM.distribute(99, SIM_ID, new Random(42));

            assertThat(result).hasSize(99);
        }

        @Test
        @DisplayName("전략별_인원_차이가_1_이하이다")
        void 전략별_인원_차이가_1_이하이다() {
            List<Audience> result = UNIFORM.distribute(99, SIM_ID, new Random(42));
            Map<SeatPreferenceStrategy, Long> counts = countByStrategy(result);

            long max = Collections.max(counts.values());
            long min = Collections.min(counts.values());
            assertThat(max - min).isLessThanOrEqualTo(1L);
        }

        @Test
        @DisplayName("보정_후_모든_전략이_11명이다")
        void 보정_후_모든_전략이_11명이다() {
            // HotScoreFirst(12%) round(99*12/100)=12, 나머지 round(10.89)=11 → sum=100
            // 보정: HotScoreFirst -1 → 11. 모든 전략 11명
            List<Audience> result = UNIFORM.distribute(99, SIM_ID, new Random(42));
            Map<SeatPreferenceStrategy, Long> counts = countByStrategy(result);

            assertThat(counts.values()).allSatisfy(c -> assertThat(c).isEqualTo(11L));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 5. 반올림 오차 보정
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("반올림 오차 보정")
    class RoundingCorrectionTest {

        @Test
        @DisplayName("MUSICAL_HEAVY_FRONT_7명_보정_후_합이_정확히_7이고_음수_인원_없다")
        void MUSICAL_HEAVY_FRONT_7명_보정_후_합이_정확히_7이고_음수_인원_없다() {
            // round합=6(HSF=2,FC=2,VIP=1,CC=1) → diff=+1 → HSF=3
            List<Audience> result = MUSICAL_HEAVY_FRONT.distribute(7, SIM_ID, new Random(42));
            Map<SeatPreferenceStrategy, Long> counts = countByStrategy(result);

            assertThat(result).hasSize(7);
            // 보정 전 0이었던 전략은 맵에 없으므로 getOrDefault 사용
            Arrays.stream(SeatPreferenceStrategy.values()).forEach(s ->
                    assertThat(counts.getOrDefault(s, 0L)).isGreaterThanOrEqualTo(0L));
            // 보정 후 HotScoreFirst가 가장 많음
            assertThat(counts.get(HotScoreFirst)).isGreaterThanOrEqualTo(2L);
        }

        @Test
        @DisplayName("MUSICAL_HEAVY_FRONT_7명_HotScoreFirst가_3명_FrontCenter가_2명이다")
        void MUSICAL_HEAVY_FRONT_7명_HotScoreFirst가_3명_FrontCenter가_2명이다() {
            // round: HSF=2, FC=2, VIP=1, CC=1, rest=0 → sum=6 → diff=+1 → HSF=3
            List<Audience> result = MUSICAL_HEAVY_FRONT.distribute(7, SIM_ID, new Random(42));
            Map<SeatPreferenceStrategy, Long> counts = countByStrategy(result);

            assertThat(counts.get(HotScoreFirst)).isEqualTo(3L);
            assertThat(counts.get(FrontCenter)).isEqualTo(2L);
        }

        @Test
        @DisplayName("CONCERT_RUSH_13명_보정_없이_합이_정확히_13이다")
        void CONCERT_RUSH_13명_보정_없이_합이_정확히_13이다() {
            // round: HSF=7(0.5→반올림), FC=3, VIP=2, CC=1, rest=0 → sum=13 → diff=0
            List<Audience> result = CONCERT_RUSH.distribute(13, SIM_ID, new Random(42));
            Map<SeatPreferenceStrategy, Long> counts = countByStrategy(result);

            assertThat(result).hasSize(13);
            assertThat(counts.get(HotScoreFirst)).isEqualTo(7L);
            assertThat(counts.get(FrontCenter)).isEqualTo(3L);
            assertThat(counts.get(GradeVIP)).isEqualTo(2L);
        }

        @Test
        @DisplayName("BALANCED_50명_보정_없이_합이_정확히_50이다")
        void BALANCED_50명_보정_없이_합이_정확히_50이다() {
            // 12%×50=6.0, 10%×50=5.0 → sum=5×6+4×5=50 → diff=0
            List<Audience> result = BALANCED.distribute(50, SIM_ID, new Random(42));

            assertThat(result).hasSize(50);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 6. Audience 기본값 검증
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Audience 기본값 검증")
    class AudienceDefaultsTest {

        @Test
        @DisplayName("생성된_모든_Audience가_올바른_기본값을_가진다")
        void 생성된_모든_Audience가_올바른_기본값을_가진다() {
            List<Audience> result = MUSICAL_HEAVY_FRONT.distribute(20, SIM_ID, new Random(42));

            assertThat(result).allSatisfy(a -> {
                assertThat(a.getSeatCnt()).isIn(1, 2);
                assertThat(a.getSeatClickWaitJitter()).isNotNull();
                assertThat(a.getSeatClickWaitJitter().toMillis())
                        .isGreaterThanOrEqualTo(500L)
                        .isLessThan(3000L);           // nextLong(500, 3000) → [500, 3000)
                assertThat(a.getStrategy()).isNotNull();
                assertThat(a.getPreferredSeatIds()).isEmpty();
                assertThat(a.getAcquiredSeatIds()).isEmpty();
                assertThat(a.getSimulationId()).isEqualTo(SIM_ID);
            });
        }

        @Test
        @DisplayName("seatCnt=1이_60~80명_seatCnt=2가_20~40명이다")
        void seatCnt_1이_60에서80명_seatCnt_2가_20에서40명이다() {
            // 100명, SINGLE_SEAT_PROBABILITY=0.7 → 기대값 70명, 범위 [60,80]
            List<Audience> result = MUSICAL_HEAVY_FRONT.distribute(100, SIM_ID, new Random(42));

            long seatCnt1 = result.stream().filter(a -> a.getSeatCnt() == 1).count();
            long seatCnt2 = result.stream().filter(a -> a.getSeatCnt() == 2).count();

            assertThat(seatCnt1).isBetween(60L, 80L);
            assertThat(seatCnt2).isBetween(20L, 40L);
            assertThat(seatCnt1 + seatCnt2).isEqualTo(100L);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 7. 엣지 케이스
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCaseTest {

        @Test
        @DisplayName("totalCount_0이면_빈_리스트를_반환하고_예외가_없다")
        void totalCount_0이면_빈_리스트를_반환하고_예외가_없다() {
            assertThatCode(() -> {
                List<Audience> result = MUSICAL_HEAVY_FRONT.distribute(0, SIM_ID, new Random(42));
                assertThat(result).isEmpty();
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("totalCount_1이면_MUSICAL에서_HotScoreFirst에_1명_배분된다")
        void totalCount_1이면_MUSICAL에서_HotScoreFirst에_1명_배분된다() {
            // 모든 가중치 < 50 → round(0.x) = 0 → sum=0 → diff=+1 → HotScoreFirst=1
            List<Audience> result = MUSICAL_HEAVY_FRONT.distribute(1, SIM_ID, new Random(42));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStrategy()).isEqualTo(HotScoreFirst);
        }

        @Test
        @DisplayName("totalCount_1이면_CONCERT_RUSH에서_HotScoreFirst에_1명_배분된다")
        void totalCount_1이면_CONCERT_RUSH에서_HotScoreFirst에_1명_배분된다() {
            // HotScoreFirst(50%): round(0.5)=1 → sum=1 → diff=0
            List<Audience> result = CONCERT_RUSH.distribute(1, SIM_ID, new Random(42));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStrategy()).isEqualTo(HotScoreFirst);
        }

        @Test
        @DisplayName("totalCount_3이면_합이_3이고_상위_전략에_집중_배분된다")
        void totalCount_3이면_합이_3이고_상위_전략에_집중_배분된다() {
            // HSF(30%): round(0.9)=1, FC(25%): round(0.75)=1, VIP(15%): round(0.45)=0
            // sum=2, diff=+1 → HSF=2, FC=1
            List<Audience> result = MUSICAL_HEAVY_FRONT.distribute(3, SIM_ID, new Random(42));
            Map<SeatPreferenceStrategy, Long> counts = countByStrategy(result);

            assertThat(result).hasSize(3);
            assertThat(counts.getOrDefault(HotScoreFirst, 0L)).isEqualTo(2L);
            assertThat(counts.getOrDefault(FrontCenter, 0L)).isEqualTo(1L);
            // 나머지 전략은 0명 (맵에 없음)
            assertThat(counts.getOrDefault(GradeVIP, 0L)).isEqualTo(0L);
        }

        @Test
        @DisplayName("totalCount_9이면_합이_9이고_모든_인원이_0_이상이다")
        void totalCount_9이면_합이_9이고_모든_인원이_0_이상이다() {
            List<Audience> result = MUSICAL_HEAVY_FRONT.distribute(9, SIM_ID, new Random(42));
            Map<SeatPreferenceStrategy, Long> counts = countByStrategy(result);

            assertThat(result).hasSize(9);
            Arrays.stream(SeatPreferenceStrategy.values()).forEach(s ->
                    assertThat(counts.getOrDefault(s, 0L)).isGreaterThanOrEqualTo(0L));
        }

        @Test
        @DisplayName("totalCount_1000이면_합이_1000이고_비율_오차가_2퍼센트_이내이다")
        void totalCount_1000이면_합이_1000이고_비율_오차가_2퍼센트_이내이다() {
            List<Audience> result = MUSICAL_HEAVY_FRONT.distribute(1000, SIM_ID, new Random(42));
            Map<SeatPreferenceStrategy, Long> counts = countByStrategy(result);

            assertThat(result).hasSize(1000);
            MUSICAL_HEAVY_FRONT.getWeights().forEach((strategy, weight) -> {
                double expected = 1000 * weight / 100.0;
                double actual = counts.getOrDefault(strategy, 0L).doubleValue();
                assertThat(Math.abs(actual - expected) / 1000.0)
                        .as("전략 %s 비율 오차", strategy)
                        .isLessThanOrEqualTo(0.02);
            });
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 8. 셔플 검증
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("셔플 검증")
    class ShuffleTest {

        @Test
        @DisplayName("다른_시드는_전략별_인원수는_같지만_순서가_다르다")
        void 다른_시드는_전략별_인원수는_같지만_순서가_다르다() {
            List<Audience> result42 = MUSICAL_HEAVY_FRONT.distribute(20, SIM_ID, new Random(42));
            List<Audience> result99 = MUSICAL_HEAVY_FRONT.distribute(20, SIM_ID, new Random(99));

            // 전략별 인원수는 동일 (배분 알고리즘은 결정적)
            assertThat(countByStrategy(result42)).isEqualTo(countByStrategy(result99));

            // 순서(셔플)는 다름
            assertThat(strategyOrder(result42)).isNotEqualTo(strategyOrder(result99));
        }

        @Test
        @DisplayName("같은_시드로_두_번_실행하면_완전히_동일한_결과가_나온다")
        void 같은_시드로_두_번_실행하면_완전히_동일한_결과가_나온다() {
            List<Audience> result1 = MUSICAL_HEAVY_FRONT.distribute(20, SIM_ID, new Random(42));
            List<Audience> result2 = MUSICAL_HEAVY_FRONT.distribute(20, SIM_ID, new Random(42));

            assertThat(strategyOrder(result1)).isEqualTo(strategyOrder(result2));
        }

        @Test
        @DisplayName("셔플로_인해_같은_전략이_연속으로_뭉치지_않는다")
        void 셔플로_인해_같은_전략이_연속으로_뭉치지_않는다() {
            // 30명 이상이면 HotScoreFirst(30명)가 앞에 뭉칠 확률이 매우 낮음
            List<Audience> result = MUSICAL_HEAVY_FRONT.distribute(100, SIM_ID, new Random(42));
            List<SeatPreferenceStrategy> order = strategyOrder(result);

            // 첫 30개가 전부 HotScoreFirst라면 셔플이 안 된 것
            long firstThirtyHotScore = order.subList(0, 30).stream()
                    .filter(s -> s == HotScoreFirst).count();
            assertThat(firstThirtyHotScore).isLessThan(30L);
        }
    }
}
