import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { http } from '../../../shared/api/http';
import { profileKeys, type CustomerProfile, type UpdateProfileRequest } from '../../../shared/api/profile';
import { sessionKeys } from '../../../shared/api/session';

/**
 * Saving the profile — the write only this screen makes, hanging off the same `profileKeys` the
 * shared read declares: one key, one owner, invalidated by prefix.
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
