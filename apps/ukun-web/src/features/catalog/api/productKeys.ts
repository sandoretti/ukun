import type { components } from '../../../types/api';

export type ProductSort = components['schemas']['ProductSort'];
export type ProductSummary = components['schemas']['ProductSummary'];
export type ProductPage = components['schemas']['ProductPage'];
export type ProductType = components['schemas']['ProductType'];

/** Everything the listing is filtered and sorted by — the part of the URL that is not the page. */
export type CatalogFilterInput = {
  name: string;
  typeId: number | null;
  priceMin: string;
  priceMax: string;
  sort: ProductSort;
};

/** One request to `GET /api/products`: the criteria plus the API's own slice, `page` from zero. */
export type ProductFilters = CatalogFilterInput & {
  page: number;
  size: number;
};

/**
 * The listing paginates on two levels, and they are not the same number.
 *
 * `CHUNK_SIZE` is what one request asks for and what scrolling to the bottom adds — 24 closes whole
 * rows on the 2/3/4-column grid. `PAGE_SIZE` is where that stops: after `CHUNKS_PER_PAGE` chunks the
 * page is full and moving on is the pagination's job, so neither the DOM nor the cache grows without
 * end. It is the same shape as chollometro's listing, and it is why the API's `totalPages` (counted
 * in chunks) is never what the pagination shows.
 */
export const CHUNK_SIZE = 24;
export const PAGE_SIZE = 72;
export const CHUNKS_PER_PAGE = PAGE_SIZE / CHUNK_SIZE;

/**
 * Query keys of the catalog, hierarchical so invalidating can be targeted by prefix instead of
 * wiping the whole cache. Never write one of these arrays by hand inside a component.
 */
export const productKeys = {
  all: ['products'] as const,
  lists: () => [...productKeys.all, 'list'] as const,
  list: (filters: ProductFilters) => [...productKeys.lists(), filters] as const,
  /** One catalog page loaded chunk by chunk: still under `lists()`, so invalidating by prefix hits it. */
  chunks: (criteria: CatalogFilterInput, page: number) =>
    [...productKeys.lists(), 'chunks', criteria, page] as const,
  details: () => [...productKeys.all, 'detail'] as const,
  detail: (id: number) => [...productKeys.details(), id] as const,
};

/** A different entity from a product, so a factory of its own rather than a branch of the above. */
export const productTypeKeys = {
  all: ['product-types'] as const,
  lists: () => [...productTypeKeys.all, 'list'] as const,
};
