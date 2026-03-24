package com.ticketing.domain.seat;

import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceRepository;
import com.ticketing.domain.audience.SeatPreferenceStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세 가지 락 전략의 동시성 보장 여부를 검증하는 통합 테스트.
 * Docker Compose로 MySQL(3306), Redis(6379)가 실행 중이어야 합니다.
 */
@SpringBootTest
@DisplayName("좌석 선점 락 전략 동시성 테스트")
class SeatLockConcurrencyTest {

    // 다른 데이터와 충돌하지 않는 전용 번호
    private static final int  SEAT_NO       = 9001;
    private static final long SIMULATION_ID = 9001L;
    private static final int  THREAD_COUNT  = 10;

    @Autowired private PessimisticSeatLockService pessimisticLockService;
    @Autowired private OptimisticSeatLockService  optimisticLockService;
    @Autowired private RedisSeatLockService       redisLockService;
    @Autowired private SeatRepository             seatRepository;
    @Autowired private AudienceRepository         audienceRepository;
    @Autowired private JdbcTemplate               jdbcTemplate;

    // ──────────────────────────────────────────────────────────────
    // Setup / Teardown
    // ──────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        cleanUp();

        Seat seat = Seat.builder()
                .no(SEAT_NO)
                .showId(SIMULATION_ID)
                .row(0).col(0)
                .seatStatus(SeatStatus.AVAILABLE)
                .seatGrade(SeatGrade.VIP)
                .hotScore(100)
                .build();
        seatRepository.save(seat);

        for (int i = 0; i < THREAD_COUNT; i++) {
            audienceRepository.save(Audience.builder()
                    .simulationId(SIMULATION_ID)
                    .isRealUser(false)
                    .seatCnt(1)
                    .seatClickWaitJitter(Duration.ZERO)
                    .strategy(SeatPreferenceStrategy.HotScoreFirst)
                    .build());
        }
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        audienceRepository.findAllBySimulationId(SIMULATION_ID)
                .forEach(audienceRepository::delete);   // @ElementCollection 포함 cascade
        seatRepository.findById(SEAT_NO)
                .ifPresent(seatRepository::delete);
    }

    // ──────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────

    /** N개 스레드가 동시에 action을 실행하고 결과를 반환한다. */
    private List<SeatHoldResult> runConcurrently(
            List<Long> audienceIds,
            Function<Long, SeatHoldResult> action) throws InterruptedException {

        List<SeatHoldResult> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(audienceIds.size());
        ExecutorService executor  = Executors.newFixedThreadPool(audienceIds.size());

        for (Long id : audienceIds) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    results.add(action.apply(id));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    results.add(SeatHoldResult.FAIL);
                } catch (Exception e) {
                    results.add(SeatHoldResult.FAIL);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();                          // 동시 시작
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        return results;
    }

    private List<Long> audienceIds() {
        return audienceRepository.findAllBySimulationId(SIMULATION_ID)
                .stream().map(Audience::getId).collect(Collectors.toList());
    }

    /** @ElementCollection(audience_acquired_seats)에서 특정 좌석을 가진 audience 수 조회 */
    private int countAudiencesWithAcquiredSeat(int seatNo) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audience_acquired_seats WHERE seat_no = ?",
                Integer.class, seatNo);
        return count != null ? count : 0;
    }

    // ──────────────────────────────────────────────────────────────
    // 1. 비관적 락
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("비관적 락 (PessimisticSeatLockService)")
    class PessimisticLockTest {

        @Test
        @DisplayName("단일 요청 → SUCCESS")
        void 단일_요청은_SUCCESS() {
            Long id = audienceIds().get(0);
            assertThat(pessimisticLockService.hold(SEAT_NO, id))
                    .isEqualTo(SeatHoldResult.SUCCESS);
        }

        @Test
        @DisplayName("이미 HELD 좌석 재시도 → DUPLICATE")
        void 이미_선점된_좌석은_DUPLICATE() {
            List<Long> ids = audienceIds();
            pessimisticLockService.hold(SEAT_NO, ids.get(0));

            assertThat(pessimisticLockService.hold(SEAT_NO, ids.get(1)))
                    .isEqualTo(SeatHoldResult.DUPLICATE);
        }

        @Test
        @DisplayName("존재하지 않는 audience → FAIL")
        void 존재하지_않는_audience는_FAIL() {
            assertThat(pessimisticLockService.hold(SEAT_NO, -1L))
                    .isEqualTo(SeatHoldResult.FAIL);
        }

        @Test
        @DisplayName("N개 동시 요청 → 정확히 1개만 SUCCESS")
        void 동시_요청_정확히_1개_SUCCESS() throws InterruptedException {
            List<SeatHoldResult> results =
                    runConcurrently(audienceIds(), id -> pessimisticLockService.hold(SEAT_NO, id));

            assertThat(results).hasSize(THREAD_COUNT);
            assertThat(results.stream().filter(r -> r == SeatHoldResult.SUCCESS).count())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("N개 동시 요청 → 나머지는 모두 DUPLICATE")
        void 동시_요청_나머지는_DUPLICATE() throws InterruptedException {
            List<SeatHoldResult> results =
                    runConcurrently(audienceIds(), id -> pessimisticLockService.hold(SEAT_NO, id));

            long notSuccess = results.stream().filter(r -> r != SeatHoldResult.SUCCESS).count();
            long duplicate  = results.stream().filter(r -> r == SeatHoldResult.DUPLICATE).count();

            // DB 락을 획득한 순서대로 순차 실행 → 이후 요청은 모두 seat=HELD 를 확인하고 DUPLICATE
            assertThat(duplicate).isEqualTo(notSuccess);
        }

        @Test
        @DisplayName("N개 동시 요청 후 DB 상태 검증 → seat HELD + acquiredSeat 1건")
        void 동시_요청_후_DB_상태_검증() throws InterruptedException {
            runConcurrently(audienceIds(), id -> pessimisticLockService.hold(SEAT_NO, id));

            Seat seat = seatRepository.findByNo(SEAT_NO).orElseThrow();
            assertThat(seat.getSeatStatus()).isEqualTo(SeatStatus.HELD);
            assertThat(seat.getHolderId()).isNotNull();
            assertThat(countAudiencesWithAcquiredSeat(SEAT_NO)).isEqualTo(1);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 2. 낙관적 락
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("낙관적 락 (OptimisticSeatLockService)")
    class OptimisticLockTest {

        @Test
        @DisplayName("단일 요청 → SUCCESS")
        void 단일_요청은_SUCCESS() {
            Long id = audienceIds().get(0);
            assertThat(optimisticLockService.hold(SEAT_NO, id))
                    .isEqualTo(SeatHoldResult.SUCCESS);
        }

        @Test
        @DisplayName("이미 HELD 좌석 재시도 → FAIL (isAvailable=false 분기)")
        void 이미_선점된_좌석은_FAIL() {
            List<Long> ids = audienceIds();
            optimisticLockService.hold(SEAT_NO, ids.get(0));

            // OptimisticSeatLockService: seat.isAvailable()==false → FAIL (DUPLICATE 아님)
            assertThat(optimisticLockService.hold(SEAT_NO, ids.get(1)))
                    .isEqualTo(SeatHoldResult.FAIL);
        }

        @Test
        @DisplayName("존재하지 않는 audience → FAIL")
        void 존재하지_않는_audience는_FAIL() {
            assertThat(optimisticLockService.hold(SEAT_NO, -1L))
                    .isEqualTo(SeatHoldResult.FAIL);
        }

        @Test
        @DisplayName("N개 동시 요청 → 정확히 1개만 SUCCESS")
        void 동시_요청_정확히_1개_SUCCESS() throws InterruptedException {
            List<SeatHoldResult> results =
                    runConcurrently(audienceIds(), id -> optimisticLockService.hold(SEAT_NO, id));

            assertThat(results).hasSize(THREAD_COUNT);
            assertThat(results.stream().filter(r -> r == SeatHoldResult.SUCCESS).count())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("N개 동시 요청 → 비성공 결과는 FAIL 또는 DUPLICATE만 존재")
        void 동시_요청_비성공_결과는_FAIL_또는_DUPLICATE() throws InterruptedException {
            List<SeatHoldResult> results =
                    runConcurrently(audienceIds(), id -> optimisticLockService.hold(SEAT_NO, id));

            // DB 속도에 따라:
            //   빠른 경우: thread1 커밋 후 나머지가 seat=HELD 확인 → FAIL
            //   느린 경우: 복수 스레드가 동시에 AVAILABLE 읽고 write 충돌 → DUPLICATE
            // 어떤 경우든 SUCCESS 이외의 결과는 FAIL 또는 DUPLICATE여야 한다
            results.stream()
                    .filter(r -> r != SeatHoldResult.SUCCESS)
                    .forEach(r -> assertThat(r)
                            .isIn(SeatHoldResult.FAIL, SeatHoldResult.DUPLICATE));
        }

        @Test
        @DisplayName("N개 동시 요청 후 DB 상태 검증 → seat HELD + acquiredSeat 1건")
        void 동시_요청_후_DB_상태_검증() throws InterruptedException {
            runConcurrently(audienceIds(), id -> optimisticLockService.hold(SEAT_NO, id));

            Seat seat = seatRepository.findByNo(SEAT_NO).orElseThrow();
            assertThat(seat.getSeatStatus()).isEqualTo(SeatStatus.HELD);
            assertThat(countAudiencesWithAcquiredSeat(SEAT_NO)).isEqualTo(1);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 3. Redis 분산 락
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Redis 분산 락 (RedisSeatLockService)")
    class RedisLockTest {

        @Test
        @DisplayName("단일 요청 → SUCCESS")
        void 단일_요청은_SUCCESS() {
            Long id = audienceIds().get(0);
            assertThat(redisLockService.hold(SEAT_NO, id))
                    .isEqualTo(SeatHoldResult.SUCCESS);
        }

        @Test
        @DisplayName("이미 HELD 좌석 재시도 → DUPLICATE")
        void 이미_선점된_좌석은_DUPLICATE() {
            List<Long> ids = audienceIds();
            redisLockService.hold(SEAT_NO, ids.get(0));

            assertThat(redisLockService.hold(SEAT_NO, ids.get(1)))
                    .isEqualTo(SeatHoldResult.DUPLICATE);
        }

        @Test
        @DisplayName("존재하지 않는 audience → FAIL")
        void 존재하지_않는_audience는_FAIL() {
            assertThat(redisLockService.hold(SEAT_NO, -1L))
                    .isEqualTo(SeatHoldResult.FAIL);
        }

        @Test
        @DisplayName("N개 동시 요청 → 정확히 1개만 SUCCESS")
        void 동시_요청_정확히_1개_SUCCESS() throws InterruptedException {
            List<SeatHoldResult> results =
                    runConcurrently(audienceIds(), id -> redisLockService.hold(SEAT_NO, id));

            assertThat(results).hasSize(THREAD_COUNT);
            assertThat(results.stream().filter(r -> r == SeatHoldResult.SUCCESS).count())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("N개 동시 요청 → 나머지는 모두 DUPLICATE (순차 실행 보장)")
        void 동시_요청_나머지는_DUPLICATE() throws InterruptedException {
            List<SeatHoldResult> results =
                    runConcurrently(audienceIds(), id -> redisLockService.hold(SEAT_NO, id));

            long notSuccess = results.stream().filter(r -> r != SeatHoldResult.SUCCESS).count();
            long duplicate  = results.stream().filter(r -> r == SeatHoldResult.DUPLICATE).count();

            // Redis 락으로 완전한 순차 실행 보장 → 이후 요청은 모두 seat=HELD 확인 후 DUPLICATE
            assertThat(duplicate).isEqualTo(notSuccess);
        }

        @Test
        @DisplayName("N개 동시 요청 후 DB 상태 검증 → seat HELD + acquiredSeat 1건")
        void 동시_요청_후_DB_상태_검증() throws InterruptedException {
            runConcurrently(audienceIds(), id -> redisLockService.hold(SEAT_NO, id));

            Seat seat = seatRepository.findByNo(SEAT_NO).orElseThrow();
            assertThat(seat.getSeatStatus()).isEqualTo(SeatStatus.HELD);
            assertThat(seat.getHolderId()).isNotNull();
            assertThat(countAudiencesWithAcquiredSeat(SEAT_NO)).isEqualTo(1);
        }
    }
}
