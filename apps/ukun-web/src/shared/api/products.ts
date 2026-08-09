import { keepPreviousData, useQuery } from '@tanstack/react-query';

import type { components } from '../../types/api';
import { http } from './http';

export type ProductSuggestion = components['schemas']['ProductSuggestion'];

/** Below this, the suggestions are noise: nothing is asked and the popup stays closed. */
export const SUGGEST_MIN_LENGTH = 2;
const SUGGEST_LIMIT = 8;

/**
 * A root of its own, not a branch of `productKeys`: the typeahead is a different read of a
 * different shape (a flat array with no `totalElements`, so the API can skip the `COUNT` on every
 * keystroke). Hanging it off the listing's key would make an invalidation of one wipe the other.
 */
export const productSuggestKeys = {
  all: ['product-suggestions'] as const,
  query: (term: string, limit: number) => [...productSuggestKeys.all, term, limit] as const,
};

/**
 * Suggestions for the search box. It lives in `shared/` and not in `features/catalog/` because its
 * consumers are the navigation bar (`app/`) and, later, the landing page — and features never
 * import each other (`docs/07-frontend-ux-ui.md`). Same reasoning as `shared/api/stores.ts`.
 *
 * The caller is expected to hand over an already debounced term.
 */
export function useProductSuggestions(term: string, limit = SUGGEST_LIMIT) {
  const trimmed = term.trim();

  return useQuery<ProductSuggestion[]>({
    queryKey: productSuggestKeys.query(trimmed, limit),
    queryFn: ({ signal }) =>
      http
        .get<ProductSuggestion[]>('/api/products/suggest', { params: { q: trimmed, limit }, signal })
        .then((response) => response.data),
    enabled: trimmed.length >= SUGGEST_MIN_LENGTH,
    // The same letters are typed over and over (backspace, retype): a minute of cache saves the
    // round trip, and the previous list stays on screen instead of blinking between keystrokes.
    staleTime: 60 * 1000,
    placeholderData: keepPreviousData,
  });
}
