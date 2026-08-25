import { http, HttpResponse } from 'msw';

import type { components } from '../../types/api';
import { normalise, products } from './catalogHandlers';
import { problem } from './problem';

/**
 * Test-only stand-in for the stock endpoints of `ukun-api` (RF-09) and for `GET /api/me/employment`,
 * typed from the same generated OpenAPI types the app uses — **except the employment**, which the
 * contract does not carry yet and whose type is written by hand in
 * `features/backoffice/api/employment.ts`. That one is the one place where this double and the app
 * could agree on something the server never said, and it is why the type lives in a single file.
 *
 * Like the cart's, this double **keeps state**: the point of these screens is that a write changes
 * what the next read answers, and a stateless stub would let a test pass while the cache was never
 * refreshed. The state belongs to each `stockState(...)` call, so it dies with the test that
 * installed it.
 */

type StockLine = components['schemas']['StockLine'];
type StockLinePage = components['schemas']['StockLinePage'];
type CreateStockLineRequest = components['schemas']['CreateStockLineRequest'];
type UpdateStockLineRequest = components['schemas']['UpdateStockLineRequest'];
type ErrorCode = components['schemas']['ErrorCode'];

const base = import.meta.env.VITE_API_URL || '';

/** What `GET /api/me/employment` answers. Mirrors the hand-written `Employment` of the feature. */
type Employment = {
  employeeId: number;
  storeId: number;
  storeName: string;
};

/** The store every test works against unless it says otherwise. Store 1 is also the online one. */
export const employment: Employment = { employeeId: 7, storeId: 1, storeName: 'Ukun Gran Vía' };

export function employmentAt(overrides: Partial<Employment> = {}) {
  const body = { ...employment, ...overrides };

  return http.get(`${base}/api/me/employment`, () => HttpResponse.json(body));
}

/** No employee row behind the account — a `403`, the same one a foreign store would get. */
export function employmentFails(code: ErrorCode, status: number) {
  return http.get(`${base}/api/me/employment`, () => problem(code, status));
}

const EMPTY_PAGE: StockLinePage = { page: 0, size: 20, totalElements: 0, totalPages: 1, items: [] };

/**
 * The default: an employee of store 1 whose store holds nothing, and **no writes**. A test that
 * writes installs its own state with `stockState(...)`; without it the write has no handler and the
 * run fails, which is the answer this suite wants for a request nobody accounted for.
 */
export const stockHandlers = [
  employmentAt(),
  http.get(`${base}/api/stores/:storeId/stock`, () => HttpResponse.json(EMPTY_PAGE)),
];

/** A row of the store's stock, as the database would hold it. The rest is joined from the catalog. */
export type SeedLine = {
  lineId: number;
  productId: number;
  quantity: number;
  version: number;
};

const productOf = (productId: number) => products.find((item) => item.id === productId)!;

function paged(items: StockLine[], page: number, size: number): StockLinePage {
  return {
    page,
    size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
    items: items.slice(page * size, page * size + size),
  };
}

/**
 * A store's stock with a life of its own for one test:
 * `server.use(...stockState({ lines: [line(1, 10)] }).handlers)`.
 *
 * It reproduces the contract: the listing answers either the rows or the anti-join depending on
 * `status`, filters by exact SKU and by name, and pages; the `POST` refuses a SKU with no active
 * product (`404`) and one that already has a row (`409 REFERENTIAL_INTEGRITY`); the `PUT` compares
 * the `version` and answers `409 VERSION_CONFLICT` when it does not match, bumping it when it does;
 * the `DELETE` answers `204`.
 */
export function stockState(options: { storeId?: number; lines?: readonly SeedLine[] } = {}) {
  const storeId = options.storeId ?? employment.storeId;
  let rows: SeedLine[] = (options.lines ?? []).map((line) => ({ ...line }));
  let nextLineId = Math.max(0, ...rows.map((row) => row.lineId)) + 1;

  /** How many writes actually reached the server — what proves client-side validation stopped one. */
  const counters = { created: 0, updated: 0, deleted: 0 };
  /** Every `version` the `PUT` was sent, in order: what proves a retry carried the fresh one. */
  const versionsSeen: number[] = [];
  /** Every write's URL, so a test can check which store it went to without reading it off the screen. */
  const urls: string[] = [];
  /** Every listing query string, in order: what proves a filter was applied by the server, not here. */
  const searches: string[] = [];

  const toStockedLine = (row: SeedLine): StockLine => {
    const product = productOf(row.productId);

    return {
      lineId: row.lineId,
      productId: product.id,
      sku: product.sku,
      productName: product.name,
      quantity: row.quantity,
      version: row.version,
    };
  };

  const toCandidate = (product: (typeof products)[number]): StockLine => ({
    lineId: null,
    productId: product.id,
    sku: product.sku,
    productName: product.name,
    quantity: null,
    version: null,
  });

  /** The other employee, in one line: changes the quantity and bumps the version behind your back. */
  const bumpQuantity = (lineId: number, quantity: number) => {
    rows = rows.map((row) => (row.lineId === lineId ? { ...row, quantity, version: row.version + 1 } : row));
  };

  /** The same other employee, deleting instead of editing. */
  const removeLine = (lineId: number) => {
    rows = rows.filter((row) => row.lineId !== lineId);
  };

  const handlers = [
    employmentAt({ storeId }),

    http.get(`${base}/api/stores/:storeId/stock`, ({ request, params }) => {
      if (Number(params.storeId) !== storeId) {
        return problem('FORBIDDEN', 403);
      }

      const search = new URL(request.url).searchParams;
      searches.push(search.toString());

      const status = search.get('status') ?? 'STOCKED';
      const sku = search.get('sku');
      const term = search.get('q');
      const page = Number(search.get('page') ?? 0);
      const size = Number(search.get('size') ?? 20);

      const stockedIds = new Set(rows.map((row) => row.productId));

      const items =
        status === 'NOT_STOCKED'
          ? products.filter((product) => !stockedIds.has(product.id)).map(toCandidate)
          : rows.map(toStockedLine);

      const matched = items
        .filter((item) => (sku ? item.sku === sku : true))
        .filter((item) => (term ? normalise(item.productName).includes(normalise(term)) : true));

      return HttpResponse.json(paged(matched, page, size));
    }),

    http.post(`${base}/api/stores/:storeId/stock`, async ({ request, params }) => {
      urls.push(new URL(request.url).pathname);

      if (Number(params.storeId) !== storeId) {
        return problem('FORBIDDEN', 403);
      }

      const body = (await request.json()) as CreateStockLineRequest;
      const product = products.find((item) => item.sku === body.sku);

      if (product === undefined) {
        return problem('RESOURCE_NOT_FOUND', 404, { resource: 'product', id: body.sku });
      }
      if (rows.some((row) => row.productId === product.id)) {
        // `ux_store_stock`: the product already has a line here. Edit that one, do not add another.
        return problem('REFERENTIAL_INTEGRITY', 409);
      }

      const created: SeedLine = {
        lineId: nextLineId,
        productId: product.id,
        quantity: body.quantity,
        version: 0,
      };
      nextLineId += 1;
      rows = [...rows, created];
      counters.created += 1;

      return HttpResponse.json(toStockedLine(created), { status: 201 });
    }),

    http.put(`${base}/api/stores/:storeId/stock/:lineId`, async ({ request, params }) => {
      urls.push(new URL(request.url).pathname);

      if (Number(params.storeId) !== storeId) {
        return problem('FORBIDDEN', 403);
      }

      const lineId = Number(params.lineId);
      const body = (await request.json()) as UpdateStockLineRequest;
      versionsSeen.push(body.version);

      const row = rows.find((candidate) => candidate.lineId === lineId);

      if (row === undefined) {
        return problem('RESOURCE_NOT_FOUND', 404, { resource: 'StockLine', id: String(lineId) });
      }
      if (row.version !== body.version) {
        return problem('VERSION_CONFLICT', 409, { resource: 'StockLine', id: String(lineId) });
      }

      const updated: SeedLine = { ...row, quantity: body.quantity, version: row.version + 1 };
      rows = rows.map((candidate) => (candidate.lineId === lineId ? updated : candidate));
      counters.updated += 1;

      return HttpResponse.json(toStockedLine(updated));
    }),

    http.delete(`${base}/api/stores/:storeId/stock/:lineId`, ({ request, params }) => {
      urls.push(new URL(request.url).pathname);

      if (Number(params.storeId) !== storeId) {
        return problem('FORBIDDEN', 403);
      }

      const lineId = Number(params.lineId);

      if (!rows.some((row) => row.lineId === lineId)) {
        return problem('RESOURCE_NOT_FOUND', 404, { resource: 'StockLine', id: String(lineId) });
      }

      rows = rows.filter((row) => row.lineId !== lineId);
      counters.deleted += 1;

      return new HttpResponse(null, { status: 204 });
    }),
  ];

  return { handlers, counters, versionsSeen, urls, searches, bumpQuantity, removeLine };
}

/** The listing failing, by `code` — the screen has nothing to show and paints the error state. */
export function stockFails(code: ErrorCode, status: number) {
  return http.get(`${base}/api/stores/:storeId/stock`, () => problem(code, status));
}

/** The quantity write failing while the table is already painted: what is on screen has to stay. */
export function updateStockFails(code: ErrorCode, status: number, extensions: Record<string, unknown> = {}) {
  return http.put(`${base}/api/stores/:storeId/stock/:lineId`, () => problem(code, status, extensions));
}

/** The creation failing, for the outcomes the add screen has to tell apart. */
export function createStockFails(code: ErrorCode, status: number, extensions: Record<string, unknown> = {}) {
  return http.post(`${base}/api/stores/:storeId/stock`, () => problem(code, status, extensions));
}
