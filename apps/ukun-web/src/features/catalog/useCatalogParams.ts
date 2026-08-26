import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';

import { readPositiveInteger } from '../../shared/browser/searchParams';
import { MONEY_PATTERN } from '../../shared/format/money';
import type { CatalogFilterInput, ProductSort } from './api/productKeys';

export const DEFAULT_SORT: ProductSort = 'PRICE_ASC';
export const FIRST_PAGE = 1;
export const SORT_OPTIONS: ProductSort[] = ['PRICE_ASC', 'PRICE_DESC'];

/** The listing with nothing filtered: also what removing one single filter resets it to. */
const NO_FILTERS: CatalogFilterInput = {
  name: '',
  typeId: null,
  priceMin: '',
  priceMax: '',
  sort: DEFAULT_SORT,
};

/** Which filters are set right now — the number the mobile "Filters" button carries. */
export function countActiveFilters(criteria: CatalogFilterInput): number {
  return (
    Number(criteria.name !== '') +
    Number(criteria.typeId !== null) +
    Number(criteria.priceMin !== '') +
    Number(criteria.priceMax !== '')
  );
}

function readAmount(value: string | null): string {
  // The shape of `Money` in the contract; anything else is dropped rather than sent to the API.
  return value !== null && MONEY_PATTERN.test(value) ? value : '';
}

function readCriteria(searchParams: URLSearchParams): CatalogFilterInput {
  const sort = searchParams.get('sort');
  return {
    name: searchParams.get('name')?.trim() ?? '',
    typeId: readPositiveInteger(searchParams.get('typeId')),
    priceMin: readAmount(searchParams.get('priceMin')),
    priceMax: readAmount(searchParams.get('priceMax')),
    sort: SORT_OPTIONS.includes(sort as ProductSort) ? (sort as ProductSort) : DEFAULT_SORT,
  };
}

/**
 * The page the URL is on, counting from one. The API counts from zero and that is where it stays:
 * `?page=1` meaning the second page is a trap for anyone editing the address bar, so the translation
 * happens once, in `api/queries.ts`. Anything that is not a page — `0`, `-3`, `abc` — is the first.
 */
function readPage(searchParams: URLSearchParams): number {
  return Math.max(FIRST_PAGE, readPositiveInteger(searchParams.get('page')) ?? FIRST_PAGE);
}

function writeParams(criteria: CatalogFilterInput, page: number): URLSearchParams {
  const params = new URLSearchParams();
  if (criteria.name.trim()) params.set('name', criteria.name.trim());
  if (criteria.typeId !== null) params.set('typeId', String(criteria.typeId));
  if (criteria.priceMin) params.set('priceMin', criteria.priceMin);
  if (criteria.priceMax) params.set('priceMax', criteria.priceMax);
  if (criteria.sort !== DEFAULT_SORT) params.set('sort', criteria.sort);
  if (page > FIRST_PAGE) params.set('page', String(page));
  return params;
}

/**
 * The listing's state lives in the URL, not in a `useState` (`docs/07-frontend-ux-ui.md`): that is
 * what makes a result linkable, survives a reload, and makes the back button do what the user
 * expects. Changing any filter goes back to the first page — staying on page 7 of a result set that
 * no longer has one is how a listing shows an empty screen for no reason.
 *
 * How many chunks of that page have been loaded is deliberately *not* in the URL: it is progress
 * through a page, not a different result, and writing it there would fill the history with entries
 * that all show the same thing.
 */
export function useCatalogParams() {
  const [searchParams, setSearchParams] = useSearchParams();

  const criteria = useMemo(() => readCriteria(searchParams), [searchParams]);
  const page = readPage(searchParams);

  // The sidebar changes one filter at a time and applies on the spot, so what it hands over is a
  // patch over what the URL already says — not the whole form, which no longer exists as such.
  const updateFilters = useCallback(
    (patch: Partial<CatalogFilterInput>) =>
      setSearchParams(writeParams({ ...criteria, ...patch }, FIRST_PAGE)),
    [criteria, setSearchParams],
  );

  const removeFilter = useCallback(
    <K extends keyof CatalogFilterInput>(key: K) => updateFilters({ [key]: NO_FILTERS[key] } as Pick<CatalogFilterInput, K>),
    [updateFilters],
  );

  const clearFilters = useCallback(() => setSearchParams(new URLSearchParams()), [setSearchParams]);

  const goToPage = useCallback(
    (next: number) => {
      // After scrolling through a full page the reader is at the bottom of the document; landing
      // halfway down the next one is how a listing loses whoever was reading it. Instant, not
      // smooth: a long animated jump is exactly what `prefers-reduced-motion` asks not to do.
      window.scrollTo({ top: 0 });
      setSearchParams(writeParams(criteria, Math.max(FIRST_PAGE, next)));
    },
    [criteria, setSearchParams],
  );

  return { criteria, page, updateFilters, removeFilter, clearFilters, goToPage };
}
