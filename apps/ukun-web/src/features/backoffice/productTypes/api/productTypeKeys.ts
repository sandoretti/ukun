import type { components } from '../../../../types/api';

export type AdminProductType = components['schemas']['AdminProductType'];
export type ProductTypeRequest = components['schemas']['ProductTypeRequest'];

/**
 * Query keys of the back-office's product types (RF-13).
 *
 * **The root is its own and not a branch of `productTypeKeys`** of `shared/api/productTypes.ts`,
 * even though both read the same six rows: they are two reads with different audiences and different
 * shapes — the public one is the closed set that feeds the catalog filter, this one adds the
 * `productCount` that decides whether a type can be deleted at all (ADR-0020). Sharing a root would
 * make the two answers collide in the cache under a prefix invalidation.
 *
 * What they do share is that a write here stales the other one, and that is `useInvalidateTypes`'s
 * job in `queries.ts`.
 *
 * There is no `list(filters)`: the endpoint takes no parameters, so there is exactly one list.
 */
export const adminProductTypeKeys = {
  all: ['admin-product-types'] as const,
  lists: () => [...adminProductTypeKeys.all, 'list'] as const,
};
