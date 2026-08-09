import { useQuery } from '@tanstack/react-query';

import type { components } from '../../types/api';
import { http } from './http';

type AvailabilityResponse = components['schemas']['AvailabilityResponse'];

export const availabilityKeys = {
  all: ['availability'] as const,
  byStore: (storeId: number) => [...availabilityKeys.all, storeId] as const,
  list: (storeId: number, productIds: readonly number[]) =>
    [...availabilityKeys.byStore(storeId), productIds] as const,
};

/**
 * Availability of a set of products in one store, read **separately** from the catalog: `catalog`
 * cannot depend on `inventory` (ADR-0017 §3), so the screen fires both requests in parallel and
 * composes them. The response carries one entry per requested identifier — a product with no stock
 * row comes back as `false`, never as a missing entry (RF-01), so the map below is total over what
 * was asked for.
 */
export function useAvailability(storeId: number | undefined, productIds: readonly number[]) {
  const enabled = storeId !== undefined && productIds.length > 0;

  return useQuery({
    queryKey: availabilityKeys.list(storeId ?? -1, productIds),
    queryFn: ({ signal }) =>
      http
        .get<AvailabilityResponse>('/api/availability', {
          params: { store: storeId, products: productIds.join(',') },
          signal,
        })
        .then((response) => response.data),
    enabled,
    select: (data) => new Map(data.items.map((item) => [item.productId, item.available])),
  });
}
