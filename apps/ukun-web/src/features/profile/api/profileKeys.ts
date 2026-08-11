import type { components } from '../../../types/api';

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
