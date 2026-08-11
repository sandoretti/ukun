import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/react-query';

import { http } from '../../../shared/api/http';
import { retryUnlessClientError } from '../../../shared/api/retry';
import {
  CHUNKS_PER_PAGE,
  CHUNK_SIZE,
  productKeys,
  type CatalogFilterInput,
  type ProductDetail,
  type ProductFilters,
  type ProductPage,
} from './productKeys';

function toQueryParams(filters: ProductFilters) {
  return {
    ...(filters.name ? { name: filters.name } : {}),
    ...(filters.typeId !== null ? { typeId: filters.typeId } : {}),
    ...(filters.priceMin ? { priceMin: filters.priceMin } : {}),
    ...(filters.priceMax ? { priceMax: filters.priceMax } : {}),
    sort: filters.sort,
    page: filters.page,
    size: filters.size,
  };
}

function fetchProducts(filters: ProductFilters, signal: AbortSignal) {
  return http
    .get<ProductPage>('/api/products', { params: toQueryParams(filters), signal })
    .then((response) => response.data);
}

/**
 * One catalog page, loaded chunk by chunk as the reader scrolls. Filtering by name ignores accents
 * and the ordering breaks ties by `id` server-side (defects #22 and #23) — nothing is filtered,
 * sorted or sliced in the browser; that tie-break is also what keeps a product from appearing in two
 * chunks or in none.
 *
 * The page number this takes counts from one, like the URL; what travels to the API is the chunk
 * index, from zero. `getNextPageParam` stops at whichever of the two borders comes first: the
 * page's own ceiling of `PAGE_SIZE` items, and the end of the result set. Returning `undefined`
 * there is what turns the "show more" button off and the pagination on.
 *
 * `keepPreviousData` keeps the current page on screen while the next one loads instead of
 * collapsing the grid on every page change (`docs/07-frontend-ux-ui.md`).
 */
export function useCatalogProducts(criteria: CatalogFilterInput, page: number) {
  return useInfiniteQuery({
    queryKey: productKeys.chunks(criteria, page),
    queryFn: ({ pageParam, signal }) =>
      fetchProducts({ ...criteria, page: pageParam, size: CHUNK_SIZE }, signal),
    initialPageParam: (page - 1) * CHUNKS_PER_PAGE,
    getNextPageParam: (lastChunk, loadedChunks) =>
      loadedChunks.length >= CHUNKS_PER_PAGE || lastChunk.page + 1 >= lastChunk.totalPages
        ? undefined
        : lastChunk.page + 1,
    placeholderData: keepPreviousData,
  });
}

/**
 * One product, by identifier. The public catalog only resolves active products, so a discontinued
 * one answers `404` with `code: RESOURCE_NOT_FOUND` (RF-01, ADR-0013) — a dedicated screen, never a
 * dimmed sheet and never the generic "something failed".
 *
 * The identifier is optional because it arrives from the URL and may not be one: the caller hands
 * over `undefined` for `/products/abc`, and nothing is asked.
 */
export function useProduct(id: number | undefined) {
  return useQuery({
    queryKey: productKeys.detail(id ?? -1),
    queryFn: ({ signal }) =>
      http.get<ProductDetail>(`/api/products/${id}`, { signal }).then((response) => response.data),
    enabled: id !== undefined,
    retry: retryUnlessClientError,
  });
}

/** How many neighbours the sheet shows, and how many are asked for to still have that many. */
const RELATED_SIZE = 4;

/**
 * The other products of the same type, for the strip at the foot of the detail sheet. It is the
 * listing read with one filter and a small page — the same `fetchProducts` and the same key factory,
 * not an endpoint of its own, so the cache entry is shared with anyone browsing that type.
 *
 * The API does not know which product is being looked at, so one extra is requested and the current
 * one is dropped here: otherwise a type with exactly five products would show four minus itself.
 */
export function useRelatedProducts(typeId: number | undefined, excludeId: number | undefined) {
  const filters: ProductFilters = {
    name: '',
    typeId: typeId ?? null,
    priceMin: '',
    priceMax: '',
    sort: 'PRICE_ASC',
    page: 0,
    size: RELATED_SIZE + 1,
  };

  return useQuery({
    queryKey: productKeys.list(filters),
    queryFn: ({ signal }) => fetchProducts(filters, signal),
    enabled: typeId !== undefined,
    select: (page) =>
      page.items.filter((item) => item.id !== excludeId).slice(0, RELATED_SIZE),
  });
}

/** One page of one element sorted downwards: the most expensive product of the whole catalog. */
const MOST_EXPENSIVE: ProductFilters = {
  name: '',
  typeId: null,
  priceMin: '',
  priceMax: '',
  sort: 'PRICE_DESC',
  page: 0,
  size: 1,
};

/** Rounded up to a multiple of this, so the end of the slider is a round number and not 749,00 €. */
const PRICE_STEP = 50;

/**
 * Where the price slider ends. It is read from the catalog instead of being hardcoded — a fixed
 * ceiling goes stale the day someone lists a more expensive sofa — and it deliberately reuses the
 * listing's own key factory: it is the same read with different filters, not a new endpoint.
 *
 * `Number()` on the amount is safe here and only here: the result is a slider bound rounded up to
 * the next multiple of 50, never money that is added, compared or charged (defect #5).
 */
export function useMaxProductPrice() {
  return useQuery<ProductPage, Error, number | undefined>({
    queryKey: productKeys.list(MOST_EXPENSIVE),
    queryFn: ({ signal }) => fetchProducts(MOST_EXPENSIVE, signal),
    staleTime: 60 * 60 * 1000,
    select: (page) =>
      page.items.length === 0
        ? undefined
        : Math.ceil(Number(page.items[0].price) / PRICE_STEP) * PRICE_STEP,
  });
}
