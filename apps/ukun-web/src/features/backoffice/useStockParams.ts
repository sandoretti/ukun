import { useCallback, useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';

import type { StockFilterInput, StockStatus } from './api/stockKeys';
import { DEFAULT_STATUS, FIRST_PAGE, stockPath, stockSearch } from './routes';
import { readSku } from './stockSchema';

const STATUSES: StockStatus[] = ['STOCKED', 'NOT_STOCKED'];

/** The listing with nothing filtered, which is also what clearing goes back to. */
export const NO_FILTERS: StockFilterInput = { status: DEFAULT_STATUS, sku: '', q: '' };

/**
 * How many filters are set right now — what the "clear" button appears for.
 *
 * **The tab does not count.** Switching between the stock and the products without a row is choosing
 * what to look at, not narrowing it down, so a screen with nothing filtered has nothing to clear
 * whichever tab it is on.
 */
export function countActiveFilters(filters: StockFilterInput): number {
  return Number(filters.sku !== '') + Number(filters.q !== '');
}

function readTerm(value: string | null): string {
  return (value ?? '').trim();
}

function readStatus(value: string | null): StockStatus {
  return STATUSES.includes(value as StockStatus) ? (value as StockStatus) : DEFAULT_STATUS;
}

function readPositiveInteger(value: string | null): number | null {
  if (value === null || !/^\d+$/.test(value)) {
    return null;
  }
  return Number(value);
}

/**
 * The page the URL is on, counting from one. The API counts from zero and that is where it stays:
 * `?page=1` meaning the second page is a trap for anyone editing the address bar, so the translation
 * happens in the screen, once. Anything that is not a page — `0`, `-3`, `abc` — is the first.
 */
function readPage(searchParams: URLSearchParams): number {
  return Math.max(FIRST_PAGE, readPositiveInteger(searchParams.get('page')) ?? FIRST_PAGE);
}

function readFilters(searchParams: URLSearchParams): StockFilterInput {
  return {
    status: readStatus(searchParams.get('status')),
    sku: readSku(searchParams.get('sku')),
    q: readTerm(searchParams.get('q')),
  };
}

/**
 * The stock listing's state lives in the URL and not in a `useState` (`docs/07-frontend-ux-ui.md`):
 * that is what makes a filtered table linkable —which is how the add screen points at the line it
 * just created, and at the one that already existed— survives a reload, and makes the back button do
 * what the reader expects.
 *
 * Changing a filter goes back to the first page: staying on page 4 of a result that no longer has one
 * is how a listing shows an empty screen for no reason.
 */
export function useStockParams() {
  const [searchParams, setSearchParams] = useSearchParams();

  const filters = useMemo(() => readFilters(searchParams), [searchParams]);
  const page = readPage(searchParams);

  const updateFilters = useCallback(
    (patch: Partial<StockFilterInput>) => setSearchParams(stockSearch({ ...filters, ...patch })),
    [filters, setSearchParams],
  );

  /**
   * Clearing keeps the tab. Everywhere else in the app clearing is an empty query string, but here
   * that would drop someone from the "not stocked" list back into the stock — which is not clearing a
   * filter, it is changing screen.
   */
  const clearFilters = useCallback(
    () => setSearchParams(stockSearch({ ...NO_FILTERS, status: filters.status })),
    [filters.status, setSearchParams],
  );

  const goToPage = useCallback(
    (next: number) => {
      // After reading a whole page the reader is at the bottom of the document; landing halfway down
      // the next one is how a listing loses whoever was reading it.
      window.scrollTo({ top: 0 });
      setSearchParams(stockSearch(filters, Math.max(FIRST_PAGE, next)));
    },
    [filters, setSearchParams],
  );

  /**
   * Where the other tab lives, as a link. The switch is navigation and not a button with state: that
   * is what makes each half addressable, keeps the back button honest, and leaves nothing to keep in
   * sync. It carries the filters over —the same SKU search makes sense on both sides— and drops the
   * page, because page 3 of one half says nothing about the other.
   */
  const hrefForStatus = useCallback(
    (status: StockStatus) => stockPath({ ...filters, status }),
    [filters],
  );

  return { filters, page, updateFilters, clearFilters, goToPage, hrefForStatus };
}

/**
 * Pulls the reader back onto a page that exists when the one they are on stops existing.
 *
 * The case that makes it necessary is deleting the last line of the last page: the answer comes back
 * empty and the pagination would read "page 3 of 2" over a table with nothing in it — an emptiness
 * the reader did not ask for. Hand-editing `?page=` out of range lands in the same place, and gets
 * the same correction.
 *
 * It waits for a page it can trust: `totalPages` is only read once a page has actually arrived, so a
 * read still in flight never moves anybody.
 */
export function usePageWithinRange(
  page: number,
  totalPages: number | undefined,
  goToPage: (page: number) => void,
) {
  useEffect(() => {
    if (totalPages === undefined) {
      return;
    }

    const lastPage = Math.max(FIRST_PAGE, totalPages);

    if (page > lastPage) {
      goToPage(lastPage);
    }
  }, [page, totalPages, goToPage]);
}
