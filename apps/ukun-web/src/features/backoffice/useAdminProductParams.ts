import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';

import { readPositiveInteger } from '../../shared/browser/searchParams';
import { adminProductsSearch, DEFAULT_SORT, DEFAULT_STATUS } from './adminRoutes';
import {
  type AdminProductFilterInput,
  type AdminProductSort,
  type ProductStatusFilter,
} from './api/adminProductKeys';
import { FIRST_PAGE } from './routes';
import { readSku } from './stockSchema';

const STATUSES: ProductStatusFilter[] = ['ACTIVE', 'DISCONTINUED', 'ALL'];

const SORTS: AdminProductSort[] = ['NAME_ASC', 'NAME_DESC', 'PRICE_ASC', 'PRICE_DESC', 'UPDATED_DESC'];

/** The listing with nothing filtered, which is also what clearing goes back to. */
export const NO_FILTERS: AdminProductFilterInput = {
  q: '',
  sku: '',
  typeId: null,
  status: DEFAULT_STATUS,
  sort: DEFAULT_SORT,
};

/**
 * How many filters are set right now — what the "clear" button appears for.
 *
 * **The sort order does not count**, and neither does a `status` left at its default: choosing how
 * the table is ordered is not narrowing it down. The status does count when it is not the default,
 * because looking at the discontinued products *is* narrowing —and it is the filter someone is most
 * likely to forget they left on.
 */
export function countActiveFilters(filters: AdminProductFilterInput): number {
  return (
    Number(filters.q !== '') +
    Number(filters.sku !== '') +
    Number(filters.typeId !== null) +
    Number(filters.status !== DEFAULT_STATUS)
  );
}

function readTerm(value: string | null): string {
  return (value ?? '').trim();
}

function readStatus(value: string | null): ProductStatusFilter {
  return STATUSES.includes(value as ProductStatusFilter) ? (value as ProductStatusFilter) : DEFAULT_STATUS;
}

function readSort(value: string | null): AdminProductSort {
  return SORTS.includes(value as AdminProductSort) ? (value as AdminProductSort) : DEFAULT_SORT;
}

/**
 * The page the URL is on, counting from one. Anything that is not a page — `0`, `-3`, `abc` — is the
 * first, and the translation to the API's zero happens once, in `toApiFilters`.
 */
function readPage(searchParams: URLSearchParams): number {
  return Math.max(FIRST_PAGE, readPositiveInteger(searchParams.get('page')) ?? FIRST_PAGE);
}

function readFilters(searchParams: URLSearchParams): AdminProductFilterInput {
  return {
    q: readTerm(searchParams.get('q')),
    sku: readSku(searchParams.get('sku')),
    typeId: readPositiveInteger(searchParams.get('typeId')),
    status: readStatus(searchParams.get('status')),
    sort: readSort(searchParams.get('sort')),
  };
}

/**
 * The catalog listing's state lives in the URL and not in a `useState`
 * (`docs/07-frontend-ux-ui.md`): that is what makes a filtered table linkable —which is how the add
 * screen points at the product it just created, and at the one whose SKU was already taken— survives
 * a reload, and makes the back button do what the reader expects.
 *
 * Changing a filter goes back to the first page: staying on page 4 of a result that no longer has one
 * is how a listing shows an empty screen for no reason. Changing the **sort order** does the same, and
 * for the same reason — the fourth page of one ordering has nothing to do with the fourth page of
 * another.
 */
export function useAdminProductParams() {
  const [searchParams, setSearchParams] = useSearchParams();

  const filters = useMemo(() => readFilters(searchParams), [searchParams]);
  const page = readPage(searchParams);

  const updateFilters = useCallback(
    (patch: Partial<AdminProductFilterInput>) =>
      setSearchParams(adminProductsSearch({ ...filters, ...patch })),
    [filters, setSearchParams],
  );

  const clearFilters = useCallback(
    // The sort order survives clearing: it is not one of the filters being cleared, and resetting it
    // would rearrange a table the reader never asked to rearrange.
    () => setSearchParams(adminProductsSearch({ ...NO_FILTERS, sort: filters.sort })),
    [filters.sort, setSearchParams],
  );

  const goToPage = useCallback(
    (next: number) => {
      // After reading a whole page the reader is at the bottom of the document; landing halfway down
      // the next one is how a listing loses whoever was reading it.
      window.scrollTo({ top: 0 });
      setSearchParams(adminProductsSearch(filters, Math.max(FIRST_PAGE, next)));
    },
    [filters, setSearchParams],
  );

  return { filters, page, updateFilters, clearFilters, goToPage };
}
