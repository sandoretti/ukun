import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

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
 *
 * **Why this component has to remember.** That rule used to hold only for the infinite scroll, which
 * keeps its own pages, and it quietly broke on every paginated listing. The five of them ask for
 * `placeholderData: keepPreviousData` so the table survives a page change, but that placeholder is
 * only applied while the status is `pending` (`queryObserver`: `data === undefined && status ===
 * 'pending'`). The moment the next page *fails*, the status turns to `error`, the placeholder stops
 * being applied, `data` comes back `undefined` — and a boundary that only looked at `data` replaced
 * a full table with an error panel. What the reader lost was not the page that failed but the one
 * they were already reading.
 *
 * So the last list actually painted is kept here, in state adjusted during render — React's own
 * «adjusting state when a prop changes», not an effect and not a ref. A ref is not a rendering input
 * and must not be read while rendering; an effect that calls `setState` is the same update taken one
 * commit late, which here would paint the error panel first and the kept rows after it.
 *
 * It cannot loop, even though several callers rebuild their array on every render of *their* own
 * (`useStockView` joins two halves inline): the update re-renders this component alone, `data` is a
 * prop and therefore identical in that second pass, and the comparison then stops it.
 */
export function QueryBoundary<T>({ query, data, empty, children }: QueryBoundaryProps<T>) {
  const { t } = useTranslation('common');

  const [lastLoaded, setLastLoaded] = useState<readonly T[] | undefined>(data);
  if (data !== undefined && data !== lastLoaded) {
    setLastLoaded(data);
  }

  if (query.isPending) {
    return <LoadingState />;
  }

  if (query.isError) {
    const kept = data ?? lastLoaded ?? [];

    // Nothing on screen: the error is the screen.
    if (kept.length === 0) {
      return <ErrorState error={query.error} />;
    }

    // Something on screen: it stays, and the failure is counted beside it — the same shape the
    // catalog's «no se pudo cargar el siguiente bloque» already had at the foot of its grid.
    return (
      <>
        {children(kept)}
        <p className="mt-3 mb-0 text-body-secondary small" role="status">
          {t('list.refreshFailed')}
        </p>
      </>
    );
  }

  const items = data ?? [];
  if (items.length === 0) {
    return <EmptyState message={empty} />;
  }

  return <>{children(items)}</>;
}
