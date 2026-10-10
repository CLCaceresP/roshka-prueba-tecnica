// Prueba de carga de sobreventa (REQ-13, REQ-31): muchos usuarios virtuales piden el mismo producto a través del
// gateway a la vez. La ejecución falla si, una vez asentadas las sagas, hay más unidades reservadas que las existentes.
//
//   k6 run load/oversell.js                       # contra http://localhost:8080
//   k6 run -e BASE_URL=http://host:8080 -e UNITS=50 -e ORDERS=300 load/oversell.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const UNITS = Number(__ENV.UNITS || 50);
const ORDERS = Number(__ENV.ORDERS || 300);
const SETTLE_SECONDS = Number(__ENV.SETTLE_SECONDS || 60);

const oversold = new Counter('oversold_units');
const acceptedOrders = new Counter('accepted_orders');

export const options = {
  scenarios: {
    competing_orders: {
      executor: 'shared-iterations',
      vus: 50,
      iterations: ORDERS,
      maxDuration: '2m',
    },
  },
  thresholds: {
    oversold_units: ['count==0'],
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
  },
};

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

function post(path, body) {
  return http.post(`${BASE_URL}${path}`, JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': uuid() },
  });
}

export function setup() {
  const response = post('/products', { name: `k6-${Date.now()}`, initialQuantity: UNITS });
  check(response, { 'product created': (r) => r.status === 201 });
  return { productId: response.json('productId') };
}

export default function (data) {
  const response = post('/orders', { lines: [{ productId: data.productId, quantity: 1 }] });
  const accepted = check(response, { 'order accepted (202)': (r) => r.status === 202 });
  if (accepted) {
    acceptedOrders.add(1);
  }
}

export function teardown(data) {
  const expectedReserved = Math.min(UNITS, ORDERS);
  let stock;
  for (let second = 0; second < SETTLE_SECONDS; second++) {
    stock = http.get(`${BASE_URL}/products/${data.productId}/stock`).json();
    if (stock.reserved === expectedReserved) {
      break;
    }
    sleep(1);
  }
  const excess = Math.max(0, stock.reserved - stock.total) + Math.max(0, -stock.available);
  oversold.add(excess);
  check(stock, {
    'stock is conserved': (s) => s.available + s.reserved === s.total,
    'every unit was sold, none twice': (s) => s.reserved === expectedReserved,
  });
  console.log(`final stock: ${JSON.stringify(stock)}`);
}
