import { useCallback } from 'react';
import { useLocation } from 'react-router-dom';

import { hasRole, useCurrentUser, type CurrentUser, type Role } from '../api/session';
import { rememberReturnTo, startSignIn } from '../auth/signIn';

type Auth = {
  /** The signed-in user, or `undefined` for a visitor — the shell renders for both. */
  user: CurrentUser | undefined;
  isAuthenticated: boolean;
  /** The probe is still in flight: it is not yet known which of the two this is. */
  isPending: boolean;
  hasRole: (role: Role) => boolean;
  /** Leaves for the authorization server, remembering the route to come back to. */
  signIn: () => void;
  /**
   * Marks the route being looked at as the one to come back to, without going anywhere. For the
   * route guard, which sends the reader to `/signin` first because it has a reason to give.
   */
  rememberThisRoute: () => void;
};

/**
 * The session, for anything that needs to know about it — the bar, the route guard, the account
 * screen. It is a transversal hook and lives here rather than in `features/auth/` because
 * `docs/07-frontend-ux-ui.md` puts it here by name.
 *
 * **`hasRole` is the only place roles are read.** What the bar offers is decided from
 * `GET /api/me`, never from a list of privileged routes copied around the components — and hiding
 * an entry is UX, never the barrier: the gateway refuses `/api/admin/**` without `ROLE_ADMIN` and
 * `ukun-api` checks it again (`docs/08-identidad-y-seguridad.md` §4).
 */
export function useAuth(): Auth {
  const { data, isPending } = useCurrentUser();
  const location = useLocation();

  const user = data ?? undefined;

  return {
    user,
    isAuthenticated: user !== undefined,
    isPending,
    hasRole: useCallback((role: Role) => hasRole(user, role), [user]),
    // The route comes from the router and not from `window.location`: they agree in the browser and
    // they do not under a `MemoryRouter`, which is where this gets tested. It is also the only
    // place that composes the two halves of a route, so the bar and the guard cannot drift.
    signIn: useCallback(
      () => startSignIn(location.pathname + location.search),
      [location.pathname, location.search],
    ),
    rememberThisRoute: useCallback(
      () => rememberReturnTo(location.pathname + location.search),
      [location.pathname, location.search],
    ),
  };
}
