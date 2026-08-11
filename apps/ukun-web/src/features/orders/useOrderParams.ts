import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';

import type { OrderFilterInput, OrderStatus } from './api/orderKeys';

export const FIRST_PAGE = 1;

/** The history with nothing filtered, which is also what clearing the dates goes back to. */
export const NO_FILTERS: OrderFilterInput = { from: '', to: '', status: null };

const ORDER_STATUSES: OrderStatus[] = ['CONFIRMED'];

/** Which filters are set right now — what the "clear" button appears for. */
export function countActiveFilters(filters: OrderFilterInput): number {
  return Number(filters.from !== '') + Number(filters.to !== '') + Number(filters.status !== null);
}

function readDate(value: string | null): string {
  // The contract's `format: date`; anything else is dropped rather than sent to the API.
  return value !== null && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : '';
}

function readPositiveInteger(value: string | null): number | null {
  if (value === null || !/^\d+$/.test(value)) {
    return null;
  }
  return Number(value);
}

function readFilters(searchParams: URLSearchParams): OrderFilterInput {
  const status = searchParams.get('status');

  return {
    from: readDate(searchParams.get('from')),
    to: readDate(searchParams.get('to')),
    // Read but not offered: `OrderStatus` has a single value today, and a select with one option is
    // a control that decides nothing. The URL still carries it so a link that filters keeps working
    // the day the enum grows (`docs/10-modelo-de-datos.md` §3).
    status: ORDER_STATUSES.includes(status as OrderStatus) ? (status as OrderStatus) : null,
  };
}

/**
 * The page the URL is on, counting from one. The API counts from zero and that is where it stays:
 * `?page=1` meaning the second page is a trap for anyone editing the address bar, so the translation
 * happens here, once. Anything that is not a page — `0`, `-3`, `abc` — is the first.
 */
function readPage(searchParams: URLSearchParams): number {
  return Math.max(FIRST_PAGE, readPositiveInteger(searchParams.get('page')) ?? FIRST_PAGE);
}

function writeParams(filters: OrderFilterInput, page: number): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.from) params.set('from', filters.from);
  if (filters.to) params.set('to', filters.to);
  if (filters.status !== null) params.set('status', filters.status);
  if (page > FIRST_PAGE) params.set('page', String(page));
  return params;
}

/**
 * The history's state lives in the URL and not in a `useState` (`docs/07-frontend-ux-ui.md`): that is
 * what makes a filtered range linkable, survives a reload, and makes the back button do what the
 * reader expects. Changing a filter goes back to the first page — staying on page 4 of a result set
 * that no longer has one is how a listing shows an empty screen for no reason.
 */
export function useOrderParams() {
  const [searchParams, setSearchParams] = useSearchParams();

  const filters = useMemo(() => readFilters(searchParams), [searchParams]);
  const page = readPage(searchParams);

  const updateFilters = useCallback(
    (patch: Partial<OrderFilterInput>) =>
      setSearchParams(writeParams({ ...filters, ...patch }, FIRST_PAGE)),
    [filters, setSearchParams],
  );

  const clearFilters = useCallback(() => setSearchParams(new URLSearchParams()), [setSearchParams]);

  const goToPage = useCallback(
    (next: number) => {
      // After reading a whole page the reader is at the bottom of the document; landing halfway down
      // the next one is how a listing loses whoever was reading it. Instant, not smooth: a long
      // animated jump is what `prefers-reduced-motion` asks not to do.
      window.scrollTo({ top: 0 });
      setSearchParams(writeParams(filters, Math.max(FIRST_PAGE, next)));
    },
    [filters, setSearchParams],
  );

  return { filters, page, updateFilters, clearFilters, goToPage };
}
