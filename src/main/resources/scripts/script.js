import http from 'k6/http';
import { sleep, check } from 'k6';
import { Counter } from 'k6/metrics';

export const options = {
  scenarios: {
    ticketing: {
      executor: 'per-vu-iterations',
      vus: parseInt(__ENV.TOT_VUS),
      iterations: 1,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)'],
};

const BASE_URL = __ENV.BASE_URL;
const SIM_ID = __ENV.SIM_ID;
const duplicateHoldCounter = new Counter('duplicate_holds');
const holdsTotalCounter = new Counter('holds_total');
const holdsSuccessCounter = new Counter('holds_success');
const lockConflictCounter = new Counter('lock_conflict');
const lockTimeoutCounter = new Counter('lock_timeout');

// 사용자 단위 (비즈니스 관점) — 신규 추가
const userFullSuccessCounter = new Counter('user_full_success');
const userRollbackCounter = new Counter('user_rollback');
const userTotalFailCounter = new Counter('user_total_fail');
const seatsRolledBackCounter = new Counter('seats_rolled_back');

const releaseSuccessCounter = new Counter('release_success');
const releaseFailCounter = new Counter('release_fail');  // NOT_HELD_BY_YOU + LOCK_TIMEOUT + LOCK_CONFLICT 통합

export function setup() {
    const headers = { 'Content-Type': 'application/json' };
    var raw = http.get(`${BASE_URL}/api/simulations/${SIM_ID}`, {headers: headers});
    var simulation = null;
    if (raw.status == 200) {
        simulation = JSON.parse(raw.body);
    }
    return { simulation };
}

export default function(data) {

    // 1차: 선호 좌석 시도
    const audience = data.simulation.audienceResponses[__VU - 1];
    const headers = { 'Content-Type': 'application/json' };
    const acquiredSeats = []
    for (const seatId of audience.preferredSeatIds) {
        sleep(audience.seatClickWaitJitter / 1000);  // ms → seconds
        const raw = http.post(`${BASE_URL}/api/seats/${seatId}/hold`, JSON.stringify({
                    simulationId: SIM_ID,
                    audienceId: audience.id,
                }), { headers: headers })
        const res = raw.body
        holdsTotalCounter.add(1);
        check(res, {
            'valid_response': (b) =>
                b === '"SUCCESS"' || b === '"ALREADY_HELD"',
        });

        check(res, {
            'infra_error': (b) =>
                b !== '"SUCCESS"' &&
                b !== '"ALREADY_HELD"' &&
                b !== '"LOCK_TIMEOUT"' &&
                b !== '"LOCK_CONFLICT"',
        });
        if (res === '"SUCCESS"') {
            break;
        }
        if (res === '"SUCCESS"') {
            holdsSuccessCounter.add(1);
            acquiredSeats.push(seatId);
        }
        if (res === '"ALREADY_HELD"') {
            duplicateHoldCounter.add(1);
        }
        if (res === '"LOCK_CONFLICT"') {
            lockConflictCounter.add(1);
        }
        if (res === '"LOCK_TIMEOUT"') {
            lockTimeoutCounter.add(1);
        }
    }

    // 원하는 좌석을 모두 획득하지 못했다면 전부 release한다.
    if (acquiredSeats.length === audience.preferredSeatIds.length) {
        userFullSuccessCounter.add(1);
    } else if (acquiredSeats.length > 0) {
        userRollbackCounter.add(1);
        seatsRolledBackCounter.add(acquiredSeats.length);
        for (const seatId of acquiredSeats) {
            const raw = http.post(`${BASE_URL}/api/seats/${seatId}/release`, JSON.stringify({
                        simulationId: SIM_ID,
                        audienceId: audience.id,
                    }), { headers: headers });
            const res = raw.body;
            if (res === '"SUCCESS"') {
                releaseSuccessCounter.add(1);
            } else {
                releaseFailCounter.add(1);
            }
        }
    } else {
        userTotalFailCounter.add(1)
    }
}

export function handleSummary(data) {
    const headers = { 'Content-Type': 'application/json' };
    const totalTps = Math.floor(data.metrics.http_reqs.values.rate);
    const avgResponseMs = Math.round(data.metrics.http_req_duration.values.avg);
    const p90ResponseMs = Math.round(data.metrics.http_req_duration.values['p(90)']);
    const p95ResponseMs = Math.round(data.metrics.http_req_duration.values['p(95)']);

    const duplicateHoldCount = data.metrics.duplicate_holds
        ? data.metrics.duplicate_holds.values.count
        : 0;
    const holdsTotal = data.metrics.holds_total
        ? data.metrics.holds_total.values.count
        : 0;
    const holdsSuccess = data.metrics.holds_success
        ? data.metrics.holds_success.values.count
        : 0;
    const lockConflict = data.metrics.lock_conflict
        ? data.metrics.lock_conflict.values.count
        : 0;
    const lockTimeout = data.metrics.lock_timeout
        ? data.metrics.lock_timeout.values.count
        : 0;

    const userFullSuccess = data.metrics.user_full_success?.values.count ?? 0;
    const userRollback = data.metrics.user_rollback?.values.count ?? 0;
    const userTotalFail = data.metrics.user_total_fail?.values.count ?? 0;
    const seatsRolledBack = data.metrics.seats_rolled_back?.values.count ?? 0;
    const releaseSuccess = data.metrics.release_success?.values.count ?? 0;
    const releaseFail = data.metrics.release_fail?.values.count ?? 0;

    const checks = data.root_group.checks;
    const infraCheck = checks.find(c => c.name === 'infra_error');
    const hasInfraFailure = infraCheck && infraCheck.passes > 0;

    if (hasInfraFailure) {
        const infraPasses = infraCheck.passes;
        const infraFails = infraCheck.fails;
        const total = infraPasses + infraFails;
        const errorRate = total > 0 ? infraPasses / total : 0;
        http.post(`${BASE_URL}/api/simulations/${SIM_ID}/fail`,
            JSON.stringify({
                message: `infra_error detected: ${infraPasses} errors out of ${infraPasses + infraFails} requests (${(errorRate * 100).toFixed(1)}%)`
            }), { headers });
    } else {
        http.post(`${BASE_URL}/api/simulations/${SIM_ID}/finish`,
            JSON.stringify({
                duplicateHoldCount,
                holdsTotal,
                holdsSuccess,
                lockConflict,
                lockTimeout,
                totalTps,
                avgResponseMs,
                p90ResponseMs,
                p95ResponseMs,
                userFullSuccess,
                userRollback,
                userTotalFail,
                seatsRolledBack,
                releaseSuccess,
                releaseFail
            }), { headers });
    }
}