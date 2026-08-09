import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';

import type { ProductFilters, ProductSort } from './api/productKeys';

export const DEFAULT_SORT: ProductSort = 'PRICE_ASC';
export const DEFAULT_PAGE_SIZE = 12;
export const SORT_OPTIONS: ProductSort[] = ['PRICE_ASC', 'PRICE_DESC'];

/** What the user can change from the filter form; the page is driven by the pagination controls. */
export type CatalogFilterInput = Omit<ProductFilters, 'page' | 'size'>;

function readPositiveInteger(value: string | null): number | null {
  if (value === null || !/^\d+$/.test(value)) {
    return null;
  }
  return Number(value);
}

function readAmount(value: string | null): string {
  // Same shape as `Money` in the contract; anything else is dropped rather than sent to the API.
  return value !== null && /^\d{1,8}(\.\d{1,2})?$/.test(value) ? value : '';
}

function readFilters(searchParams: URLSearchParams): ProductFilters {
  const sort = searchParams.get('sort');
  return {
    name: searchParams.get('name')?.trim() ?? '',
    typeId: readPositiveInteger(searchParams.get('typeId')),
    priceMin: readAmount(searchParams.get('priceMin')),
    priceMax: readAmount(searchParams.get('priceMax')),
    sort: SORT_OPTIONS.includes(sort as ProductSort) ? (sort as ProductSort) : DEFAULT_SORT,
    page: readPositiveInteger(searchParams.get('page')) ?? 0,
    size: DEFAULT_PAGE_SIZE,
  };
}

function writeParams(filters: CatalogFilterInput, page: number): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.name.trim()) params.set('name', filters.name.trim());
  if (filters.typeId !== null) params.set('typeId', String(filters.typeId));
  if (filters.priceMin) params.set('priceMin', filters.priceMin);
  if (filters.priceMax) params.set('priceMax', filters.priceMax);
  if (filters.sort !== DEFAULT_SORT) params.set('sort', filters.sort);
  if (page > 0) params.set('page', String(page));
  return params;
}

/**
 * The listing's state lives in the URL, not in a `useState` (`docs/07-frontend-ux-ui.md`): that is
 * what makes a result linkable, survives a reload, and makes the back button do what the user
 * expects. Changing any filter goes back to the first page — staying on page 7 of a result set that
 * no longer has one is how a listing shows an empty screen for no reason.
 */
export function useCatalogParams() {
  const [searchParams, setSearchParams] = useSearchParams();

  const filters = useMemo(() => readFilters(searchParams), [searchParams]);

  const applyFilters = useCallback(
    (next: CatalogFilterInput) => setSearchParams(writeParams(next, 0)),
    [setSearchParams],
  );

  const clearFilters = useCallback(() => setSearchParams(new URLSearchParams()), [setSearchParams]);

  const goToPage = useCallback(
    (page: number) => setSearchParams(writeParams(filters, Math.max(0, page))),
    [filters, setSearchParams],
  );

  return { filters, applyFilters, clearFilters, goToPage };
}
