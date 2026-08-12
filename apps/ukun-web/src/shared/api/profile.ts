import { useQuery } from '@tanstack/react-query';

import type { components } from '../../types/api';
import { http } from './http';
import { retryUnlessClientError } from './retry';

/** What `GET`/`PUT /api/me/profile` answers with. `email` is `readonly`: it is a projection (RF-05). */
export type CustomerProfile = components['schemas']['CustomerProfile'];
/** The body of the `PUT`. It is a **replacement**, not a patch — an absent field is a cleared field. */
export type UpdateProfileRequest = components['schemas']['UpdateProfileRequest'];

/**
 * Query keys of the profile. One resource and no filters, so the hierarchy is short — but it is
 * still a factory and still hierarchical: `all` is what the mutation invalidates by prefix, and
 * writing `['profile', 'me']` by hand in two places is the cache bug that shows up later.
 */
export const profileKeys = {
  all: ['profile'] as const,
  me: () => [...profileKeys.all, 'me'] as const,
};

/**
 * The signed-in customer's profile (RF-05). The route is behind `RequireAuth` and the endpoint
 * requires a token, so there is no anonymous case to answer here: a `401` is a dead session and the
 * axios interceptor already deals with it, and a `403` —an employee opening this screen— is a real
 * error state with its own translated `code`.
 *
 * **Why the read lives in `shared/api/` and the write does not**: checkout shows the address and the
 * card the order is about to freeze into its snapshots (RF-04, ADR-0011), so `features/orders` reads
 * the same profile `features/profile` edits — a second consumer in another feature, and features
 * never import each other (`docs/07-frontend-ux-ui.md`). It is the same move `cart.ts` and
 * `session.ts` already made. Saving the profile stays where its only screen is.
 *
 * **`enabled` exists for the public screens, and it is a correctness switch rather than a
 * saved request.** The catalogue reads the favourite store from here to place its availability
 * (RF-01) and it is reachable with no session at all; this endpoint answers `401` to a visitor, and
 * a `401` without `allowAnonymous` is a dead session as far as the axios interceptor is concerned —
 * it would send everyone browsing the catalogue to sign in. Whoever passes `false` is saying "there
 * is no session to ask about", and the query stays pending for good, which is the honest answer.
 */
export function useMyProfile({ enabled = true }: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: profileKeys.me(),
    queryFn: ({ signal }) =>
      http.get<CustomerProfile>('/api/me/profile', { signal }).then((response) => response.data),
    enabled,
    retry: retryUnlessClientError,
  });
}
