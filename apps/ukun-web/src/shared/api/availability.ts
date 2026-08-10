import { useQuery } from '@tanstack/react-query';

import type { components } from '../../types/api';
import { http } from './http';
import { useOnlineStore, type Store } from './stores';

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
 *
 * Not exported: which store is being asked about is a decision, not a parameter a screen improvises
 * — today it is always the online one, and v0.5 will add the customer's favourite next to it, here.
 */
function useAvailability(storeId: number | undefined, productIds: readonly number[]) {
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

/** What a screen needs to paint stock: the store it is talking about, the answer, and whether it came. */
export type OnlineAvailability = {
  store: Store | undefined;
  /** `undefined` for a product while the read is in flight or has failed — never "unknown" once it lands. */
  available: ReadonlyMap<number, boolean> | undefined;
  failed: boolean;
};

/**
 * Stock of a set of products in the online store, which in v0.3 is the only one anyone asks about
 * (`isOnline`, a database invariant). It pairs the two reads every screen was pairing by hand —
 * resolve the store, then ask for its stock — and reports a failure of either as one flag, because
 * from the screen's point of view they are the same thing: there is no stock answer to show.
 *
 * Availability of the customer's favourite store depends on there being a session, and that is
 * v0.5/v0.6; this hook is the seam where it will be chosen.
 */
export function useOnlineAvailability(productIds: readonly number[]): OnlineAvailability {
  const storeQuery = useOnlineStore();
  const availabilityQuery = useAvailability(storeQuery.data?.id, productIds);

  return {
    store: storeQuery.data,
    available: availabilityQuery.data,
    failed: storeQuery.isError || availabilityQuery.isError,
  };
}
