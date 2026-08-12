import type { StoreAvailability } from '../../shared/api/availability';

/** One store's answer about one product: its name, and whether there is stock. */
export type StoreStock = {
  storeName: string | undefined;
  /** `undefined` while the parallel availability read is still in flight or has failed. */
  available: boolean | undefined;
};

/**
 * A store's reading narrowed to one product, which is what a card takes.
 *
 * It lives in its own module and not next to the card because both grids —the catalogue and the
 * related strip— were spelling out the same two fields by hand, and a card reporting a different
 * store from its neighbour is exactly the kind of drift nobody notices. Out of `ProductCard.tsx`
 * because a file that exports components and functions alike loses fast refresh
 * (`react-refresh/only-export-components`).
 */
export function stockOf(availability: StoreAvailability, productId: number): StoreStock {
  return {
    storeName: availability.store?.name,
    available: availability.available?.get(productId),
  };
}

/**
 * The same, for the favourite store: `undefined` — and therefore a card with a single unnamed line
 * — until there is a session with a favourite store resolved (RF-01).
 */
export function favoriteStockOf(
  availability: StoreAvailability,
  productId: number,
): StoreStock | undefined {
  return availability.store === undefined ? undefined : stockOf(availability, productId);
}
