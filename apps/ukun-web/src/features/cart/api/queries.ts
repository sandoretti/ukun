import { useMutation, useQueryClient } from '@tanstack/react-query';

import { http } from '../../../shared/api/http';
import { cartKeys } from '../../../shared/api/cart';

/**
 * The two writes only this screen makes. They hang off `cartKeys` in `shared/api/cart.ts` rather
 * than declaring a second factory: one key, one owner, invalidated by prefix.
 *
 * Both answer `204` and no body, unlike the upsert — so there is nothing to write into the cache and
 * the new state comes from the refetch the invalidation triggers. Recomputing the total here from
 * the lines that are left would be exactly the client-side arithmetic on money the contract returns
 * the whole cart to avoid.
 */

/** Removing one line. A line that is not there answers `404`, which the screen reports as an error. */
export function useRemoveCartItem() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (productId: number) => {
      await http.delete(`/api/cart/items/${productId}`);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: cartKeys.all }),
  });
}

/**
 * Emptying the cart in one operation — the one the legacy system never had, where the only ways out
 * were deleting the lines one by one or completing an order (RF-03).
 *
 * Idempotent: emptying an already empty cart is also a `204`, so a double click is not an error.
 */
export function useClearCart() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      await http.delete('/api/cart');
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: cartKeys.all }),
  });
}
