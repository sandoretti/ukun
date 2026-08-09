import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/react-query';

import { http } from '../../../shared/api/http';
import {
  CHUNKS_PER_PAGE,
  CHUNK_SIZE,
  productKeys,
  type CatalogFilterInput,
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
