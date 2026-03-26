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

    private static final long SIMULATION_ID = 9001L;
    private static final int  THREAD_COUNT  = 10;

    // setUp에서 DB 저장 후 할당되는 seat ID
    private Long seatId;

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
                .simulationId(SIMULATION_ID)
                .row(0).col(0)
                .seatStatus(SeatStatus.AVAILABLE)
                .seatGrade(SeatGrade.VIP)
                .hotScore(100)
                .build();
        seatRepository.save(seat);
        seatId = seat.getId();

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
                .forEach(audienceRepository::delete);
        seatRepository.findAllBySimulationId(SIMULATION_ID)
                .forEach(seatRepository::delete);
    }

    // ──────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────

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

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        return results;
    }

    private List<Long> audienceIds() {
        return audienceRepository.findAllBySimulationId(SIMULATION_ID)
                .stream().map(Audience::getId).collect(Collectors.toList());
    }

    private int countAudiencesWithAcquiredSeat(Long sid) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audience_acquired_seats WHERE seat_id = ?",
                Integer.class, sid);
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
            assertThat(pessimisticLockService.hold(seatId, id))
                    .isEqualTo(SeatHoldResult.SUCCESS);
        }

        @Test
        @DisplayName("이미 HELD 좌석 재시도 → ALREADY_HELD")
        void 이미_선점된_좌석은_ALREADY_HELD() {
            List<Long> ids = audienceIds();
            pessimisticLockService.hold(seatId, ids.get(0));

            assertThat(pessimisticLockService.hold(seatId, ids.get(1)))
                    .isEqualTo(SeatHoldResult.ALREADY_HELD);
        }

        @Test
        @DisplayName("존재하지 않는 audience → AUDIENCE_NOT_FOUND")
        void 존재하지_않는_audience는_AUDIENCE_NOT_FOUND() {
            assertThat(pessimisticLockService.hold(seatId, -1L))
                    .isEqualTo(SeatHoldResult.AUDIENCE_NOT_FOUND);
        }

        @Test
        @DisplayName("N개 동시 요청 → 정확히 1개만 SUCCESS")
        void 동시_요청_정확히_1개_SUCCESS() throws InterruptedException {
            List<SeatHoldResult> results =
                    runConcurrently(audienceIds(), id -> pessimisticLockService.hold(seatId, id));

            assertThat(results).hasSize(THREAD_COUNT);
            assertThat(results.stream().filter(r -> r == SeatHoldResult.SUCCESS).count())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("N개 동시 요청 → 나머지는 모두 ALREADY_HELD")
        void 동시_요청_나머지는_ALREADY_HELD() throws InterruptedException {
            List<SeatHoldResult> results =
                    runConcurrently(audienceIds(), id -> pessimisticLockService.hold(seatId, id));

            long notSuccess  = results.stream().filter(r -> r != SeatHoldResult.SUCCESS).count();
            long alreadyHeld = results.stream().filter(r -> r == SeatHoldResult.ALREADY_HELD).count();

            assertThat(alreadyHeld).isEqualTo(notSuccess);
        }

        @Test
        @DisplayName("N개 동시 요청 후 DB 상태 검증 → seat HELD + acquiredSeat 1건")
        void 동시_요청_후_DB_상태_검증() throws InterruptedException {
            runConcurrently(audienceIds(), id -> pessimisticLockService.hold(seatId, id));

            Seat seat = seatRepository.findById(seatId).orElseThrow();
            assertThat(seat.getSeatStatus()).isEqualTo(SeatStatus.HELD);
            assertThat(seat.getHolderId()).isNotNull();
            assertThat(countAudiencesWithAcquiredSeat(seatId)).isEqualTo(1);
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
            assertThat(optimisticLockService.hold(seatId, id))
                    .isEqualTo(SeatHoldResult.SUCCESS);
        }

        @Test
        @DisplayName("이미 HELD 좌석 재시도 → ALREADY_HELD")
        void 이미_선점된_좌석은_ALREADY_HELD() {
            List<Long> ids = audienceIds();
            optimisticLockService.hold(seatId, ids.get(0));

            assertThat(optimisticLockService.hold(seatId, ids.get(1)))
                    .isEqualTo(SeatHoldResult.ALREADY_HELD);
        }

        @Test
        @DisplayName("존재하지 않는 audience → AUDIENCE_NOT_FOUND")
        void 존재하지_않는_audience는_AUDIENCE_NOT_FOUND() {
            assertThat(optimisticLockService.hold(seatId, -1L))
                    .isEqualTo(SeatHoldResult.AUDIENCE_NOT_FOUND);
        }

        @Test
        @DisplayName("N개 동시 요청 → 정확히 1개만 SUCCESS")
        void 동시_요청_정확히_1개_SUCCESS() throws InterruptedException {
            List<SeatHoldResult> results =
                    runConcurrently(audienceIds(), id -> optimisticLockService.hold(seatId, id));

            assertThat(results).hasSize(THREAD_COUNT);
            assertThat(results.stream().filter(r -> r == SeatHoldResult.SUCCESS).count())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("N개 동시 요청 → 비성공 결과는 ALREADY_HELD 또는 LOCK_CONFLICT만 존재")
        void 동시_요청_비성공_결과는_ALREADY_HELD_또는_LOCK_CONFLICT() throws InterruptedException {
            List<SeatHoldResult> results =
                    runConcurrently(audienceIds(), id -> optimisticLockService.hold(seatId, id));

            results.stream()
                    .filter(r -> r != SeatHoldResult.SUCCESS)
                    .forEach(r -> assertThat(r)
                            .isIn(SeatHoldResult.ALREADY_HELD, SeatHoldResult.LOCK_CONFLICT));
        }

        @Test
        @DisplayName("N개 동시 요청 후 DB 상태 검증 → seat HELD + acquiredSeat 1건")
        void 동시_요청_후_DB_상태_검증() throws InterruptedException {
            runConcurrently(audienceIds(), id -> optimisticLockService.hold(seatId, id));

            Seat seat = seatRepository.findById(seatId).orElseThrow();
            assertThat(seat.getSeatStatus()).isEqualTo(SeatStatus.HELD);
            assertThat(countAudiencesWithAcquiredSeat(seatId)).isEqualTo(1);
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
            assertThat(redisLockService.hold(seatId, id))
                    .isEqualTo(SeatHoldResult.SUCCESS);
        }

        @Test
        @DisplayName("이미 HELD 좌석 재시도 → ALREADY_HELD")
        void 이미_선점된_좌석은_ALREADY_HELD() {
            List<Long> ids = audienceIds();
            redisLockService.hold(seatId, ids.get(0));

            assertThat(redisLockService.hold(seatId, ids.get(1)))
                    .isEqualTo(SeatHoldResult.ALREADY_HELD);
        }

        @Test
        @DisplayName("존재하지 않는 audience → FAIL")
        void 존재하지_않는_audience는_FAIL() {
            assertThat(redisLockService.hold(seatId, -1L))
                    .isEqualTo(SeatHoldResult.FAIL);
        }

        @Test
        @DisplayName("N개 동시 요청 → 정확히 1개만 SUCCESS")
        void 동시_요청_정확히_1개_SUCCESS() throws InterruptedException {
            List<SeatHoldResult> results =
                    runConcurrently(audienceIds(), id -> redisLockService.hold(seatId, id));

            assertThat(results).hasSize(THREAD_COUNT);
            assertThat(results.stream().filter(r -> r == SeatHoldResult.SUCCESS).count())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("N개 동시 요청 → 나머지는 모두 ALREADY_HELD (순차 실행 보장)")
        void 동시_요청_나머지는_ALREADY_HELD() throws InterruptedException {
            List<SeatHoldResult> results =
                    runConcurrently(audienceIds(), id -> redisLockService.hold(seatId, id));

            long notSuccess  = results.stream().filter(r -> r != SeatHoldResult.SUCCESS).count();
            long alreadyHeld = results.stream().filter(r -> r == SeatHoldResult.ALREADY_HELD).count();

            assertThat(alreadyHeld).isEqualTo(notSuccess);
        }

        @Test
        @DisplayName("N개 동시 요청 후 DB 상태 검증 → seat HELD + acquiredSeat 1건")
        void 동시_요청_후_DB_상태_검증() throws InterruptedException {
            runConcurrently(audienceIds(), id -> redisLockService.hold(seatId, id));

            Seat seat = seatRepository.findById(seatId).orElseThrow();
            assertThat(seat.getSeatStatus()).isEqualTo(SeatStatus.HELD);
            assertThat(seat.getHolderId()).isNotNull();
            assertThat(countAudiencesWithAcquiredSeat(seatId)).isEqualTo(1);
        }
    }
}
