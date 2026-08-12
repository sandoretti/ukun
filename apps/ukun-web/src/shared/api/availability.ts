import { useQuery } from '@tanstack/react-query';

import type { components } from '../../types/api';
import { useAuth } from '../hooks/useAuth';
import { http } from './http';
import { useMyProfile } from './profile';
import { useOnlineStore, useStore, type Store } from './stores';

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
 * Not exported: which store is being asked about is a decision, not a parameter a screen improvises.
 * The two stores RF-01 names are chosen by the two hooks below.
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
export type StoreAvailability = {
  store: Store | undefined;
  /** `undefined` for a product while the read is in flight or has failed — never "unknown" once it lands. */
  available: ReadonlyMap<number, boolean> | undefined;
  failed: boolean;
};

/**
 * Stock of a set of products in the online store — the one every visitor sees, with or without a
 * session, and the only one an order is ever placed against (RF-04). It pairs the two reads every
 * screen was pairing by hand — resolve the store, then ask for its stock — and reports a failure of
 * either as one flag, because from the screen's point of view they are the same thing: there is no
 * stock answer to show.
 */
export function useOnlineAvailability(productIds: readonly number[]): StoreAvailability {
  const storeQuery = useOnlineStore();
  const availabilityQuery = useAvailability(storeQuery.data?.id, productIds);

  return {
    store: storeQuery.data,
    available: availabilityQuery.data,
    failed: storeQuery.isError || availabilityQuery.isError,
  };
}

/**
 * Stock of the same products in the customer's favourite store — the second half of RF-01, shown
 * next to the online one and never instead of it.
 *
 * **The guard is the interesting part, and it is not an optimisation.** The catalogue and the
 * product sheet are public, and `GET /api/me/profile` answers `401` to a visitor — which, without
 * `allowAnonymous`, the axios interceptor reads as a dead session and turns into a trip to the
 * sign-in screen. Asking for the profile before knowing there is a session would throw every
 * anonymous reader out of the catalogue. The role is read through `useAuth` because that is where
 * roles are read (`hasRole`, one place in the whole app), and it also keeps the `403` an employee
 * would collect here from ever being requested.
 *
 * That is why this file reaches into `shared/hooks/`: the store being asked about is decided here,
 * as the private hook above says, and deciding it needs the session.
 *
 * Each step gates the next: no session, no profile; no favourite, no store; no store, no stock
 * request. A customer whose favourite store is not set never issues the second read at all.
 */
export function useFavoriteStoreAvailability(productIds: readonly number[]): StoreAvailability {
  const { hasRole } = useAuth();

  const profileQuery = useMyProfile({ enabled: hasRole('CUSTOMER') });
  const favoriteStoreId = profileQuery.data?.favoriteStoreId ?? undefined;

  const storeQuery = useStore(favoriteStoreId);
  const availabilityQuery = useAvailability(storeQuery.data?.id, productIds);

  return {
    store: storeQuery.data,
    available: availabilityQuery.data,
    // Reported so a screen can tell "not asked" from "asked and lost", even though today every
    // screen treats both the same way: it leaves the line out. This one degrades in silence — the
    // store the reader can actually buy from is already answered, and a second error message about
    // an informative line is noise.
    failed: profileQuery.isError || storeQuery.isError || availabilityQuery.isError,
  };
}
