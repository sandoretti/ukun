import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';

import { readPositiveInteger } from '../../../shared/browser/searchParams';
import { FIRST_PAGE } from '../routes';
import type { EmployeeFilterInput } from './api/employeeKeys';
import { employeesSearch } from './routes';

/** The listing with nothing filtered, which is also what clearing goes back to. */
export const NO_FILTERS: EmployeeFilterInput = { q: '', isAdmin: null };

/** How many filters are set right now — what the "clear" button appears for. */
export function countActiveFilters(filters: EmployeeFilterInput): number {
  return Number(filters.q !== '') + Number(filters.isAdmin !== null);
}

function readTerm(value: string | null): string {
  return (value ?? '').trim();
}

/**
 * The administrator filter, read back from the URL. Only the two words the contract accepts count as
 * a filter; anything else —a typo, an empty `?isAdmin=`— is nobody filtered rather than a request
 * the API would answer with a `400`.
 */
function readIsAdmin(value: string | null): boolean | null {
  if (value === 'true') {
    return true;
  }
  if (value === 'false') {
    return false;
  }
  return null;
}

function readFilters(searchParams: URLSearchParams): EmployeeFilterInput {
  return {
    q: readTerm(searchParams.get('q')),
    isAdmin: readIsAdmin(searchParams.get('isAdmin')),
  };
}

/**
 * The employee listing's state lives in the URL and not in a `useState` (`docs/07-frontend-ux-ui.md`):
 * that is what makes a filtered table linkable —which is how the alta points at the employee it just
 * created— survives a reload, and makes the back button do what the reader expects.
 *
 * Changing a filter goes back to the first page: staying on page 4 of a result that no longer has one
 * is how a listing shows an empty screen for no reason.
 */
export function useEmployeeParams() {
  const [searchParams, setSearchParams] = useSearchParams();

  const filters = useMemo(() => readFilters(searchParams), [searchParams]);
  const page = Math.max(FIRST_PAGE, readPositiveInteger(searchParams.get('page')) ?? FIRST_PAGE);

  const updateFilters = useCallback(
    (patch: Partial<EmployeeFilterInput>) =>
      setSearchParams(employeesSearch({ ...filters, ...patch })),
    [filters, setSearchParams],
  );

  const clearFilters = useCallback(
    () => setSearchParams(employeesSearch(NO_FILTERS)),
    [setSearchParams],
  );

  const goToPage = useCallback(
    (next: number) => {
      // After reading a whole page the reader is at the bottom of the document; landing halfway down
      // the next one is how a listing loses whoever was reading it.
      window.scrollTo({ top: 0 });
      setSearchParams(employeesSearch(filters, Math.max(FIRST_PAGE, next)));
    },
    [filters, setSearchParams],
  );

  return { filters, page, updateFilters, clearFilters, goToPage };
}
