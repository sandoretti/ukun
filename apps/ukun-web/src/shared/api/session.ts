import { useQuery } from '@tanstack/react-query';

import type { components } from '../../types/api';
import { http, toProblem } from './http';

export type CurrentUser = components['schemas']['CurrentUser'];
export type Role = components['schemas']['Role'];

/**
 * The session probe is read by the shell, the route guard and the account screen, so its key factory
 * lives in `shared/` for the same reason the stores' does: features never import each other.
 */
export const sessionKeys = {
  all: ['session'] as const,
  currentUser: () => [...sessionKeys.all, 'currentUser'] as const,
};

const UNAUTHENTICATED = 401;

/**
 * Who the browser is talking as, or `null` for a visitor with no session.
 *
 * **A 401 here is an answer, not an error.** The catalogue, the product sheet and the stores are
 * public (`GatewaySecurityConfig`: everything is `permitAll` except `/api/me/**` and
 * `/api/admin/**`), so every anonymous visitor gets one on the very first render of the shell.
 * Turning it into `null` is what keeps the home page from painting an error — and `allowAnonymous`
 * is what keeps the interceptor from reading it as a dead session and bouncing the visitor to the
 * authorization server.
 *
 * No retries, for the same reason: "there is no session" does not become "there is one" by asking
 * three times.
 */
export function useCurrentUser() {
  return useQuery({
    queryKey: sessionKeys.currentUser(),
    queryFn: async ({ signal }) => {
      try {
        const response = await http.get<CurrentUser>('/api/me', { signal, allowAnonymous: true });
        return response.data;
      } catch (error) {
        if (toProblem(error).status === UNAUTHENTICATED) {
          return null;
        }
        throw error;
      }
    },
    retry: false,
    // Identity does not change while the tab is open: it changes by signing in or out, and both are
    // full page navigations that rebuild this cache from scratch.
    staleTime: Infinity,
  });
}
