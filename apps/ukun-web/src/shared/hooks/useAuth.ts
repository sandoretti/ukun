import { useCallback } from 'react';
import { useLocation } from 'react-router-dom';

import { useCurrentUser, type CurrentUser, type Role } from '../api/session';
import { startSignIn } from '../auth/signIn';

type Auth = {
  /** The signed-in user, or `undefined` for a visitor — the shell renders for both. */
  user: CurrentUser | undefined;
  isAuthenticated: boolean;
  /** The probe is still in flight: it is not yet known which of the two this is. */
  isPending: boolean;
  hasRole: (role: Role) => boolean;
  /** Sends the browser to authenticate and brings it back to the route it is on now. */
  signIn: () => void;
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
    hasRole: useCallback((role: Role) => user?.roles.includes(role) ?? false, [user]),
    // The route comes from the router and not from `window.location`: they agree in the browser and
    // they do not under a `MemoryRouter`, which is where this gets tested.
    signIn: useCallback(
      () => startSignIn(location.pathname + location.search),
      [location.pathname, location.search],
    ),
  };
}
