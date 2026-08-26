import type { components } from '../../../types/api';

export type AdminProduct = components['schemas']['AdminProduct'];
export type AdminProductPage = components['schemas']['AdminProductPage'];
export type CreateProductRequest = components['schemas']['CreateProductRequest'];
export type UpdateProductRequest = components['schemas']['UpdateProductRequest'];
export type ProductImage = components['schemas']['ProductImage'];
export type ProductStatusFilter = components['schemas']['ProductStatusFilter'];
export type AdminProductSort = components['schemas']['AdminProductSort'];

/**
 * What the back-office listing is filtered and sorted by — the part of the URL that is not the page.
 *
 * `status` is a filter here and not a view, unlike the two halves of the stock screen: RF-10 asks for
 * one table that can be pointed at the active products, at the discontinued ones or at both, because
 * finding a discontinued product is the only way to reactivate it. `typeId` is `null` and never `0`
 * for "any type": zero is a perfectly good identifier and would be indistinguishable from no filter.
 */
export type AdminProductFilterInput = {
  q: string;
  sku: string;
  typeId: number | null;
  status: ProductStatusFilter;
  sort: AdminProductSort;
};

/** One request to `GET /api/admin/products`: the criteria plus the API's own slice, `page` from zero. */
export type AdminProductFilters = AdminProductFilterInput & {
  page: number;
  size: number;
};

/** How many rows a page of the table holds. One request per page. */
export const PAGE_SIZE = 20;

/**
 * The listing's state as the API takes it.
 *
 * **This is the one place the page number is translated.** The URL counts from one and the API from
 * zero (`docs/07-frontend-ux-ui.md`), and doing it once is what keeps a `?page=1` from meaning the
 * second page here and the first one somewhere else.
 */
export function toApiFilters(filters: AdminProductFilterInput, page: number): AdminProductFilters {
  return { ...filters, page: page - 1, size: PAGE_SIZE };
}

/**
 * Query keys of the back-office catalog, hierarchical so invalidating is targeted by prefix.
 *
 * A root of its own and **not** a branch of the catalog's `productKeys`: this read answers something
 * the public one cannot —a discontinued product— and the two live in different features, which never
 * import each other. Writing `['products', ...]` by hand here to share the prefix is exactly the
 * cache bug the conventions warn about.
 */
export const adminProductKeys = {
  all: ['admin-products'] as const,
  lists: () => [...adminProductKeys.all, 'list'] as const,
  list: (filters: AdminProductFilters) => [...adminProductKeys.lists(), filters] as const,
  details: () => [...adminProductKeys.all, 'detail'] as const,
  detail: (id: number) => [...adminProductKeys.details(), id] as const,
};
