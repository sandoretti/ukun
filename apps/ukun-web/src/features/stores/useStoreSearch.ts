import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';

import type { Store } from '../../shared/api/stores';

/** Same normalisation the API applies with `unaccent(lower(...))`: «gijon» has to find «Gijón». */
const normalise = (value: string) => value.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();

/**
 * The listing, filtered by what was typed. `undefined` travels through untouched — that is the read
 * still being in flight, and it is what tells `QueryBoundary` to paint the loading state instead of
 * an empty one.
 *
 * It filters and **does not sort**: the response already comes ordered by name (`listStores`), and
 * reordering it here would be a second, silent criterion.
 */
export function filterStores(stores: Store[] | undefined, term: string): Store[] | undefined {
  const needle = normalise(term.trim());
  if (stores === undefined || needle === '') {
    return stores;
  }
  // Name or city, which is what the contract's own `q` filters by.
  return stores.filter(
    (store) => normalise(store.name).includes(needle) || normalise(store.address.city).includes(needle),
  );
}

/**
 * The search term of the stores screen, held in the URL and not in a `useState`
 * (`docs/07-frontend-ux-ui.md §El estado de una pantalla de listado vive en la URL`): a filtered
 * result is linkable, survives a reload and comes back with the back button.
 *
 * What it does **not** do is reach the API. Stores are closed reference data cached for the whole
 * session by `useStores()`; sending the term would force one cache entry per typed term and throw
 * that away. The contract's `q` stays available for whoever needs it — this screen filters what it
 * already has.
 */
export function useStoreSearch() {
  const [searchParams, setSearchParams] = useSearchParams();

  // Not trimmed on the way out: it is what the field shows, and a space swallowed as it is typed
  // makes the input feel broken. The trimming happens where it matters, in `filterStores`.
  const term = searchParams.get('q') ?? '';

  const setTerm = useCallback(
    (next: string) => {
      const params = new URLSearchParams(searchParams);
      if (next.trim() === '') {
        params.delete('q');
      } else {
        params.set('q', next);
      }
      // A history entry per keystroke would turn the back button into an undo of letters. Only
      // entering the filtered listing and leaving it are navigations; refining the term replaces.
      setSearchParams(params, { replace: params.has('q') === searchParams.has('q') });
    },
    [searchParams, setSearchParams],
  );

  return { term, setTerm };
}
