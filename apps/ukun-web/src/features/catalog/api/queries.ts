import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { http } from '../../../shared/api/http';
import {
  productKeys,
  productTypeKeys,
  type ProductFilters,
  type ProductPage,
  type ProductType,
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

/**
 * The catalog listing. Filtering by name ignores accents and the ordering breaks ties by `id`
 * server-side (defects #22 and #23) — nothing is filtered, sorted or sliced in the browser.
 * `keepPreviousData` keeps the previous page on screen while the next one loads instead of
 * collapsing the grid on every page change.
 */
export function useProducts(filters: ProductFilters) {
  return useQuery<ProductPage>({
    queryKey: productKeys.list(filters),
    queryFn: ({ signal }) =>
      http
        .get<ProductPage>('/api/products', { params: toQueryParams(filters), signal })
        .then((response) => response.data),
    placeholderData: keepPreviousData,
  });
}

/** Feeds the type filter: a small closed set the SPA caches whole. */
export function useProductTypes() {
  return useQuery({
    queryKey: productTypeKeys.lists(),
    queryFn: ({ signal }) =>
      http.get<ProductType[]>('/api/product-types', { signal }).then((response) => response.data),
    staleTime: 60 * 60 * 1000,
  });
}
