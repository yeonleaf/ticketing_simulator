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
    const audience = data.simulation.audienceResponses[__VU - 1];
    const headers = { 'Content-Type': 'application/json' };
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