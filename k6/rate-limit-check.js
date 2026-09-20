import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CAMPAIGN_CODE = __ENV.CAMPAIGN_CODE || 'FLASH50';
const USER_ID = 'rate-limit-check-user';

export const options = {
    vus: 1,
    iterations: 12,
};

export default function () {
    const res = http.post(`${BASE_URL}/api/v1/nolock/campaigns/${CAMPAIGN_CODE}/claim?userId=${USER_ID}`);
    console.log(`attempt status=${res.status} body=${res.body}`);
    check(res, {
        'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
    });
    sleep(0.2);
}
