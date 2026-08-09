import type { components } from '../../../types/api';

export type ProductSort = components['schemas']['ProductSort'];
export type ProductSummary = components['schemas']['ProductSummary'];
export type ProductPage = components['schemas']['ProductPage'];
export type ProductType = components['schemas']['ProductType'];

/** Everything the listing is filtered, sorted and paginated by. It is also the URL's state. */
export type ProductFilters = {
  name: string;
  typeId: number | null;
  priceMin: string;
  priceMax: string;
  sort: ProductSort;
  page: number;
  size: number;
};

/**
 * Query keys of the catalog, hierarchical so invalidating can be targeted by prefix instead of
 * wiping the whole cache. Never write one of these arrays by hand inside a component.
 */
export const productKeys = {
  all: ['products'] as const,
  lists: () => [...productKeys.all, 'list'] as const,
  list: (filters: ProductFilters) => [...productKeys.lists(), filters] as const,
  details: () => [...productKeys.all, 'detail'] as const,
  detail: (id: number) => [...productKeys.details(), id] as const,
};

/** A different entity from a product, so a factory of its own rather than a branch of the above. */
export const productTypeKeys = {
  all: ['product-types'] as const,
  lists: () => [...productTypeKeys.all, 'list'] as const,
};
