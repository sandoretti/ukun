import { useQuery } from '@tanstack/react-query';

import type { components } from '../../types/api';
import { http } from './http';

export type ProductType = components['schemas']['ProductType'];

/**
 * A different entity from a product, so a factory of its own rather than a branch of `productKeys`.
 * It lives in `shared/` for the same reason `stores.ts` does: the type filter of the catalog and the
 * featured categories of the landing page read the same closed set, and features never import each
 * other (`docs/07-frontend-ux-ui.md`).
 */
export const productTypeKeys = {
  all: ['product-types'] as const,
  lists: () => [...productTypeKeys.all, 'list'] as const,
};

/** Feeds the type filter and the landing's categories: a small closed set the SPA caches whole. */
export function useProductTypes() {
  return useQuery({
    queryKey: productTypeKeys.lists(),
    queryFn: ({ signal }) =>
      http.get<ProductType[]>('/api/product-types', { signal }).then((response) => response.data),
    staleTime: 60 * 60 * 1000,
  });
}
