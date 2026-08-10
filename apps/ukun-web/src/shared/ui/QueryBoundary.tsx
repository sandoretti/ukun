import type { ReactNode } from 'react';

import { EmptyState } from './EmptyState';
import { ErrorState } from './ErrorState';
import { LoadingState } from './LoadingState';

/**
 * The part of a TanStack Query result this needs. Typed structurally rather than as
 * `UseQueryResult` so a `useInfiniteQuery` — whose result carries a different `data` — fits the
 * same boundary without a cast.
 */
type QueryStatus = {
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

type QueryBoundaryProps<T> = {
  query: QueryStatus;
  /**
   * What success renders from. It travels apart from `query` because a paginated read hands over
   * its **flattened** list (`data.pages.flatMap(...)`), not the raw cache entry.
   */
  data: readonly T[] | undefined;
  /** Why the list is empty, in the words of the screen; falls back to the generic one. */
  empty?: string;
  children: (data: readonly T[]) => ReactNode;
};

/**
 * The four states every screen that shows data must cover — loading, error, empty, success — in one
 * place, which is where `docs/07-frontend-ux-ui.md §Estados` says they belong: not improvised as a
 * chain of ternaries inside each screen.
 *
 * The error state only takes over when there is **nothing** on screen. A read that fails after some
 * of its data already arrived — the catalog's next chunk — keeps what is there and reports the
 * failure next to whatever tried to load more: dropping 48 products to announce the 49th is worse
 * than the failure itself.
 */
export function QueryBoundary<T>({ query, data, empty, children }: QueryBoundaryProps<T>) {
  const items = data ?? [];

  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError && items.length === 0) {
    return <ErrorState error={query.error} />;
  }
  if (items.length === 0) {
    return <EmptyState message={empty} />;
  }

  return <>{children(items)}</>;
}
