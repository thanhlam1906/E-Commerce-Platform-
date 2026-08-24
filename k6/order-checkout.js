// Load test: create order (checkout) flow — Phase 2 exit criteria:
//   ~100 req/s, p95 latency < 500ms, 0 business errors, no oversell.
//
// Flow per iteration (default): add 1 SKU to cart -> checkout -> assert.
//
// Auth: users are REGISTERED in setup() — register returns accessToken directly,
// so we never touch the gateway login route's strict rate limit (1 req/s, burst 5).
// Each VU is pinned to one registered user (tokens[(__VU-1) % n]) -> per-user cart
// and no per-user checkout-lock contention.
//
// Gateway note: the `order` route (orders/cart/inventory) is rate-limited to
// 100 req/s per IP (burst 200). Because all VUs come from localhost, cart-add +
// checkout share that budget. Sustained full-flow iterations therefore cap real
// checkout throughput at ~50/s before the gateway emits 429s — see k6/README.md
// for how to read that vs the 100 RPS exit criterion.
//
// Run (see k6/README.md for prerequisites):
//   k6 run order-checkout.js -e ADMIN_TOKEN=<token> -e TARGET_RPS=100 -e STOCK_QTY=5000
//
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TARGET_RPS = Math.max(1, parseInt(__ENV.TARGET_RPS || '100', 10));
const RAMP_SEC = Math.max(1, parseInt(__ENV.RAMP || '60', 10));
const HOLD_SEC = Math.max(1, parseInt(__ENV.HOLD || '120', 10));
const USERS = Math.max(1, parseInt(__ENV.USERS || '100', 10));
const STOCK_QTY = Math.max(1, parseInt(__ENV.STOCK_QTY || '5000', 10));
const SKU = __ENV.SKU || 'SKU-001';
// COD = no external VNPay dependency, exercises the full reserve+order+outbox path.
// VNPAY_QR works too but calls VNPay sandbox (adds external latency/failure modes).
const PAYMENT_METHOD = __ENV.PAYMENT_METHOD || 'COD';

// --- outcome counters (these are the pass/fail signals, not the `check`s) ---
const checkoutOk = new Counter('checkout_ok');              // successful checkout (2xx + business code 200)
const checkoutStockOut = new Counter('checkout_stockout');  // controlled 409 "Sản phẩm không đủ tồn kho" — expected once stock is consumed
const checkoutRateLimited = new Counter('checkout_ratelimited'); // gateway 429 (rate limit)
const checkout5xx = new Counter('checkout_5xx');            // server error — MUST stay 0
const checkoutBizErr = new Counter('checkout_biz_err');     // unexpected business error — MUST stay 0
const cartAddFail = new Counter('cart_add_fail');

export const options = {
  setupTimeout: '3m',
  scenarios: {
    checkout: {
      executor: 'ramping-arrival-rate',
      timeUnit: '1s',
      startRate: Math.min(10, TARGET_RPS),
      stages: [
        { target: TARGET_RPS, duration: RAMP_SEC + 's' },
        { target: TARGET_RPS, duration: HOLD_SEC + 's' },
        { target: 0, duration: '30s' },
      ],
      preAllocatedVUs: Math.min(50, USERS),
      maxVUs: USERS,
    },
  },
  thresholds: {
    // --- exit criteria ---
    // p95 latency of SUCCESSFUL checkouts < 500ms (scoped via the checkout_status tag)
    'http_req_duration{name:checkout,checkout_status:ok}': ['p(95)<500'],
    'checkout_5xx': ['count==0'],            // no server crash
    'checkout_biz_err': ['count==0'],        // no unexpected business error
    // no oversell: every ok checkout reserves exactly 1 unit, so the number of ok
    // checkouts must never exceed the seeded stock. (Assumes COD / healthy payment:
    // if payment fails and releases stock, this threshold would false-alarm.)
    'checkout_ok': ['count<=' + STOCK_QTY],
  },
};

// Register `USERS` accounts and return their tokens. Optionally seed stock via the
// inventory import endpoint when an admin token is supplied.
export function setup() {
  const adminToken = __ENV.ADMIN_TOKEN || '';
  const tokens = [];

  for (let i = 0; i < USERS; i++) {
    const email = 'k6u' + Math.random().toString(36).slice(2, 10) + '-' + i + '@' + (__ENV.EMAIL_DOMAIN || 'k6load.local');
    const res = http.post(
      BASE_URL + '/api/v1/auth/register',
      JSON.stringify({ email, password: 'Password123!', fullName: 'K6 Load ' + i, phone: '0900000000' }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup-register' } }
    );
    if (res.status === 200 || res.status === 201) {
      const body = res.json();
      const t = body && body.data && body.data.accessToken;
      if (t) tokens.push(t);
      else console.log('register ' + i + ': ' + res.status + ' but no accessToken: ' + res.body.slice(0, 200));
    } else {
      console.log('register ' + i + ': status=' + res.status + ' ' + res.body.slice(0, 200));
    }
  }
  if (tokens.length === 0) {
    throw new Error('no usable token after registering ' + USERS + ' users — is identity-service + gateway up?');
  }
  if (tokens.length < USERS) {
    console.log('WARN: got ' + tokens.length + '/' + USERS + ' tokens; VUs above that share a user (lock contention).');
  }

  // Seed stock. inventory/import only ADDS quantity — use a fresh SKU per run so
  // the no-oversell threshold (<= STOCK_QTY) stays valid.
  if (adminToken) {
    const r = http.post(
      BASE_URL + '/api/v1/inventory/import',
      JSON.stringify({ sku: SKU, quantity: STOCK_QTY, reference: 'k6-load-seed' }),
      { headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + adminToken }, tags: { name: 'setup-import' } }
    );
    console.log('stock import (sku=' + SKU + ', +' + STOCK_QTY + ') status=' + r.status + ' body=' + r.body.slice(0, 160));
  } else {
    console.log('ADMIN_TOKEN not set — assume SKU=' + SKU + ' already has >= ' + STOCK_QTY + ' available stock.');
  }

  return { tokens, sku: SKU, baseUrl: BASE_URL };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const headers = { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' };

  // 1. add 1 item to the user's cart
  const cartRes = http.post(
    data.baseUrl + '/api/v1/cart/items',
    JSON.stringify({ sku: data.sku, quantity: 1 }),
    { headers, tags: { name: 'cart-add' } }
  );
  if (cartRes.status !== 200) cartAddFail.add(1);
  check(cartRes, { 'cart add item 2xx': (r) => r.status === 200 });

  // 2. checkout / create order — fresh Idempotency-Key per iteration (8-64 chars [A-Za-z0-9_-])
  const idemKey = 'k6-' + __VU + '-' + __ITER + '-' + Math.random().toString(36).slice(2, 10);
  const payload = JSON.stringify({
    shippingAddress: '123 Le Loi, Q1, TP.HCM',
    paymentMethod: PAYMENT_METHOD,
    email: 'loadtest@k6.local',
  });
  const res = http.post(data.baseUrl + '/api/v1/orders', payload, {
    headers: Object.assign({}, headers, { 'Idempotency-Key': idemKey }),
    tags: { name: 'checkout' },
  });

  // classify the outcome and tag the response so thresholds can scope to real checkouts
  let outcome;
  if (res.status === 200 && bodyCode(res) === 200) {
    outcome = 'ok';
    checkoutOk.add(1);
  } else if (res.status === 429) {
    outcome = 'ratelimited';
    checkoutRateLimited.add(1);
  } else if (res.status >= 500) {
    outcome = '5xx';
    checkout5xx.add(1);
  } else if (res.status === 409 && /tồn kho|insufficient stock|not enough stock/i.test(res.body || '')) {
    outcome = 'stockout'; // expected once stock is consumed — controlled business rejection, NOT an error
    checkoutStockOut.add(1);
  } else {
    outcome = 'bizerr';
    checkoutBizErr.add(1);
  }
  res.tags.checkout_status = outcome;

  check(res, {
    'checkout ok (2xx + business code 200)': () => outcome === 'ok',
  });
}

function bodyCode(res) {
  try { return res.json().code; } catch (e) { return -1; }
}
