package com.ticketing.domain.audience;

import com.ticketing.domain.seat.Seat;
import com.ticketing.domain.seat.SeatGrade;
import com.ticketing.domain.seat.SeatSettingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.ticketing.domain.audience.SeatPreferenceStrategy.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SeatPreferenceStrategy 좌석 선택 로직 테스트")
class SeatPreferenceStrategyTest {

    private static final int MAX_ROW = 10;
    private static final int MAX_COL = 16;
    // center = (16-1)/2.0 = 7.5,  midRow = (10-1)/2.0 = 4.5
    private static final double CENTER = (MAX_COL - 1) / 2.0;
    private static final double MID_ROW = (MAX_ROW - 1) / 2.0;

    private List<Seat> seats;
    private Map<Integer, Seat> seatMap;

    @BeforeEach
    void setUp() {
        seats = SeatSettingStrategy.MUSICAL_STANDARD.generateSeats(1L, MAX_ROW, MAX_COL);
        seatMap = seats.stream().collect(Collectors.toMap(Seat::getNo, s -> s));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────
    private Seat seat(int no) {
        return seatMap.get(no);
    }

    private void holdAll() {
        seats.forEach(s -> s.hold(1L));
    }

    /** 앞에서 keepCount개만 AVAILABLE, 나머지는 HELD */
    private void holdExcept(int keepCount) {
        seats.stream().skip(keepCount).forEach(s -> s.hold(1L));
    }

    /** 전략 정렬 기준으로 상위 n개의 seat.no 집합 반환 */
    private Set<Integer> topNByComparator(Comparator<Seat> comp, int n) {
        return seats.stream()
                .sorted(comp)
                .limit(n)
                .map(Seat::getNo)
                .collect(Collectors.toSet());
    }

    // ══════════════════════════════════════════════════════════════════
    // 1. HotScoreFirst
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("HotScoreFirst — 인기석 우선")
    class HotScoreFirstTest {

        @Test
        @DisplayName("반환된_좌석이_모두_hotScore_상위_9개_풀_안에_포함된다")
        void 반환된_좌석이_모두_hotScore_상위_9개_풀_안에_포함된다() {
            Set<Integer> top9 = topNByComparator(
                    Comparator.comparingInt(Seat::getHotScore).reversed().thenComparingInt(Seat::getNo), 9);

            List<Integer> result = HotScoreFirst.selectPreferred(seats, 3, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).hasSize(3);
            assertThat(result).isSubsetOf(top9);
        }

        @Test
        @DisplayName("반환된_좌석의_평균_hotScore가_전체_평균보다_높다")
        void 반환된_좌석의_평균_hotScore가_전체_평균보다_높다() {
            double overallAvg = seats.stream().mapToInt(Seat::getHotScore).average().orElse(0);

            List<Integer> result = HotScoreFirst.selectPreferred(seats, 3, MAX_ROW, MAX_COL, new Random(42));

            double resultAvg = result.stream().mapToInt(no -> seat(no).getHotScore()).average().orElse(0);
            assertThat(resultAvg).isGreaterThan(overallAvg);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. HotScoreLast
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("HotScoreLast — 비인기석 우선")
    class HotScoreLastTest {

        @Test
        @DisplayName("반환된_좌석이_모두_hotScore_하위_9개_풀_안에_포함된다")
        void 반환된_좌석이_모두_hotScore_하위_9개_풀_안에_포함된다() {
            Set<Integer> bottom9 = topNByComparator(
                    Comparator.comparingInt(Seat::getHotScore).thenComparingInt(Seat::getNo), 9);

            List<Integer> result = HotScoreLast.selectPreferred(seats, 3, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).hasSize(3);
            assertThat(result).isSubsetOf(bottom9);
        }

        @Test
        @DisplayName("반환된_좌석의_평균_hotScore가_전체_평균보다_낮다")
        void 반환된_좌석의_평균_hotScore가_전체_평균보다_낮다() {
            double overallAvg = seats.stream().mapToInt(Seat::getHotScore).average().orElse(0);

            List<Integer> result = HotScoreLast.selectPreferred(seats, 3, MAX_ROW, MAX_COL, new Random(42));

            double resultAvg = result.stream().mapToInt(no -> seat(no).getHotScore()).average().orElse(0);
            assertThat(resultAvg).isLessThan(overallAvg);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. FrontCenter
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("FrontCenter — 앞줄 중앙 우선")
    class FrontCenterTest {

        @Test
        @DisplayName("반환된_좌석이_앞쪽_3행_이내이고_중앙_근처_열이다")
        void 반환된_좌석이_앞쪽_3행_이내이고_중앙_근처_열이다() {
            List<Integer> result = FrontCenter.selectPreferred(seats, 2, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).hasSize(2);
            assertThat(result).allSatisfy(no -> {
                assertThat(seat(no).getRow()).isLessThanOrEqualTo(2);
                assertThat(Math.abs(seat(no).getCol() - CENTER)).isLessThanOrEqualTo(4.0);
            });
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 4. CenterCenter
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("CenterCenter — 정중앙 우선")
    class CenterCenterTest {

        @Test
        @DisplayName("반환된_좌석의_맨해튼_거리가_5_이하이다")
        void 반환된_좌석의_맨해튼_거리가_5_이하이다() {
            List<Integer> result = CenterCenter.selectPreferred(seats, 2, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).hasSize(2);
            assertThat(result).allSatisfy(no -> {
                double dist = Math.abs(seat(no).getRow() - MID_ROW) + Math.abs(seat(no).getCol() - CENTER);
                assertThat(dist).isLessThanOrEqualTo(5.0);
            });
        }

        @Test
        @DisplayName("반환된_좌석이_중간_행_근처이며_FrontCenter와_다른_결과이다")
        void 반환된_좌석이_중간_행_근처이며_FrontCenter와_다른_결과이다() {
            List<Integer> frontResult = FrontCenter.selectPreferred(seats, 2, MAX_ROW, MAX_COL, new Random(42));
            List<Integer> centerResult = CenterCenter.selectPreferred(seats, 2, MAX_ROW, MAX_COL, new Random(42));

            assertThat(centerResult).allSatisfy(no ->
                    assertThat(seat(no).getRow()).isBetween(3, 6));
            assertThat(centerResult).isNotEqualTo(frontResult);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 5. BehindCenter
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("BehindCenter — 뒷줄 중앙 우선")
    class BehindCenterTest {

        @Test
        @DisplayName("반환된_좌석이_모두_뒤쪽_3행_이내이다")
        void 반환된_좌석이_모두_뒤쪽_3행_이내이다() {
            List<Integer> result = BehindCenter.selectPreferred(seats, 2, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).hasSize(2);
            assertThat(result).allSatisfy(no ->
                    assertThat(seat(no).getRow()).isGreaterThanOrEqualTo(MAX_ROW - 3));
        }

        @Test
        @DisplayName("BehindCenter와_FrontCenter_결과는_겹치지_않는다")
        void BehindCenter와_FrontCenter_결과는_겹치지_않는다() {
            List<Integer> frontResult = FrontCenter.selectPreferred(seats, 2, MAX_ROW, MAX_COL, new Random(42));
            List<Integer> behindResult = BehindCenter.selectPreferred(seats, 2, MAX_ROW, MAX_COL, new Random(42));

            assertThat(behindResult).doesNotContainAnyElementsOf(frontResult);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 6. FrontRight
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("FrontRight — 앞줄 우측 우선")
    class FrontRightTest {

        @Test
        @DisplayName("반환된_좌석이_앞쪽_3행_이내이고_열_평균이_center보다_크다")
        void 반환된_좌석이_앞쪽_3행_이내이고_열_평균이_center보다_크다() {
            List<Integer> result = FrontRight.selectPreferred(seats, 3, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).hasSize(3);
            assertThat(result).allSatisfy(no ->
                    assertThat(seat(no).getRow()).isLessThanOrEqualTo(2));

            double colAvg = result.stream().mapToInt(no -> seat(no).getCol()).average().orElse(0);
            assertThat(colAvg).isGreaterThan(CENTER);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 7. FrontLeft
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("FrontLeft — 앞줄 좌측 우선")
    class FrontLeftTest {

        @Test
        @DisplayName("반환된_좌석이_앞쪽_3행_이내이고_열_평균이_center보다_작다")
        void 반환된_좌석이_앞쪽_3행_이내이고_열_평균이_center보다_작다() {
            List<Integer> result = FrontLeft.selectPreferred(seats, 3, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).hasSize(3);
            assertThat(result).allSatisfy(no ->
                    assertThat(seat(no).getRow()).isLessThanOrEqualTo(2));

            double colAvg = result.stream().mapToInt(no -> seat(no).getCol()).average().orElse(0);
            assertThat(colAvg).isLessThan(CENTER);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 8. GradeVIP
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GradeVIP — VIP 등급 전용")
    class GradeVIPTest {

        @Test
        @DisplayName("반환된_좌석이_모두_VIP_등급이며_R_등급은_없다")
        void 반환된_좌석이_모두_VIP_등급이며_R_등급은_없다() {
            List<Integer> result = GradeVIP.selectPreferred(seats, 3, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).hasSize(3);
            assertThat(result).allSatisfy(no ->
                    assertThat(seat(no).getSeatGrade()).isEqualTo(SeatGrade.VIP));
            assertThat(result).noneMatch(no -> seat(no).getSeatGrade() == SeatGrade.R);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 9. GradeR
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GradeR — R 등급 전용")
    class GradeRTest {

        @Test
        @DisplayName("반환된_좌석이_모두_R_등급이다")
        void 반환된_좌석이_모두_R_등급이다() {
            List<Integer> result = GradeR.selectPreferred(seats, 3, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).hasSize(3);
            assertThat(result).allSatisfy(no ->
                    assertThat(seat(no).getSeatGrade()).isEqualTo(SeatGrade.R));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 10. Jitter
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Jitter 동작 검증")
    class JitterTest {

        @Test
        @DisplayName("다른_시드는_같은_풀_안에서_다른_결과를_만든다")
        void 다른_시드는_같은_풀_안에서_다른_결과를_만든다() {
            Set<Integer> top9 = topNByComparator(
                    Comparator.comparingInt(Seat::getHotScore).reversed().thenComparingInt(Seat::getNo), 9);

            List<Integer> result42 = HotScoreFirst.selectPreferred(seats, 3, MAX_ROW, MAX_COL, new Random(42));
            List<Integer> result99 = HotScoreFirst.selectPreferred(seats, 3, MAX_ROW, MAX_COL, new Random(99));

            assertThat(result42).isNotEqualTo(result99);
            assertThat(result42).isSubsetOf(top9);
            assertThat(result99).isSubsetOf(top9);
        }

        @Test
        @DisplayName("같은_시드는_항상_동일한_결과를_반환한다")
        void 같은_시드는_항상_동일한_결과를_반환한다() {
            List<Integer> result1 = HotScoreFirst.selectPreferred(seats, 3, MAX_ROW, MAX_COL, new Random(42));
            List<Integer> result2 = HotScoreFirst.selectPreferred(seats, 3, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result1).isEqualTo(result2);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 11. 엣지 케이스
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCaseTest {

        @Test
        @DisplayName("AVAILABLE_좌석이_0개이면_빈_리스트를_반환한다")
        void AVAILABLE_좌석이_0개이면_빈_리스트를_반환한다() {
            holdAll();

            List<Integer> result = HotScoreFirst.selectPreferred(seats, 3, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("AVAILABLE_좌석이_seatCnt보다_적으면_있는_만큼만_반환한다")
        void AVAILABLE_좌석이_seatCnt보다_적으면_있는_만큼만_반환한다() {
            holdExcept(2); // 2석만 AVAILABLE

            List<Integer> result = HotScoreFirst.selectPreferred(seats, 5, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("GradeVIP_전략인데_VIP_좌석이_0개이면_빈_리스트를_반환한다")
        void GradeVIP_전략인데_VIP_좌석이_0개이면_빈_리스트를_반환한다() {
            seats.stream()
                    .filter(s -> s.getSeatGrade() == SeatGrade.VIP)
                    .forEach(s -> s.hold(1L));

            List<Integer> result = GradeVIP.selectPreferred(seats, 3, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("seatCnt가_0이면_빈_리스트를_반환한다")
        void seatCnt가_0이면_빈_리스트를_반환한다() {
            List<Integer> result = HotScoreFirst.selectPreferred(seats, 0, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("seatCnt가_1이면_정확히_1개만_반환한다")
        void seatCnt가_1이면_정확히_1개만_반환한다() {
            List<Integer> result = HotScoreFirst.selectPreferred(seats, 1, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).hasSize(1);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 12. 풀 크기 검증
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("풀 크기 검증")
    class PoolSizeTest {

        @Test
        @DisplayName("HotScoreFirst_결과는_hotScore_상위_15개_풀_안에_속한다")
        void HotScoreFirst_결과는_hotScore_상위_15개_풀_안에_속한다() {
            Set<Integer> top15 = topNByComparator(
                    Comparator.comparingInt(Seat::getHotScore).reversed().thenComparingInt(Seat::getNo), 15);

            List<Integer> result = HotScoreFirst.selectPreferred(seats, 5, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).hasSize(5);
            assertThat(result).isSubsetOf(top15);
        }

        @Test
        @DisplayName("FrontCenter_결과는_row_정렬_상위_15개_풀_안에_속한다")
        void FrontCenter_결과는_row_정렬_상위_15개_풀_안에_속한다() {
            Set<Integer> top15 = topNByComparator(
                    Comparator.comparingInt(Seat::getRow)
                            .thenComparingDouble(s -> Math.abs(s.getCol() - CENTER))
                            .thenComparingInt(Seat::getNo), 15);

            List<Integer> result = FrontCenter.selectPreferred(seats, 5, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).hasSize(5);
            assertThat(result).isSubsetOf(top15);
        }

        @Test
        @DisplayName("AVAILABLE이_풀_크기보다_적을_때도_seatCnt만큼_반환한다")
        void AVAILABLE이_풀_크기보다_적을_때도_seatCnt만큼_반환한다() {
            // 10석만 AVAILABLE → pool = min(5×3=15, 10) = 10 → pick 5
            holdExcept(10);

            List<Integer> result = HotScoreFirst.selectPreferred(seats, 5, MAX_ROW, MAX_COL, new Random(42));

            assertThat(result).hasSize(5);
        }
    }
}
