import type { components } from '../../../types/api';

export type CheckoutAttempt = components['schemas']['CheckoutAttempt'];
export type CheckoutAttemptLine = components['schemas']['CheckoutAttemptLine'];
export type Order = components['schemas']['OrderResponse'];
export type OrderLine = components['schemas']['OrderLine'];
export type OrderPage = components['schemas']['OrderPage'];
export type OrderStatus = components['schemas']['OrderStatus'];

/** What the history is filtered by — the part of the URL that is not the page. */
export type OrderFilterInput = {
  from: string;
  to: string;
  status: OrderStatus | null;
};

/** One request to `GET /api/orders`: the filters plus the API's own slice, `page` from zero. */
export type OrderFilters = OrderFilterInput & {
  page: number;
  size: number;
};

/** How many orders a page of the history holds. Whole rows of cards, and one request per page. */
export const PAGE_SIZE = 10;

/**
 * Query keys of the orders, hierarchical so invalidating can be targeted by prefix instead of
 * wiping the whole cache. Never write one of these arrays by hand inside a component.
 *
 * The **attempt** hangs here too, under a branch of its own. It is not an order and it is never
 * listed, but it belongs to the same cache neighbourhood: placing an order ends the attempt, and
 * that removal and the invalidation of the lists are the same line of code.
 */
export const orderKeys = {
  all: ['orders'] as const,
  lists: () => [...orderKeys.all, 'list'] as const,
  list: (filters: OrderFilters) => [...orderKeys.lists(), filters] as const,
  details: () => [...orderKeys.all, 'detail'] as const,
  detail: (id: number) => [...orderKeys.details(), id] as const,
  attempt: () => [...orderKeys.all, 'attempt'] as const,
};
