import type { components } from '../../../types/api';

type StockLine = components['schemas']['StockLine'];
type PageMeta = components['schemas']['PageMeta'];

export type StockStatus = components['schemas']['StockStatus'];
export type CreateStockLineRequest = components['schemas']['CreateStockLineRequest'];
export type UpdateStockLineRequest = components['schemas']['UpdateStockLineRequest'];

/**
 * A stock line that exists in the store: the three fields `status=STOCKED` guarantees are not null.
 *
 * The contract describes both halves of RF-09 with a single `StockLine`, where `lineId`, `quantity`
 * and `version` are `null` for a product that has no row in this store yet. That is right for the
 * wire and useless for a screen: nothing would stop a component from sending `null` as the `version`
 * of a `PUT`. The narrowing happens once, in the `select` of each query, so no view ever sees the
 * contract's own type — and no component needs a `!` or a `?? 0`.
 */
export type StockedLine = StockLine & { lineId: number; quantity: number; version: number };

/** A catalog product with no row in this store: a candidate for `POST`, and nothing else. */
export type UnstockedCandidate = StockLine & { lineId: null; quantity: null; version: null };

export type StockedLinePage = PageMeta & { items: StockedLine[] };
export type UnstockedCandidatePage = PageMeta & { items: UnstockedCandidate[] };

export function isStocked(line: StockLine): line is StockedLine {
  return line.lineId !== null && line.quantity !== null && line.version !== null;
}

export function isUnstocked(line: StockLine): line is UnstockedCandidate {
  return line.lineId === null && line.quantity === null && line.version === null;
}

/** What the listing is filtered by — the part of the URL that is not the page. */
export type StockFilterInput = {
  /** Which of the two halves of RF-09 is on screen. It is the view, not a filter. */
  status: StockStatus;
  sku: string;
  q: string;
};

/** One request to `GET /api/stores/{storeId}/stock`: the filters plus the API's slice, `page` from zero. */
export type StockFilters = StockFilterInput & {
  page: number;
  size: number;
};

/** How many rows a page of the table holds. One request per page; the contract has no sort. */
export const PAGE_SIZE = 20;

/**
 * The `pattern` the contract puts on `Sku` (`ukun-api.yaml`, schema `Sku`). It lives here so a
 * mistyped SKU is rejected before it becomes a request the server would only answer with a `400`.
 */
export const SKU_PATTERN = /^[A-Z0-9-]{3,32}$/u;

/**
 * Query keys of a store's stock, hierarchical so invalidating is targeted by prefix.
 *
 * The store is part of the key and not an afterthought: the same browser can hold the stock of two
 * stores in cache — an employee moved between stores, or two tabs — and a write to one must not
 * quietly refresh the other's rows.
 */
export const stockKeys = {
  all: ['store-stock'] as const,
  store: (storeId: number) => [...stockKeys.all, storeId] as const,
  lists: (storeId: number) => [...stockKeys.store(storeId), 'list'] as const,
  list: (storeId: number, filters: StockFilters) => [...stockKeys.lists(storeId), filters] as const,
};

/**
 * Resolving a product by SKU, on its own root rather than under the catalog's `['products']`.
 *
 * Hanging it there would be a key written by hand in two features, which is the cache bug the
 * conventions warn about. The price — invalidating the catalog does not reach this read — costs
 * nothing, because a SKU is immutable (ADR-0015): the answer to "which product is ABC-123" does not
 * change while the tab is open.
 */
export const skuLookupKeys = {
  all: ['product-by-sku'] as const,
  sku: (sku: string) => [...skuLookupKeys.all, sku] as const,
};
