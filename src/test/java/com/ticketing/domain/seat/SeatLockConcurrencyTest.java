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
import org.springframework.test.context.ActiveProfiles;

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

import static com.ticketing.domain.seat.SeatHoldResult.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세 가지 락 전략의 동시성 보장 여부를 검증하는 통합 테스트.
 * Docker Compose로 MySQL(3306), Redis(6379)가 실행 중이어야 합니다.
 *
 * <p>핵심 불변식: 같은 좌석에 N개 스레드가 동시에 hold() 요청 시
 * <b>정확히 1개만 SUCCESS</b>, 나머지는 전략별 허용 결과 집합에 속해야 한다.
 *
 * <ul>
 *   <li>비관적 락: 나머지 → ALREADY_HELD | LOCK_TIMEOUT</li>
 *   <li>낙관적 락: 나머지 → ALREADY_HELD | LOCK_CONFLICT</li>
 *   <li>Redis 분산 락: 나머지 → ALREADY_HELD | LOCK_TIMEOUT</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("좌석 선점 락 전략 동시성 테스트")
class SeatLockConcurrencyTest {

    private static final long SIMULATION_ID = 9001L;
    private static final int  THREAD_COUNT  = 10;

    private Long seatId;

    @Autowired private PessimisticSeatLockService pessimisticLockService;
    @Autowired private OptimisticSeatLockService  optimisticLockService;
    @Autowired private RedisSeatLockService        redisLockService;
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
                    results.add(FAIL);
                } catch (Exception e) {
                    results.add(FAIL);
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
    //
    // hold() 반환 가능 결과:
    //   SUCCESS          - 정상 선점
    //   ALREADY_HELD     - 락 획득 후 이미 HELD 확인
    //   LOCK_TIMEOUT     - PessimisticLockingFailureException (DB 락 대기 초과)
    //   SEAT_NOT_FOUND   - 좌석 없음
    //   AUDIENCE_NOT_FOUND - audience 없음
    //   FAIL             - 기타 예외
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("비관적 락 (PessimisticSeatLockService)")
    class PessimisticLockTest {

        @Test
        @DisplayName("단일 요청 → SUCCESS")
        void 단일_요청은_SUCCESS() {
            Long id = audienceIds().get(0);
            assertThat(pessimisticLockService.hold(seatId, id))
                    .isEqualTo(SUCCESS);
        }

        @Test
        @DisplayName("이미 HELD 좌석 재요청 → ALREADY_HELD")
        void 이미_선점된_좌석은_ALREADY_HELD() {
            List<Long> ids = audienceIds();
            pessimisticLockService.hold(seatId, ids.get(0));

            assertThat(pessimisticLockService.hold(seatId, ids.get(1)))
                    .isEqualTo(ALREADY_HELD);
        }

        @Test
        @DisplayName("존재하지 않는 audience → AUDIENCE_NOT_FOUND")
        void 존재하지_않는_audience는_AUDIENCE_NOT_FOUND() {
            assertThat(pessimisticLockService.hold(seatId, -1L))
                    .isEqualTo(AUDIENCE_NOT_FOUND);
        }

        /**
         * 핵심 불변식 검증:
         * N개 동시 요청 → 정확히 1개 SUCCESS, 나머지는 ALREADY_HELD 또는 LOCK_TIMEOUT.
         *
         * LOCK_TIMEOUT: DB 비관적 락 대기가 innodb_lock_wait_timeout을 초과할 때 발생.
         */
        @Test
        @DisplayName("N개 동시 요청 → 정확히 1개 SUCCESS, 나머지는 ALREADY_HELD | LOCK_TIMEOUT")
        void 동시_요청_정확히_1개_SUCCESS_나머지는_ALREADY_HELD_또는_LOCK_TIMEOUT() throws InterruptedException {
            List<SeatHoldResult> results =
                    runConcurrently(audienceIds(), id -> pessimisticLockService.hold(seatId, id));

            assertThat(results).hasSize(THREAD_COUNT);

            long successCount = results.stream().filter(r -> r == SUCCESS).count();
            assertThat(successCount)
                    .as("정확히 1개의 스레드만 SUCCESS여야 한다")
                    .isEqualTo(1);

            results.stream()
                    .filter(r -> r != SUCCESS)
                    .forEach(r -> assertThat(r)
                            .as("비성공 결과는 ALREADY_HELD 또는 LOCK_TIMEOUT이어야 한다")
                            .isIn(ALREADY_HELD, LOCK_TIMEOUT));
        }

        @Test
        @DisplayName("N개 동시 요청 후 DB 상태 → seat HELD + acquiredSeat 1건")
        void 동시_요청_후_DB_상태_검증() throws InterruptedException {
            runConcurrently(audienceIds(), id -> pessimisticLockService.hold(seatId, id));

            Seat seat = seatRepository.findById(seatId).orElseThrow();
            assertThat(seat.getSeatStatus()).isEqualTo(SeatStatus.HELD);
            assertThat(seat.getHolderId()).isNotNull();
            assertThat(countAudiencesWithAcquiredSeat(seatId))
                    .as("acquiredSeat 레코드는 정확히 1건이어야 한다")
                    .isEqualTo(1);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 2. 낙관적 락
    //
    // hold() 반환 가능 결과:
    //   SUCCESS          - 정상 선점 (버전 충돌 없음)
    //   ALREADY_HELD     - 커밋 전 isAvailable() 확인 실패
    //   LOCK_CONFLICT    - ObjectOptimisticLockingFailureException (버전 충돌)
    //   SEAT_NOT_FOUND   - 좌석 없음
    //   AUDIENCE_NOT_FOUND - audience 없음
    //   FAIL             - 기타 예외
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("낙관적 락 (OptimisticSeatLockService)")
    class OptimisticLockTest {

        @Test
        @DisplayName("단일 요청 → SUCCESS")
        void 단일_요청은_SUCCESS() {
            Long id = audienceIds().get(0);
            assertThat(optimisticLockService.hold(seatId, id))
                    .isEqualTo(SUCCESS);
        }

        @Test
        @DisplayName("이미 HELD 좌석 재요청 → ALREADY_HELD")
        void 이미_선점된_좌석은_ALREADY_HELD() {
            List<Long> ids = audienceIds();
            optimisticLockService.hold(seatId, ids.get(0));

            assertThat(optimisticLockService.hold(seatId, ids.get(1)))
                    .isEqualTo(ALREADY_HELD);
        }

        @Test
        @DisplayName("존재하지 않는 audience → AUDIENCE_NOT_FOUND")
        void 존재하지_않는_audience는_AUDIENCE_NOT_FOUND() {
            assertThat(optimisticLockService.hold(seatId, -1L))
                    .isEqualTo(AUDIENCE_NOT_FOUND);
        }

        /**
         * 핵심 불변식 검증:
         * N개 동시 요청 → 정확히 1개 SUCCESS, 나머지는 ALREADY_HELD 또는 LOCK_CONFLICT.
         *
         * LOCK_CONFLICT: 동시에 같은 @Version으로 saveAndFlush 시 ObjectOptimisticLockingFailureException 발생.
         * ALREADY_HELD:  선점 완료 후 들어온 요청이 isAvailable() 검사에서 탈락.
         */
        @Test
        @DisplayName("N개 동시 요청 → 정확히 1개 SUCCESS, 나머지는 ALREADY_HELD | LOCK_CONFLICT")
        void 동시_요청_정확히_1개_SUCCESS_나머지는_ALREADY_HELD_또는_LOCK_CONFLICT() throws InterruptedException {
            List<SeatHoldResult> results =
                    runConcurrently(audienceIds(), id -> optimisticLockService.hold(seatId, id));

            assertThat(results).hasSize(THREAD_COUNT);

            long successCount = results.stream().filter(r -> r == SUCCESS).count();
            assertThat(successCount)
                    .as("정확히 1개의 스레드만 SUCCESS여야 한다")
                    .isEqualTo(1);

            results.stream()
                    .filter(r -> r != SUCCESS)
                    .forEach(r -> assertThat(r)
                            .as("비성공 결과는 ALREADY_HELD 또는 LOCK_CONFLICT이어야 한다")
                            .isIn(ALREADY_HELD, LOCK_CONFLICT));
        }

        @Test
        @DisplayName("N개 동시 요청 후 DB 상태 → seat HELD + acquiredSeat 1건")
        void 동시_요청_후_DB_상태_검증() throws InterruptedException {
            runConcurrently(audienceIds(), id -> optimisticLockService.hold(seatId, id));

            Seat seat = seatRepository.findById(seatId).orElseThrow();
            assertThat(seat.getSeatStatus()).isEqualTo(SeatStatus.HELD);
            assertThat(countAudiencesWithAcquiredSeat(seatId))
                    .as("acquiredSeat 레코드는 정확히 1건이어야 한다")
                    .isEqualTo(1);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 3. Redis 분산 락
    //
    // hold() 반환 가능 결과:
    //   SUCCESS          - 정상 선점
    //   ALREADY_HELD     - 락 획득 후 isAvailable() 확인 실패
    //   LOCK_TIMEOUT     - tryLock(5s) 초과로 락 미획득
    //   FAIL             - audience/seat 없음, 기타 예외
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Redis 분산 락 (RedisSeatLockService)")
    class RedisLockTest {

        @Test
        @DisplayName("단일 요청 → SUCCESS")
        void 단일_요청은_SUCCESS() {
            Long id = audienceIds().get(0);
            assertThat(redisLockService.hold(seatId, id))
                    .isEqualTo(SUCCESS);
        }

        @Test
        @DisplayName("이미 HELD 좌석 재요청 → ALREADY_HELD")
        void 이미_선점된_좌석은_ALREADY_HELD() {
            List<Long> ids = audienceIds();
            redisLockService.hold(seatId, ids.get(0));

            assertThat(redisLockService.hold(seatId, ids.get(1)))
                    .isEqualTo(ALREADY_HELD);
        }

        @Test
        @DisplayName("존재하지 않는 audience → FAIL")
        void 존재하지_않는_audience는_FAIL() {
            assertThat(redisLockService.hold(seatId, -1L))
                    .isEqualTo(FAIL);
        }

        /**
         * 핵심 불변식 검증:
         * N개 동시 요청 → 정확히 1개 SUCCESS, 나머지는 ALREADY_HELD 또는 LOCK_TIMEOUT.
         *
         * Redis 분산 락은 순차 실행을 보장하므로 대부분 ALREADY_HELD 반환.
         * LOCK_TIMEOUT: tryLock(5s) 대기 내 락 획득 실패 시 반환 (부하 환경에서 가능).
         */
        @Test
        @DisplayName("N개 동시 요청 → 정확히 1개 SUCCESS, 나머지는 ALREADY_HELD | LOCK_TIMEOUT")
        void 동시_요청_정확히_1개_SUCCESS_나머지는_ALREADY_HELD_또는_LOCK_TIMEOUT() throws InterruptedException {
            List<SeatHoldResult> results =
                    runConcurrently(audienceIds(), id -> redisLockService.hold(seatId, id));

            assertThat(results).hasSize(THREAD_COUNT);

            long successCount = results.stream().filter(r -> r == SUCCESS).count();
            assertThat(successCount)
                    .as("정확히 1개의 스레드만 SUCCESS여야 한다")
                    .isEqualTo(1);

            results.stream()
                    .filter(r -> r != SUCCESS)
                    .forEach(r -> assertThat(r)
                            .as("비성공 결과는 ALREADY_HELD 또는 LOCK_TIMEOUT이어야 한다")
                            .isIn(ALREADY_HELD, LOCK_TIMEOUT));
        }

        @Test
        @DisplayName("N개 동시 요청 후 DB 상태 → seat HELD + acquiredSeat 1건")
        void 동시_요청_후_DB_상태_검증() throws InterruptedException {
            runConcurrently(audienceIds(), id -> redisLockService.hold(seatId, id));

            Seat seat = seatRepository.findById(seatId).orElseThrow();
            assertThat(seat.getSeatStatus()).isEqualTo(SeatStatus.HELD);
            assertThat(seat.getHolderId()).isNotNull();
            assertThat(countAudiencesWithAcquiredSeat(seatId))
                    .as("acquiredSeat 레코드는 정확히 1건이어야 한다")
                    .isEqualTo(1);
        }
    }
}