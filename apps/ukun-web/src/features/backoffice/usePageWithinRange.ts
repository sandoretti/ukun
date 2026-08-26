import { useEffect } from 'react';

import { FIRST_PAGE } from './routes';

/**
 * Pulls the reader back onto a page that exists when the one they are on stops existing.
 *
 * It lives in a file of its own, and not in the hook that first needed it, because it is about
 * pagination and not about stock: the stock table, the products with no row in a store and the
 * back-office catalog all use it, and the third one had to import it from `useStockParams` to get
 * hold of it. It stays inside the feature —both readers are here— rather than going up to `shared/`,
 * which is for what a *second* feature needs.
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
