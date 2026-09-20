import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CAMPAIGN_CODE = __ENV.CAMPAIGN_CODE || 'FLASH50';

const claimSuccess = new Counter('claim_success');
const claimSoldOut = new Counter('claim_sold_out');
const claimAlreadyClaimed = new Counter('claim_already_claimed');
const claimOther = new Counter('claim_other');

export const options = {
    scenarios: {
        flash_claim: {
            executor: 'shared-iterations',
            vus: 300,
            iterations: 300,
            maxDuration: '30s',
        },
    },
};

export default function () {
    const userId = `k6-user-${__VU}-${__ITER}`;
    const res = http.post(`${BASE_URL}/api/v1/nolock/campaigns/${CAMPAIGN_CODE}/claim?userId=${userId}`);

    check(res, {
        'status 200': (r) => r.status === 200,
    });

    if (res.status === 200) {
        const body = res.json();
        if (body.status === 'SUCCESS') {
            claimSuccess.add(1);
        } else if (body.status === 'SOLD_OUT') {
            claimSoldOut.add(1);
        } else if (body.status === 'ALREADY_CLAIMED') {
            claimAlreadyClaimed.add(1);
        } else {
            claimOther.add(1);
        }
    }
}
