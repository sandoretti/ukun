import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { cartKeys } from '../../../shared/api/cart';
import { http } from '../../../shared/api/http';
import { retryUnlessClientError } from '../../../shared/api/retry';
import {
  orderKeys,
  type CheckoutAttempt,
  type Order,
  type OrderFilters,
  type OrderPage,
} from './orderKeys';

/**
 * The checkout attempt of this screen (ADR-0016): the server opens it, freezes the unit prices,
 * computes the total and hands back the `attemptId` that will travel as `Idempotency-Key` when the
 * order is confirmed. **The browser never invents that key** — that is the whole point of the ADR,
 * and it is why nothing here generates a UUID.
 *
 * **Why a `useQuery` around a `POST`.** What the screen needs is "an attempt exists for me", which
 * is a state to read and to cover with the four states of a boundary, not an action the reader
 * takes. The endpoint is a `POST` because it creates one, and it is deliberately not idempotent —
 * asking twice opens two attempts and neither of them charges anybody. What must not happen is
 * *silently* asking again, so every automatic refetch is off: with `staleTime: Infinity` and the
 * three `refetchOn*` disabled, one mount is one attempt, and coming back to the screen reuses the
 * one already in the cache instead of opening a fresh one.
 *
 * Reopening is therefore always explicit — `reopenCheckout` below — which is exactly what a
 * `410 CHECKOUT_EXPIRED` asks the frontend to do.
 */
export function useCheckoutAttempt() {
  return useQuery({
    queryKey: orderKeys.attempt(),
    queryFn: ({ signal }) =>
      http.post<CheckoutAttempt>('/api/checkout/attempts', undefined, { signal }).then((response) => response.data),
    staleTime: Infinity,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    // An `EMPTY_CART` does not become a full cart by asking again, and a 5xx here is cheap to retry
    // by hand from a screen that has a button for it.
    retry: false,
  });
}

/**
 * Throwing away the attempt on screen so the next render opens a new one. It is what the contract
 * asks for after a `410 CHECKOUT_EXPIRED` —"the frontend must reopen it"— and also the way out of an
 * `IDEMPOTENCY_MISMATCH`, whose key was already used against a different cart.
 */
export function useReopenCheckout() {
  const queryClient = useQueryClient();

  return () => queryClient.resetQueries({ queryKey: orderKeys.attempt() });
}

/**
 * Confirming the order (RF-04, ADR-0016). **It carries no body**: the attempt is the request, and the
 * `Idempotency-Key` header —whose value is the `attemptId`, verbatim— is what identifies it.
 *
 * It is not retried automatically, and that is not because a retry would be unsafe: the server makes
 * it safe. It is because a repeat while the first one is still in flight answers
 * `409 IDEMPOTENCY_IN_PROGRESS`, which is a worse thing to show than the original failure.
 *
 * On success the cart is empty **by contract**, so it is invalidated rather than recomputed here, and
 * the created order is written into the cache under its own key: the confirmation screen the reader
 * lands on next reads that key and paints immediately instead of asking for what just arrived.
 */
export function usePlaceOrder() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (attemptId: string) =>
      http
        .post<Order>('/api/orders', undefined, { headers: { 'Idempotency-Key': attemptId } })
        .then((response) => response.data),
    onSuccess: async (order) => {
      queryClient.setQueryData(orderKeys.detail(order.id), order);
      queryClient.removeQueries({ queryKey: orderKeys.attempt() });

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: orderKeys.lists() }),
        queryClient.invalidateQueries({ queryKey: cartKeys.all }),
      ]);
    },
  });
}

function toQueryParams(filters: OrderFilters) {
  return {
    ...(filters.from ? { from: filters.from } : {}),
    ...(filters.to ? { to: filters.to } : {}),
    ...(filters.status !== null ? { status: filters.status } : {}),
    page: filters.page,
    size: filters.size,
  };
}

/**
 * The customer's order history (RF-04, query Q3), most recent first, filtered and paged server-side.
 * The page number that travels here already counts from zero — the URL counts from one and the
 * translation happens in `useOrderParams`, once.
 *
 * `keepPreviousData` keeps the page on screen while the next one loads instead of collapsing the
 * list on every page change (`docs/07-frontend-ux-ui.md`).
 */
export function useOrders(filters: OrderFilters) {
  return useQuery<OrderPage>({
    queryKey: orderKeys.list(filters),
    queryFn: ({ signal }) =>
      http
        .get<OrderPage>('/api/orders', { params: toQueryParams(filters), signal })
        .then((response) => response.data),
    placeholderData: keepPreviousData,
    retry: retryUnlessClientError,
  });
}

/**
 * One order with its lines and its immutable snapshots (ADR-0011). Someone else's order answers
 * `404` and not `403` on purpose (`docs/08-identidad-y-seguridad.md` §4): the id in the path
 * identifies the order, it does not authorise reading it.
 */
export function useOrder(id: number) {
  return useQuery({
    queryKey: orderKeys.detail(id),
    queryFn: ({ signal }) =>
      http.get<Order>(`/api/orders/${id}`, { signal }).then((response) => response.data),
    retry: retryUnlessClientError,
  });
}
