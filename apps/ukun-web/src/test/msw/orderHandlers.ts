import { http, HttpResponse } from 'msw';

import type { components } from '../../types/api';
import { customerProfile } from './profileHandlers';
import { problem } from './problem';

/**
 * Test-only stand-in for the checkout and orders endpoints of `ukun-api` (RF-04, ADR-0016), typed
 * from the same generated OpenAPI types the app uses, so a contract change breaks compilation here
 * too. It is not a dev backend: the browser always talks to the real API (`docs/13-testing.md` §2).
 *
 * Like the cart's, this one **keeps state**, and for a sharper reason: the whole point of the screen
 * is that confirming twice with the same key places one order. A stateless stub would answer `201`
 * every time and let the duplicate-order test pass while proving nothing.
 */

type CheckoutAttempt = components['schemas']['CheckoutAttempt'];
type CheckoutAttemptLine = components['schemas']['CheckoutAttemptLine'];
type Order = components['schemas']['OrderResponse'];
type OrderLine = components['schemas']['OrderLine'];
type OrderPage = components['schemas']['OrderPage'];
type Money = components['schemas']['Money'];
type ErrorCode = components['schemas']['ErrorCode'];

const base = import.meta.env.VITE_API_URL || '';

/** Money is arithmetic on cents, never on a `double` — the same rule the app follows on the way out. */
function toCents(amount: Money): number {
  const [units, decimals = ''] = amount.split('.');

  return Number(units) * 100 + Number(decimals.padEnd(2, '0').slice(0, 2));
}

function toMoney(cents: number): Money {
  return `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, '0')}`;
}

export const ATTEMPT_ID = '4f1b0d5e-9c2a-4f3b-8f1a-1c2d3e4f5a6b';

/** Comfortably in stock. */
export const sofaLine: CheckoutAttemptLine = {
  sku: 'SOFA-NORDIC-3P',
  productName: 'Sofá Nordic 3 plazas',
  unitPrice: '399.99',
  quantity: 2,
  available: 7,
};

/** Asking for more units than the online store has: this is the one that triggers the warning. */
export const lampLine: CheckoutAttemptLine = {
  sku: 'LAMP-OSLO',
  productName: 'Lámpara Oslo',
  unitPrice: '49.50',
  quantity: 5,
  available: 3,
};

function totalOf(lines: readonly CheckoutAttemptLine[]): Money {
  return toMoney(lines.reduce((cents, line) => cents + toCents(line.unitPrice) * line.quantity, 0));
}

/** An attempt as the server issues it: prices frozen, total computed, availability informational. */
export function attemptOf(
  lines: readonly CheckoutAttemptLine[],
  { attemptId = ATTEMPT_ID, expiresAt = '2026-08-11T12:30:00Z' } = {},
): CheckoutAttempt {
  return { attemptId, lines: [...lines], total: totalOf(lines), expiresAt };
}

function orderLineOf(line: CheckoutAttemptLine, productId: number): OrderLine {
  return {
    productId,
    productName: line.productName,
    unitPrice: line.unitPrice,
    quantity: line.quantity,
    totalPrice: toMoney(toCents(line.unitPrice) * line.quantity),
  };
}

/**
 * The order the attempt turns into, with the profile copied in rather than referenced: shipping and
 * payment are **snapshots** taken at confirmation time (ADR-0011), which is what the screen says
 * about them.
 */
export function orderOf(
  lines: readonly CheckoutAttemptLine[],
  { id = 1001, placedAt = '2026-08-11T12:05:00Z' } = {},
): Order {
  return {
    id,
    placedAt,
    status: 'CONFIRMED',
    total: totalOf(lines),
    shippingAddress: { ...customerProfile.address },
    payment: { ...customerProfile.card },
    lines: lines.map((line, index) => orderLineOf(line, index + 1)),
  };
}

function pageOf(orders: readonly Order[], page = 0, size = 10): OrderPage {
  return {
    page,
    size,
    totalElements: orders.length,
    totalPages: Math.max(1, Math.ceil(orders.length / size)),
    items: orders.slice(page * size, page * size + size),
  };
}

/** The default: a history with nothing in it, and **no writes**. A test that checks out installs them. */
export const orderHandlers = [
  http.get(`${base}/api/orders`, ({ request }) => {
    const page = Number(new URL(request.url).searchParams.get('page') ?? 0);

    return HttpResponse.json(pageOf([], page));
  }),
];

/** A history with orders in it, paged the way the API pages it. */
export function orderHistory(orders: readonly Order[]) {
  return [
    http.get(`${base}/api/orders`, ({ request }) => {
      const params = new URL(request.url).searchParams;
      const page = Number(params.get('page') ?? 0);
      const size = Number(params.get('size') ?? 10);
      const from = params.get('from');

      // Enough date filtering to prove the screen sends the range and paints what comes back.
      const filtered = from === null ? orders : orders.filter((order) => order.placedAt >= from);

      return HttpResponse.json(pageOf(filtered, page, size));
    }),
    http.get(`${base}/api/orders/:id`, ({ params }) => {
      const order = orders.find((candidate) => candidate.id === Number(params.id));

      // Someone else's order is a `404` and never a `403`: an existence oracle over other people's
      // orders would leak how many the shop has taken (`docs/08-identidad-y-seguridad.md` §4).
      return order === undefined
        ? problem('RESOURCE_NOT_FOUND', 404, { resource: 'Order', id: String(params.id) })
        : HttpResponse.json(order);
    }),
  ];
}

/**
 * Checkout as the server implements it (ADR-0016): the attempt is opened here, and `POST /api/orders`
 * is keyed on the `Idempotency-Key` header.
 *
 * The idempotency table is a `Map` in this closure, and it answers exactly what the contract's table
 * says: `201` the first time, `200` with the **same** body on any retry, `400` if the header is
 * missing, `410` if the key is not the attempt's. `counters.orders` is what a test counts to prove
 * that two clicks placed one order.
 */
export function checkoutFlow(lines: readonly CheckoutAttemptLine[] = [sofaLine], options: { id?: number } = {}) {
  const attempt = attemptOf(lines);
  const order = orderOf(lines, { id: options.id ?? 1001 });
  const completed = new Map<string, Order>();
  const counters = { attempts: 0, orders: 0 };
  /** Every `Idempotency-Key` this saw, in order: what proves the browser sent back the server's. */
  const seenKeys: string[] = [];

  const handlers = [
    http.post(`${base}/api/checkout/attempts`, () => {
      counters.attempts += 1;

      return HttpResponse.json(attempt, { status: 201 });
    }),

    http.post(`${base}/api/orders`, ({ request }) => {
      const key = request.headers.get('Idempotency-Key');
      if (key !== null) {
        seenKeys.push(key);
      }

      if (key === null) {
        return problem('MALFORMED_REQUEST', 400);
      }
      if (key !== attempt.attemptId) {
        return problem('CHECKOUT_EXPIRED', 410, { attemptId: key });
      }

      const already = completed.get(key);
      if (already !== undefined) {
        // The retry answers with the stored response and does not repeat the effect.
        return HttpResponse.json(already, { status: 200 });
      }

      counters.orders += 1;
      completed.set(key, order);

      return HttpResponse.json(order, {
        status: 201,
        headers: { Location: `/api/orders/${order.id}` },
      });
    }),

    http.get(`${base}/api/orders/:id`, ({ params }) =>
      Number(params.id) === order.id
        ? HttpResponse.json(order)
        : problem('RESOURCE_NOT_FOUND', 404, { resource: 'Order', id: String(params.id) }),
    ),
  ];

  return { handlers, attempt, order, counters, seenKeys };
}

/** Opening the checkout failing, by `code` — an empty cart is the one the contract names (`422`). */
export function checkoutAttemptFails(code: ErrorCode, status: number, extensions: Record<string, unknown> = {}) {
  return http.post(`${base}/api/checkout/attempts`, () => problem(code, status, extensions));
}

/** Confirming failing, by `code`, over an attempt that was opened normally. */
export function placeOrderFails(code: ErrorCode, status: number, extensions: Record<string, unknown> = {}) {
  return http.post(`${base}/api/orders`, () => problem(code, status, extensions));
}
