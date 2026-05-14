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
    let held = false;
    for (const seatId of audience.preferredSeatIds) {
        sleep(audience.seatClickWaitJitter / 1000);  // ms → seconds
        const raw = http.post(`${BASE_URL}/api/seats/${seatId}/hold`, JSON.stringify({
                    simulationId: SIM_ID,
                    audienceId: audience.id,
                }), { headers: headers })
        const res = raw.body
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
        if (res == "\"SUCCESS\"") {
            held = true;
        }
        if (res === '"ALREADY_HELD"') {
            duplicateHoldCounter.add(1);
        }
    }

    // 2차: 실패 시 가용 좌석에서 재시도
    const MAX_RETRY = 10;
    let retry = 0;
    while (!held && retry < MAX_RETRY) {
        sleep(Math.random()*2)

        const available = http.get(
            `${BASE_URL}/api/simulations/${SIM_ID}/seats/available`,
            { headers }
        );
        const seats = JSON.parse(available.body);
        if (seats.length === 0) break;

        const randomSeat = seats[Math.floor(Math.random() * seats.length)];
        const raw = http.post(`${BASE_URL}/api/seats/${randomSeat.id}/hold`, JSON.stringify({
            simulationId: SIM_ID,
            audienceId: audience.id,
        }), { headers });

        const res = raw.body;
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

        if (res == "\"SUCCESS\"") {
            held = true;
        }
        if (res === '"ALREADY_HELD"') {
            duplicateHoldCounter.add(1);
        }
        retry++;
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

    // infra_error check 비율로 실패 판단
    const checks = data.root_group.checks;
    const infraCheck = checks.find(c => c.name === 'infra_error');
    const hasInfraFailure = infraCheck && infraCheck.passes > 0;

    if (hasInfraFailure) {
        const infraPasses = infraCheck.passes;
        const infraFails = infraCheck.fails;
        const total = infraPasses + infraFails;
        const errorRate = total > 0 ? infraPasses / total : 0;
        http.patch(`${BASE_URL}/api/simulations/${SIM_ID}/fail`,
            JSON.stringify({
                message: `infra_error detected: ${infraPasses} errors out of ${infraPasses + infraFails} requests (${(errorRate * 100).toFixed(1)}%)`
            }), { headers });
    } else {
        http.post(`${BASE_URL}/api/simulations/${SIM_ID}/finish`,
            JSON.stringify({
                duplicateHoldCount,
                totalTps,
                avgResponseMs,
                p90ResponseMs,
                p95ResponseMs
            }), { headers });
    }
}