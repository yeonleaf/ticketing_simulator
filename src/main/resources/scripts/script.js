import http from 'k6/http';
import { sleep, check } from 'k6';

export const options = {
  scenarios: {
    ticketing: {
      executor: 'per-vu-iterations',
      vus: parseInt(__ENV.TOT_VUS),
      iterations: 1,
    },
  },
};

const BASE_URL = __ENV.BASE_URL;
const SIM_ID = __ENV.SIM_ID;

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
    held = true;
    for (const seatId of audience.preferredSeatIds) {
        sleep(audience.seatClickWaitJitter / 1000);  // ms → seconds
        const raw = http.post(`${BASE_URL}/api/seats/${seatId}/hold`, JSON.stringify({
                    simulationId: SIM_ID,
                    audienceId: audience.id,
                }), { headers: headers })
        const res = raw.body
        check(res, {
            'hold': (r) => r == "\"SUCCESS\""
        });
        check(res, {
            'crash': (r) => r == "\"ALREADY_HELD\""
        })
        if (res == "\"SUCCESS\"") {
            held = true;
        }
    }

    // 2차: 실패 시 가용 좌석에서 재시도
    const MAX_RETRY = 3;
    while (!held && retry < MAX_RETRY) {
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
            'hold': (r) => r == "\"SUCCESS\""
        });
        check(res, {
            'crash': (r) => r == "\"ALREADY_HELD\""
        });

        if (res == "\"SUCCESS\"") {
            held = true;
        }
        retry++;
    }
}

export function handleSummary(data) {
    const headers = { 'Content-Type': 'application/json' };
    const totalTps = Math.floor(data.metrics.http_reqs.values.rate); // http_reqs의 초당 처리량
    const avgResponseMs = Math.round(data.metrics.http_req_duration.values.avg); // 평균 응답 시간

    const checks = data.root_group.checks;
    const crashCheck = checks.find(c => c.name === 'crash');
    const duplicateHoldCount = crashCheck ? crashCheck.passes : 0;

    const raw = http.post(`${BASE_URL}/api/simulations/${SIM_ID}/finish`, JSON.stringify({
            duplicateHoldCount: duplicateHoldCount,
            totalTps: totalTps,
            avgResponseMs: avgResponseMs
        }), {headers : headers});
    console.log(raw.body)
}