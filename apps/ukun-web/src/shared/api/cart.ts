import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { components } from '../../types/api';
import { http } from './http';
import { retryUnlessClientError } from './retry';
import { useAuth } from '../hooks/useAuth';

export type Cart = components['schemas']['Cart'];
export type CartLine = components['schemas']['CartLine'];
type UpdateCartItemRequest = components['schemas']['UpdateCartItemRequest'];

/**
 * Query keys of the cart, under a root of their own so an invalidation is aimed instead of broad.
 * There is one cart per customer and no filters, so the hierarchy is as short as the profile's —
 * and it is still a factory, because `['cart', 'mine']` written by hand in two files is the cache
 * bug that shows up the day a third screen writes to it.
 */
export const cartKeys = {
  all: ['cart'] as const,
  mine: () => [...cartKeys.all, 'mine'] as const,
};

/**
 * The cart of the signed-in customer (RF-03), and the mutation that adds to it.
 *
 * **Why this lives in `shared/api/` and not in `features/cart/`**, which is where the screen is: the
 * bar counts the units of the cart, the product sheet adds to it and the cart screen shows it —
 * three consumers in two different features plus the shell, and features never import each other
 * (`docs/07-frontend-ux-ui.md`). It is the same move `session.ts`, `stores.ts` and `availability.ts`
 * already made, for the same reason. What only the cart screen uses — removing a line, emptying it —
 * stays in `features/cart/api/queries.ts`, hanging off this very factory.
 *
 * The read is only fired for a **customer**, which is stricter than "there is a session" and is the
 * condition that actually holds. `GET /api/cart` answers `401` to a visitor — and the bar is painted
 * on the catalogue, the home page and the stores, which are public, so asking anyway would bounce
 * every anonymous reader to the authorization server through the interceptor.
 *
 * It answers `403` to an **employee**, who is signed in and has no cart (`docs/02-requisitos.md` §3,
 * ADR-0006). That one is worse than a wasted request: every `403` writes an
 * `auth.authorization.denied` business event, so a back-office session used to file a denial into
 * the closed registry of `docs/11-observabilidad-y-logging.md` §3 on every page it painted — noise
 * in the one channel that exists to be signal, and rows in `audit_log` once that lands.
 *
 * Gating here and not in each caller is the point: the bar, the sheet's button and the cart screen
 * all read through this hook, and a condition copied into three files drifts in two of them.
 *
 * The role is read through `useAuth`, which is the one place in the app that reads roles, and the
 * reason this file reaches into `shared/hooks/` is the one `availability.ts` already writes down for
 * the same crossing: what is asked for depends on the session, so deciding it needs the session.
 */
export function useCart() {
  const { hasRole } = useAuth();

  return useQuery({
    queryKey: cartKeys.mine(),
    queryFn: ({ signal }) => http.get<Cart>('/api/cart', { signal }).then((response) => response.data),
    enabled: hasRole('CUSTOMER'),
    retry: retryUnlessClientError,
  });
}

type PutCartItem = {
  productId: number;
  /** The line's **total** quantity, never an increment: the endpoint is an idempotent upsert. */
  quantity: number;
};

/**
 * Adding a product or changing the quantity of its line — the same operation, because there is
 * exactly one line per `(customer, product)` (RF-03) and the endpoint is keyed on `productId`.
 *
 * The response is the **whole cart**, which the contract returns precisely so that nobody has to
 * recompute the total in the browser or chain a `GET` after writing. It is written into the cache
 * as the new state; the invalidation on top is not redundant — what is painted comes from the write,
 * and the background refetch is what confirms it against the server, which is also where `available`
 * may have moved in the meantime.
 */
export function usePutCartItem() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ productId, quantity }: PutCartItem) => {
      const body: UpdateCartItemRequest = { quantity };

      return http.put<Cart>(`/api/cart/items/${productId}`, body).then((response) => response.data);
    },
    onSuccess: async (cart) => {
      queryClient.setQueryData(cartKeys.mine(), cart);
      await queryClient.invalidateQueries({ queryKey: cartKeys.all });
    },
  });
}

/**
 * The line of a product, for whoever needs to know what is already in the cart before writing to it:
 * the sheet's button sends `quantity + 1` and not "one more", so it has to read the current one.
 */
export function cartLineOf(cart: Cart | undefined, productId: number): CartLine | undefined {
  return cart?.items.find((line) => line.productId === productId);
}

/** Units in the cart, which is what the bar counts — three of one product are three, not one line. */
export function cartUnitCount(cart: Cart | undefined): number {
  return cart?.items.reduce((units, line) => units + line.quantity, 0) ?? 0;
}
