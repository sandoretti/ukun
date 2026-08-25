import { http, HttpResponse } from 'msw';

import type { components } from '../../types/api';
import { problem } from './problem';

/**
 * Test-only stand-in for the cart endpoints of `ukun-api` (RF-03), typed from the same generated
 * OpenAPI types the app uses, so a contract change breaks compilation here too. It is not a dev
 * backend: the browser always talks to the real API (`docs/13-testing.md` §2).
 *
 * Unlike the other doubles, this one **keeps state**: the whole point of the screen is that a write
 * changes what the next read answers, and a stateless stub would let a test pass while the cache was
 * never refreshed. The state belongs to each `cartState(...)` call, so it dies with the test that
 * installed it instead of leaking into the next one through `server.resetHandlers()`.
 */

type Cart = components['schemas']['Cart'];
type CartLine = components['schemas']['CartLine'];
type Money = components['schemas']['Money'];
type UpdateCartItemRequest = components['schemas']['UpdateCartItemRequest'];
type ErrorCode = components['schemas']['ErrorCode'];

const base = import.meta.env.VITE_API_URL || '';

/**
 * Money is arithmetic on cents, never on a `double`: the double computes what the server computes,
 * and the exact decimal string is what travels — the same rule the app follows on the way out.
 */
function toCents(amount: Money): number {
  const [units, decimals = ''] = amount.split('.');

  return Number(units) * 100 + Number(decimals.padEnd(2, '0').slice(0, 2));
}

function toMoney(cents: number): Money {
  return `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, '0')}`;
}

/** The catalogue this double knows about, which is what a `PUT` on an unknown id fails against. */
type KnownProduct = Omit<CartLine, 'quantity' | 'lineTotal'>;

export const sofa: KnownProduct = {
  productId: 1,
  sku: 'SOFA-NORDIC-3P',
  productName: 'Sofá Nordic 3 plazas',
  imageUrl: '/media/products/sofa-nordic.jpg',
  unitPrice: '399.99',
  available: 7,
};

/**
 * Fewer units available than a test will ask for: this is the one that triggers the warning. It is
 * also **the one with no photo** — the two cases of `imageUrl` are shared out between the two known
 * products so that any test showing both lines shows both, and neither has to be set up for it.
 */
export const lamp: KnownProduct = {
  productId: 2,
  sku: 'LAMP-OSLO',
  productName: 'Lámpara Oslo',
  imageUrl: null,
  unitPrice: '49.50',
  available: 3,
};

const knownProducts = [sofa, lamp];

/** A line of the cart, with the totals the server would have computed. */
export function lineOf(product: KnownProduct, quantity: number): CartLine {
  return { ...product, quantity, lineTotal: toMoney(toCents(product.unitPrice) * quantity) };
}

/** The cart the server would have computed for these lines — exported for a double of its own. */
export function cartOf(lines: readonly CartLine[]): Cart {
  return {
    items: [...lines],
    total: toMoney(lines.reduce((cents, line) => cents + toCents(line.lineTotal), 0)),
  };
}

/** An empty cart is a `200` with no lines and a total of zero, never a `404`. */
const EMPTY_CART: Cart = { items: [], total: '0.00' };

/**
 * The default: an empty cart, and **no writes**. A test that mutates the cart installs its own
 * state with `cartState(...)`; without it the write has no handler and the run fails, which is the
 * answer this suite wants for a request nobody accounted for.
 */
export const cartHandlers = [http.get(`${base}/api/cart`, () => HttpResponse.json(EMPTY_CART))];

/**
 * A cart with a life of its own for one test: `server.use(...cartState([lineOf(sofa, 2)]))`.
 *
 * It reproduces the four operations as the API implements them — the upsert takes the **total**
 * quantity and answers with the whole cart, both deletes answer `204`, and emptying an already empty
 * cart answers `204` again.
 */
export function cartState(initial: readonly CartLine[] = []) {
  let lines = initial.map((line) => ({ ...line }));

  return [
    http.get(`${base}/api/cart`, () => HttpResponse.json(cartOf(lines))),

    http.put(`${base}/api/cart/items/:productId`, async ({ params, request }) => {
      const productId = Number(params.productId);
      const { quantity } = (await request.json()) as UpdateCartItemRequest;

      if (quantity < 1) {
        return problem('VALIDATION_FAILED', 400, {
          errors: [{ field: 'quantity', message: 'must be greater than or equal to 1' }],
        });
      }

      const product = knownProducts.find((known) => known.productId === productId);
      if (product === undefined) {
        // Same answer for a product that never existed and one that was discontinued.
        return problem('RESOURCE_NOT_FOUND', 404, { resource: 'Product', id: String(productId) });
      }

      lines = lines.some((line) => line.productId === productId)
        ? lines.map((line) => (line.productId === productId ? lineOf(product, quantity) : line))
        : [...lines, lineOf(product, quantity)];

      return HttpResponse.json(cartOf(lines));
    }),

    http.delete(`${base}/api/cart/items/:productId`, ({ params }) => {
      const productId = Number(params.productId);

      if (!lines.some((line) => line.productId === productId)) {
        return problem('RESOURCE_NOT_FOUND', 404, { resource: 'CartItem', id: String(productId) });
      }

      lines = lines.filter((line) => line.productId !== productId);

      return new HttpResponse(null, { status: 204 });
    }),

    http.delete(`${base}/api/cart`, () => {
      lines = [];

      return new HttpResponse(null, { status: 204 });
    }),
  ];
}

/** The read failing, by `code` — the screen has nothing to show and paints the error state. */
export function cartFails(code: ErrorCode, status: number) {
  return http.get(`${base}/api/cart`, () => problem(code, status));
}

/** The upsert failing while the cart is already painted: what is on screen has to stay there. */
export function putCartItemFails(code: ErrorCode, status: number, extensions: Record<string, unknown> = {}) {
  return http.put(`${base}/api/cart/items/:productId`, () => problem(code, status, extensions));
}
