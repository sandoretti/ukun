import { BACKOFFICE_STOCK_PATH } from '../../shared/routes';
import type { StockFilterInput, StockStatus } from './api/stockKeys';

/**
 * The paths of this feature, and the one place that knows what a stock URL looks like. The listing's
 * own path is **not** declared here: the session menu of `features/auth` links to it, and a path a
 * second feature needs goes up to `shared/routes.ts` — features never import each other.
 *
 * The whole branch is `/backoffice/*` and deliberately not `/admin/*`: that one is reserved for what
 * talks to `/api/admin/**` (ADR-0020), and managing stock is any employee's job, not an
 * administrator's (RF-09, permission matrix of `docs/02-requisitos.md` §3).
 */
export const NEW_STOCK_LINE_PATH = '/backoffice/stock/new';

/** Pages count from one in the URL, whatever the API does with them. */
export const FIRST_PAGE = 1;

/** The half of RF-09 the screen opens on, which is also the contract's own default for `status`. */
export const DEFAULT_STATUS: StockStatus = 'STOCKED';

/**
 * The listing's query string. Defaults are left out: `/backoffice/stock` and
 * `?status=STOCKED&page=1` are the same screen, and only one of the two is worth putting in
 * someone's address bar.
 */
export function stockSearch(filters: Partial<StockFilterInput>, page: number = FIRST_PAGE): URLSearchParams {
  const params = new URLSearchParams();

  if (filters.status !== undefined && filters.status !== DEFAULT_STATUS) {
    params.set('status', filters.status);
  }
  if (filters.sku) {
    params.set('sku', filters.sku);
  }
  if (filters.q) {
    params.set('q', filters.q);
  }
  if (page > FIRST_PAGE) {
    params.set('page', String(page));
  }

  return params;
}

/**
 * The listing, filtered. It is how a screen sends the reader to one specific row without inventing a
 * way to address one: filtering by SKU leaves exactly the line in question on screen, which is what
 * both a fresh creation and a "this one already exists" need to land on.
 */
export function stockPath(filters: Partial<StockFilterInput> = {}, page: number = FIRST_PAGE): string {
  const query = stockSearch(filters, page).toString();

  return query === '' ? BACKOFFICE_STOCK_PATH : `${BACKOFFICE_STOCK_PATH}?${query}`;
}

/**
 * The add screen, optionally carrying the SKU to resolve on arrival. It is what every row of the
 * "not stocked" tab links to, so the employee does not have to retype a SKU that is already on
 * screen.
 */
export function newStockLinePath(sku?: string): string {
  return sku === undefined ? NEW_STOCK_LINE_PATH : `${NEW_STOCK_LINE_PATH}?sku=${encodeURIComponent(sku)}`;
}
