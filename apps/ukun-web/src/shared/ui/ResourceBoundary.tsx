import type { ReactNode } from 'react';

import { EmptyState } from './EmptyState';
import { ErrorState } from './ErrorState';
import { LoadingState } from './LoadingState';

/** The part of a TanStack Query result this needs, typed structurally like `QueryBoundary`'s. */
type QueryStatus = {
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

type ResourceBoundaryProps<T> = {
  query: QueryStatus;
  /** The resource, once it is there. `null` counts as nothing to show, not as a value. */
  data: T | null | undefined;
  /** What "there is nothing here" says, in the words of the screen. */
  empty?: string;
  children: (data: T) => ReactNode;
};

/**
 * The four states of a screen that reads **one** resource — the profile, an order, a cart — where
 * `QueryBoundary` reads a list. `docs/07-frontend-ux-ui.md` §Estados foresees this case when it says
 * `LoadingState`, `EmptyState` and `ErrorState` still exist loose for what is not a list; this is
 * that chain written once instead of re-improvised per screen.
 *
 * Same non-negotiable rule as its sibling: the error state only takes over when there is nothing on
 * screen. A refetch that fails over a resource already painted keeps what is there.
 */
export function ResourceBoundary<T>({ query, data, empty, children }: ResourceBoundaryProps<T>) {
  if (data !== undefined && data !== null) {
    return <>{children(data)}</>;
  }
  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} />;
  }

  return <EmptyState message={empty} />;
}
