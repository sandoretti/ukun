import { useQuery } from '@tanstack/react-query';

import type { components } from '../../types/api';
import { http } from './http';

export type Store = components['schemas']['Store'];

/**
 * Stores are read by more than one feature (the catalog resolves the online store to ask for
 * availability, the stores screen lists them), so the key factory lives in `shared/` — features
 * never import each other.
 */
export const storeKeys = {
  all: ['stores'] as const,
  lists: () => [...storeKeys.all, 'list'] as const,
};

/** Closed reference data: a flat array, cached for the whole session. */
const storesQuery = {
  queryKey: storeKeys.lists(),
  queryFn: ({ signal }: { signal: AbortSignal }) =>
    http.get<Store[]>('/api/stores', { signal }).then((response) => response.data),
  staleTime: 60 * 60 * 1000,
};

export function useStores() {
  return useQuery(storesQuery);
}

/**
 * The online store, the one the anonymous catalog asks availability about (RF-01). Exactly one row
 * has `isOnline` set — it is a database invariant (`ux_store_online`), which is why the identifier
 * is resolved from the contract instead of being hardcoded on screen. It reuses the listing's cache
 * entry: same key, a `select` on top.
 */
export function useOnlineStore() {
  return useQuery({
    ...storesQuery,
    select: (stores: Store[]) => stores.find((store) => store.isOnline),
  });
}
