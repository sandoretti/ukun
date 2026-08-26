import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { availabilityKeys } from '../../../shared/api/availability';
import { http, toProblem } from '../../../shared/api/http';
import { retryUnlessClientError } from '../../../shared/api/retry';
import type { components } from '../../../types/api';
import {
  isStocked,
  isUnstocked,
  SKU_PATTERN,
  type StockView,
  stockKeys,
  skuLookupKeys,
  type CreateStockLineRequest,
  type StockedLine,
  type StockedLinePage,
  type StockFilters,
  type UnstockedCandidatePage,
  type UpdateStockLineRequest,
} from './stockKeys';

type StockLinePage = components['schemas']['StockLinePage'];

/**
 * The product sheet as the catalog serves it. Typed from the contract here rather than imported from
 * `features/catalog`: features never import each other, and this is one type alias, not shared code.
 */
export type ProductDetail = components['schemas']['ProductDetail'];

/** What travels to the API. `status` is always explicit — the default is the contract's, not ours. */
function toQueryParams(filters: StockFilters) {
  return {
    status: filters.status,
    ...(filters.sku ? { sku: filters.sku } : {}),
    ...(filters.q ? { q: filters.q } : {}),
    page: filters.page,
    size: filters.size,
  };
}

function fetchStock(storeId: number, filters: StockFilters, signal: AbortSignal) {
  return http
    .get<StockLinePage>(`/api/stores/${storeId}/stock`, { params: toQueryParams(filters), signal })
    .then((response) => response.data);
}

/**
 * The stock of the store, page by page (RF-09, query Q5). The page number that arrives here already
 * counts from zero: the URL counts from one and the translation happens in `useStockParams`, once.
 *
 * `keepPreviousData` keeps the table on screen while the next page loads instead of collapsing it on
 * every page change (`docs/07-frontend-ux-ui.md`) — and it is also what lets the edit dialog keep
 * showing a row while the list behind it is being re-read after a version conflict.
 */
export function useStockedLines(
  storeId: number,
  filters: Omit<StockFilters, 'status'>,
  enabled = true,
) {
  const withStatus: StockFilters = { ...filters, status: 'STOCKED' };

  return useQuery({
    queryKey: stockKeys.list(storeId, withStatus),
    queryFn: ({ signal }) => fetchStock(storeId, withStatus, signal),
    enabled,
    placeholderData: keepPreviousData,
    retry: retryUnlessClientError,
    // `STOCKED` promises the three fields, so this filter never drops a row. It is here so the type
    // says what the contract already says, and so an anomalous row costs one missing line instead of
    // a screen that throws.
    select: (page): StockedLinePage => ({ ...page, items: page.items.filter(isStocked) }),
  });
}

/**
 * The other half of RF-09: catalog products with **no** stock row in this store — the "add new stock"
 * screen. Same endpoint and same shape, another `status`; a product with no row counts as not
 * managed in this store, which is not the same thing as a quantity of zero.
 */
export function useUnstockedCandidates(
  storeId: number,
  filters: Omit<StockFilters, 'status'>,
  enabled = true,
) {
  const withStatus: StockFilters = { ...filters, status: 'NOT_STOCKED' };

  return useQuery({
    queryKey: stockKeys.list(storeId, withStatus),
    queryFn: ({ signal }) => fetchStock(storeId, withStatus, signal),
    enabled,
    placeholderData: keepPreviousData,
    retry: retryUnlessClientError,
    select: (page): UnstockedCandidatePage => ({ ...page, items: page.items.filter(isUnstocked) }),
  });
}

/**
 * A product resolved by its SKU, the step before creating a stock line (ADR-0015).
 *
 * It only asks once the SKU matches the contract's own pattern: a half-typed SKU is not a request
 * the server should have to reject. And it does not retry, because **a `404` here is the right
 * answer, not a failure**: this path resolves active products only, so a discontinued one comes back
 * `404` and that is exactly what stops an employee from restocking something pulled from the catalog
 * (ADR-0013). Which of the two it is, and how to say it, is the form's decision — not this hook's.
 */
export function useProductBySku(sku: string | undefined) {
  const candidate = sku ?? '';

  return useQuery({
    queryKey: skuLookupKeys.sku(candidate),
    queryFn: ({ signal }) =>
      http
        .get<ProductDetail>(`/api/products/by-sku/${encodeURIComponent(candidate)}`, { signal })
        .then((response) => response.data),
    enabled: SKU_PATTERN.test(candidate),
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}

/**
 * What every write to this store's stock has to refresh: both halves of the listing —creating a line
 * moves a product from one tab to the other— and the availability the public catalog reads for this
 * store, which is a projection of exactly these numbers.
 */
function useInvalidateStock(storeId: number) {
  const queryClient = useQueryClient();

  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: stockKeys.lists(storeId) }),
      queryClient.invalidateQueries({ queryKey: availabilityKeys.byStore(storeId) }),
    ]);
}

/**
 * Creating a line **by SKU**, not by internal identifier (ADR-0015).
 *
 * A `409 REFERENTIAL_INTEGRITY` means the product already has a line in this store (`ux_store_stock`):
 * the way out on screen is to go and edit the existing one, never to try again. The screen decides
 * that; this hook only reports it.
 */
export function useCreateStockLine(storeId: number) {
  const invalidate = useInvalidateStock(storeId);

  return useMutation({
    // `StockedLine` and not the contract's `StockLine`: a line that was just created has a row, so
    // its three nullable fields are filled in. Same assertion every `http.get<T>` in the app makes
    // about the shape it is handed, kept in one place so no component has to make it.
    mutationFn: (request: CreateStockLineRequest) =>
      http
        .post<StockedLine>(`/api/stores/${storeId}/stock`, request)
        .then((response) => response.data),
    onSuccess: invalidate,
  });
}

export type UpdateStockLineVariables = UpdateStockLineRequest & { lineId: number };

/**
 * Changing the quantity of a line, with the optimistic concurrency of ADR-0008: the `version` that
 * was read travels back, and a line someone else touched in between answers `409 VERSION_CONFLICT`.
 *
 * **The conflict invalidates too**, and that is the whole mechanism behind the dialog's conflict
 * state: a 409 is proof that what is on screen is stale, so the re-read is fired by the mutation
 * itself and the dialog gets the fresh row through its props. There is no `GET` for a single line in
 * the contract, so the listing is where a fresh line comes from.
 *
 * Nothing retries. A write that lost a race does not win it by being sent again — that is precisely
 * the overwrite optimistic locking exists to prevent.
 */
export function useUpdateStockLine(storeId: number) {
  const invalidate = useInvalidateStock(storeId);

  return useMutation({
    mutationFn: ({ lineId, ...request }: UpdateStockLineVariables) =>
      http
        .put<StockedLine>(`/api/stores/${storeId}/stock/${lineId}`, request)
        .then((response) => response.data),
    onSuccess: invalidate,
    onError: async (error) => {
      if (toProblem(error).code === 'VERSION_CONFLICT') {
        await invalidate();
      }
    },
  });
}

/**
 * Deleting the line. **This is not setting the quantity to zero** (RF-09): zero leaves the row in
 * place and the product still counts as managed in this store, while this removes it and sends the
 * product back to the "not stocked" list. The dialog that asks says so in those words.
 *
 * `204` and no body, so there is nothing to write into the cache: the new state comes from the
 * re-read the invalidation triggers.
 */
export function useDeleteStockLine(storeId: number) {
  const invalidate = useInvalidateStock(storeId);

  return useMutation({
    mutationFn: async (lineId: number) => {
      await http.delete(`/api/stores/${storeId}/stock/${lineId}`);
    },
    onSuccess: invalidate,
  });
}

/**
 * The whole store in one table: the lines it holds **and** the catalog products with no row here yet.
 *
 * **The contract has no such answer, and this is the cost of asking for it anyway.** `status` is
 * `STOCKED` or `NOT_STOCKED` — two queries over two different shapes — so the crossed view is two
 * requests joined in the browser, and the join is honest only in what it counts:
 *
 * - `totalElements` is the **sum**, which is the real number of things in the store.
 * - `totalPages` is the **larger of the two**, and that is where the seam shows: page 1 holds up to
 *   forty rows (twenty of each) and a page past the shorter half shows only the longer one. There is
 *   no honest way to paginate the union of two independently paginated answers from the client.
 *
 * The alternative —a third value of `status` served by one query— is a change to a contract frozen
 * at SINC-1, and it is written up in its own issue. Until then this is a deliberate compromise, not
 * an oversight: it was decided with the trade-off on the table.
 *
 * A read that fails is reported by the first half that failed; a half that is not asked for
 * (`enabled: false`) never makes the whole thing pending.
 */
export function useStockView(
  storeId: number,
  view: StockView,
  filters: Omit<StockFilters, 'status'>,
) {
  const wantsStocked = view !== 'NOT_STOCKED';
  const wantsUnstocked = view !== 'STOCKED';

  const stocked = useStockedLines(storeId, filters, wantsStocked);
  const unstocked = useUnstockedCandidates(storeId, filters, wantsUnstocked);

  const halves = [
    ...(wantsStocked ? [stocked] : []),
    ...(wantsUnstocked ? [unstocked] : []),
  ];

  const answered = halves.every((half) => half.data !== undefined);

  return {
    // Stocked first, always: what the store has comes before what it could have.
    items: answered
      ? [
          ...(wantsStocked ? (stocked.data?.items ?? []) : []),
          ...(wantsUnstocked ? (unstocked.data?.items ?? []) : []),
        ]
      : undefined,
    totalElements: answered
      ? halves.reduce((total, half) => total + (half.data?.totalElements ?? 0), 0)
      : undefined,
    totalPages: answered
      ? Math.max(...halves.map((half) => half.data?.totalPages ?? 0))
      : undefined,
    isPending: halves.some((half) => half.isPending),
    isError: halves.some((half) => half.isError),
    error: halves.find((half) => half.isError)?.error ?? null,
    isFetching: halves.some((half) => half.isFetching),
  };
}
