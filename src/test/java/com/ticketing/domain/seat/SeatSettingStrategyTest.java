package com.ticketing.domain.seat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.ticketing.domain.seat.SeatSettingStrategy.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("SeatSettingStrategy 좌석 세팅 로직 테스트")
class SeatSettingStrategyTest {

    private static Seat findSeat(List<Seat> seats, int row, int col) {
        return seats.stream()
                .filter(s -> s.getRow() == row && s.getCol() == col)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Seat not found: row=" + row + ", col=" + col));
    }

    // ──────────────────────────────────────────────────────────────
    // 1. MUSICAL_STANDARD
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("MUSICAL_STANDARD 전략 (distWeight=0.55, centerWeight=0.45, vipRowRatio=0.45, vipColRatio=0.55)")
    class MusicalStandardTest {

        private static final int MAX_ROW = 10;
        private static final int MAX_COL = 16;
        private List<Seat> seats;

        @BeforeEach
        void setUp() {
            seats = MUSICAL_STANDARD.generateSeats(1L, MAX_ROW, MAX_COL);
        }

        @Nested
        @DisplayName("hotScore 검증")
        class HotScoreTest {

            @Test
            @DisplayName("앞줄_중앙(0,8)이_가장_높은_hotScore를_가진다 (95~100)")
            void 앞줄_중앙이_가장_높은_hotScore를_가진다() {
                // distanceScore=1, centerScore=1 → (0.55+0.45)*100 = 100
                assertThat(findSeat(seats, 0, 8).getHotScore()).isBetween(95, 100);
            }

            @Test
            @DisplayName("앞줄_사이드(0,0)는_중간_정도의_hotScore를_가진다 (50~60)")
            void 앞줄_사이드는_중간_정도의_hotScore를_가진다() {
                // distanceScore=1, centerScore=0 → 0.55*100 = 55
                assertThat(findSeat(seats, 0, 0).getHotScore()).isBetween(50, 60);
            }

            @Test
            @DisplayName("뒷줄_중앙(9,8)은_중간_정도의_hotScore를_가진다 (40~50)")
            void 뒷줄_중앙은_중간_정도의_hotScore를_가진다() {
                // distanceScore=0, centerScore=1 → 0.45*100 = 45
                assertThat(findSeat(seats, 9, 8).getHotScore()).isBetween(40, 50);
            }

            @Test
            @DisplayName("뒷줄_사이드(9,0)가_가장_낮은_hotScore를_가진다 (0~5)")
            void 뒷줄_사이드가_가장_낮은_hotScore를_가진다() {
                // distanceScore=0, centerScore=0 → 0
                assertThat(findSeat(seats, 9, 0).getHotScore()).isBetween(0, 5);
            }

            @Test
            @DisplayName("hotScore_순서: 앞줄중앙 > 앞줄사이드 > 뒷줄사이드, 앞줄중앙 > 뒷줄중앙 > 뒷줄사이드")
            void hotScore_내림차순_순서() {
                int frontCenter = findSeat(seats, 0, 8).getHotScore();
                int frontSide   = findSeat(seats, 0, 0).getHotScore();
                int backCenter  = findSeat(seats, 9, 8).getHotScore();
                int backSide    = findSeat(seats, 9, 0).getHotScore();

                assertThat(frontCenter).isGreaterThan(frontSide);
                assertThat(frontCenter).isGreaterThan(backCenter);
                assertThat(frontSide).isGreaterThan(backSide);
                assertThat(backCenter).isGreaterThan(backSide);
            }
        }

        @Nested
        @DisplayName("seatGrade 검증")
        class SeatGradeTest {

            @Test
            @DisplayName("앞줄_중앙(0,8)은_VIP_등급이다")
            void 앞줄_중앙은_VIP_등급이다() {
                assertThat(findSeat(seats, 0, 8).getSeatGrade()).isEqualTo(SeatGrade.VIP);
            }

            @Test
            @DisplayName("앞줄_사이드(0,0)는_R_등급이다 (vipColRatio 범위 밖)")
            void 앞줄_사이드는_R_등급이다() {
                assertThat(findSeat(seats, 0, 0).getSeatGrade()).isEqualTo(SeatGrade.R);
            }

            @Test
            @DisplayName("뒷줄_중앙(9,8)은_R_등급이다 (vipRowRatio 범위 밖)")
            void 뒷줄_중앙은_R_등급이다() {
                assertThat(findSeat(seats, 9, 8).getSeatGrade()).isEqualTo(SeatGrade.R);
            }

            @Test
            @DisplayName("VIP_좌석_수가_전체의_20~30% 범위이다")
            void VIP_좌석_수가_전체의_20_30퍼센트_범위이다() {
                // vipRow: row < 10*0.45=4.5 → rows 0~4 (5행)
                // vipCol: |col-8| <= 8*0.55=4.4 → cols 4~12 (9열)
                // VIP = 5*9 = 45 / 160 = 28.1%
                long vipCount = seats.stream().filter(s -> s.getSeatGrade() == SeatGrade.VIP).count();
                double vipRatio = (double) vipCount / seats.size();
                assertThat(vipRatio).isBetween(0.20, 0.30);
            }
        }

        @Nested
        @DisplayName("hotScore 분포 검증")
        class HotScoreDistributionTest {

            @Test
            @DisplayName("hotScore_80이상_좌석이_전체의_10~25% 범위이다")
            void hotScore_80이상_분포가_10_25퍼센트이다() {
                long highCount = seats.stream().filter(s -> s.getHotScore() >= 80).count();
                double ratio = (double) highCount / seats.size();
                assertThat(ratio).isBetween(0.10, 0.25);
            }

            @Test
            @DisplayName("hotScore_20이하_좌석이_전체의_10~25% 범위이다")
            void hotScore_20이하_분포가_10_25퍼센트이다() {
                long lowCount = seats.stream().filter(s -> s.getHotScore() <= 20).count();
                double ratio = (double) lowCount / seats.size();
                assertThat(ratio).isBetween(0.10, 0.25);
            }

            @Test
            @DisplayName("모든_hotScore가_0~100_범위이다")
            void 모든_hotScore가_유효_범위이다() {
                assertThat(seats).allSatisfy(seat ->
                        assertThat(seat.getHotScore()).isBetween(0, 100)
                );
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 2. CONCERT_FRONT
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("CONCERT_FRONT 전략 (distWeight=0.8, centerWeight=0.2, vipRowRatio=0.5, vipColRatio=1.0)")
    class ConcertFrontTest {

        private static final int MAX_ROW = 10;
        private static final int MAX_COL = 16;
        private List<Seat> seats;

        @BeforeEach
        void setUp() {
            seats = CONCERT_FRONT.generateSeats(1L, MAX_ROW, MAX_COL);
        }

        @Nested
        @DisplayName("hotScore 검증")
        class HotScoreTest {

            @Test
            @DisplayName("같은_행에서_col_변화에_따른_hotScore_차이가_20_이하이다 (거리 가중치 0.8 지배)")
            void 같은_행에서_col_차이에_의한_hotScore_차이가_작다() {
                // (0,0): distanceScore=1, centerScore=0 → 80
                // (0,8): distanceScore=1, centerScore=1 → 100  차이=20
                int frontSide   = findSeat(seats, 0, 0).getHotScore();
                int frontCenter = findSeat(seats, 0, 8).getHotScore();
                assertThat(Math.abs(frontCenter - frontSide)).isLessThanOrEqualTo(20);
            }

            @Test
            @DisplayName("같은_열에서_row_변화에_따른_hotScore_차이가_60_이상이다")
            void 같은_열에서_row_차이에_의한_hotScore_차이가_크다() {
                // (0,8): 100, (9,8): distanceScore=0, centerScore=1 → 20  차이=80
                int frontCenter = findSeat(seats, 0, 8).getHotScore();
                int backCenter  = findSeat(seats, 9, 8).getHotScore();
                assertThat(frontCenter - backCenter).isGreaterThanOrEqualTo(60);
            }
        }

        @Nested
        @DisplayName("seatGrade 검증")
        class SeatGradeTest {

            @Test
            @DisplayName("앞_50%_행은_col_무관하게_전부_VIP_이다 (vipColRatio=1.0)")
            void 앞50퍼센트_행은_col_무관하게_전부_VIP이다() {
                // rows 0~4, 모든 col
                List<Seat> frontSeats = seats.stream()
                        .filter(s -> s.getRow() < MAX_ROW / 2)
                        .toList();
                assertThat(frontSeats).allSatisfy(seat ->
                        assertThat(seat.getSeatGrade())
                                .as("row=%d, col=%d", seat.getRow(), seat.getCol())
                                .isEqualTo(SeatGrade.VIP)
                );
            }

            @Test
            @DisplayName("(row=4,col=0)은_VIP_이다")
            void row4_col0은_VIP이다() {
                assertThat(findSeat(seats, 4, 0).getSeatGrade()).isEqualTo(SeatGrade.VIP);
            }

            @Test
            @DisplayName("(row=5,col=8)은_R_이다")
            void row5_col8은_R이다() {
                assertThat(findSeat(seats, 5, 8).getSeatGrade()).isEqualTo(SeatGrade.R);
            }

            @Test
            @DisplayName("VIP_좌석_수가_전체의_약_50%이다")
            void VIP_좌석_수가_전체의_50퍼센트이다() {
                long vipCount = seats.stream().filter(s -> s.getSeatGrade() == SeatGrade.VIP).count();
                double vipRatio = (double) vipCount / seats.size();
                assertThat(vipRatio).isBetween(0.45, 0.55);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 3. UNIFORM
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("UNIFORM 전략")
    class UniformTest {

        @Test
        @DisplayName("모든_좌석의_hotScore가_50이다")
        void 모든_좌석의_hotScore가_50이다() {
            List<Seat> seats = UNIFORM.generateSeats(1L, 10, 16);
            assertThat(seats).allSatisfy(seat ->
                    assertThat(seat.getHotScore()).isEqualTo(50)
            );
        }

        @Test
        @DisplayName("모든_좌석의_seatGrade가_R이다")
        void 모든_좌석의_seatGrade가_R이다() {
            List<Seat> seats = UNIFORM.generateSeats(1L, 10, 16);
            assertThat(seats).allSatisfy(seat ->
                    assertThat(seat.getSeatGrade()).isEqualTo(SeatGrade.R)
            );
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 4. 경계값 테스트
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("경계값 테스트")
    class BoundaryTest {

        @Test
        @DisplayName("1행_1열_최소크기에서_divide_by_zero_없이_hotScore를_반환한다")
        void 일행_일열_최소크기에서_예외_없이_동작한다() {
            assertThatCode(() -> {
                List<Seat> seats = MUSICAL_STANDARD.generateSeats(1L, 1, 1);
                assertThat(seats).hasSize(1);
                assertThat(seats.get(0).getHotScore()).isBetween(0, 100);
            }).doesNotThrowAnyException();

            assertThatCode(() -> {
                List<Seat> seats = UNIFORM.generateSeats(1L, 1, 1);
                assertThat(seats.get(0).getHotScore()).isEqualTo(50);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("1행_16열_단일행에서_distanceScore_divide_by_zero_없이_동작한다 (maxRow-1=0 처리)")
        void 일행_십육열_단일행에서_예외_없이_동작한다() {
            assertThatCode(() -> {
                List<Seat> seats = MUSICAL_STANDARD.generateSeats(1L, 1, 16);
                assertThat(seats).hasSize(16);
                // 단일 행은 최전방으로 처리 → distanceScore=1
                // 중앙(col=8): centerScore=1 → hotScore=100
                assertThat(findSeat(seats, 0, 8).getHotScore()).isEqualTo(100);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("10행_1열_단일열에서_centerScore_divide_by_zero_없이_동작한다 (center=0.5 처리)")
        void 십행_일열_단일열에서_예외_없이_동작한다() {
            assertThatCode(() -> {
                List<Seat> seats = MUSICAL_STANDARD.generateSeats(1L, 10, 1);
                assertThat(seats).hasSize(10);
                // 단일 열은 중앙으로 처리 → centerScore=1
                // 앞줄(row=0): distanceScore=1 → hotScore=100
                assertThat(findSeat(seats, 0, 0).getHotScore()).isEqualTo(100);
            }).doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 5. 좌석 생성 기본 검증
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("좌석 생성 기본 검증")
    class SeatGenerationTest {

        @Test
        @DisplayName("10행_16열_총_160석이_생성된다")
        void 십행_십육열_총_160석이_생성된다() {
            List<Seat> seats = MUSICAL_STANDARD.generateSeats(1L, 10, 16);
            assertThat(seats).hasSize(160);
        }

        @Test
        @DisplayName("모든_좌석의_simulationId가_올바르게_세팅된다")
        void 모든_좌석의_simulationId가_올바르게_세팅된다() {
            long simId = 42L;
            List<Seat> seats = MUSICAL_STANDARD.generateSeats(simId, 10, 16);
            assertThat(seats).allSatisfy(seat ->
                    assertThat(seat.getSimulationId()).isEqualTo(simId)
            );
        }

        @Test
        @DisplayName("모든_좌석의_초기_상태가_AVAILABLE이다")
        void 모든_좌석의_초기_상태가_AVAILABLE이다() {
            List<Seat> seats = MUSICAL_STANDARD.generateSeats(1L, 10, 16);
            assertThat(seats).allSatisfy(seat ->
                    assertThat(seat.getSeatStatus()).isEqualTo(SeatStatus.AVAILABLE)
            );
        }

        @Test
        @DisplayName("모든_row_col_조합이_빠짐없이_생성된다")
        void 모든_row_col_조합이_생성된다() {
            List<Seat> seats = MUSICAL_STANDARD.generateSeats(1L, 10, 16);
            for (int r = 0; r < 10; r++) {
                for (int c = 0; c < 16; c++) {
                    final int row = r, col = c;
                    assertThat(seats).anyMatch(s -> s.getRow() == row && s.getCol() == col);
                }
            }
        }
    }
}
