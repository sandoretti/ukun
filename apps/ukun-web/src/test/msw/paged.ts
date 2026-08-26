import type { components } from '../../types/api';

type PageMeta = components['schemas']['PageMeta'];

/**
 * One page of a listing, shaped like `PageMeta` + `items` — the wrapper every paginated endpoint of
 * this contract answers with (`docs/api/openapi/ukun-api.yaml`, schema `PageMeta`).
 *
 * It lives here and not in one of the doubles because the stock and the staff page identically, and
 * two copies of `Math.ceil(items.length / size)` are two chances of one of them counting
 * `totalPages` differently from the other — which would be a test passing against a pagination the
 * server does not have.
 */
export function paged<T>(items: T[], page: number, size: number): PageMeta & { items: T[] } {
  return {
    page,
    size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
    items: items.slice(page * size, page * size + size),
  };
}
