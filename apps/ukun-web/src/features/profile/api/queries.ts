import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { http } from '../../../shared/api/http';
import { retryUnlessClientError } from '../../../shared/api/retry';
import { sessionKeys } from '../../../shared/api/session';
import { profileKeys, type CustomerProfile, type UpdateProfileRequest } from './profileKeys';

/**
 * The signed-in customer's profile (RF-05). The route is behind `RequireAuth` and the endpoint
 * requires a token, so there is no anonymous case to answer here: a `401` is a dead session and the
 * axios interceptor already deals with it, and a `403` —an employee opening this screen— is a real
 * error state with its own translated `code`.
 */
export function useMyProfile() {
  return useQuery({
    queryKey: profileKeys.me(),
    queryFn: ({ signal }) =>
      http.get<CustomerProfile>('/api/me/profile', { signal }).then((response) => response.data),
    retry: retryUnlessClientError,
  });
}

/**
 * Saving the profile.
 *
 * The response is the updated profile, which the contract returns precisely so the SPA does not need
 * a second round trip — it is written into the cache and the form resyncs from it. The invalidation
 * on top is not redundant: what is painted comes from the write, and the background refetch is what
 * confirms it against the server.
 *
 * **The language is the interesting part.** `locale` is the source of truth while there is a session
 * and it travels between devices (ADR-0019, RF-05), so saving a different one has to change the
 * language the application is actually speaking. It also travels in `GET /api/me`, whose cache entry
 * is stale from that moment on — hence the second invalidation, and only when it really changed.
 * `<html lang>` is not touched here: `useLocaleSync` in the shell already follows i18next.
 */
export function useUpdateMyProfile() {
  const queryClient = useQueryClient();
  const { i18n } = useTranslation();

  return useMutation({
    mutationFn: (request: UpdateProfileRequest) =>
      http.put<CustomerProfile>('/api/me/profile', request).then((response) => response.data),
    onSuccess: async (updated) => {
      // Read before overwriting: afterwards there is nothing left to compare the language against.
      const previous = queryClient.getQueryData<CustomerProfile>(profileKeys.me());

      queryClient.setQueryData(profileKeys.me(), updated);
      await queryClient.invalidateQueries({ queryKey: profileKeys.all });

      if (previous?.locale !== updated.locale) {
        await i18n.changeLanguage(updated.locale);
        await queryClient.invalidateQueries({ queryKey: sessionKeys.currentUser() });
      }
    },
  });
}
