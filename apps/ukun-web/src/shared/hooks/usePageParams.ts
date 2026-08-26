import { useEffect } from 'react';

/**
 * Pages count from **one** in the URL and from zero in the API (`docs/07-frontend-ux-ui.md`), and
 * the translation between the two happens in each listing's own `api/` layer, once. What lives here
 * is what every paginated listing does identically with the number the URL carries.
 */
export const FIRST_PAGE = 1;

function readPositiveInteger(value: string | null): number | null {
  if (value === null || !/^\d+$/.test(value)) {
    return null;
  }
  return Number(value);
}

/**
 * The page the URL is on, counting from one. `?page=1` meaning the second page is a trap for anyone
 * editing the address bar, so the offset stays in the API layer. Anything that is not a page — `0`,
 * `-3`, `abc` — is the first.
 */
export function readPage(searchParams: URLSearchParams): number {
  return Math.max(FIRST_PAGE, readPositiveInteger(searchParams.get('page')) ?? FIRST_PAGE);
}

/**
 * Pulls the reader back onto a page that exists when the one they are on stops existing.
 *
 * The case that makes it necessary is deleting the last row of the last page: the answer comes back
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
