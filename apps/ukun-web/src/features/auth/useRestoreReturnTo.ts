import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { takeReturnTo } from '../../shared/auth/signIn';
import { useAuth } from '../../shared/hooks/useAuth';

/**
 * Brings the reader back to the route they asked for before authenticating. Mounted once from the
 * shell, next to `useLocaleSync` and for the same reason: it is one effect of the frame, not of any
 * screen.
 *
 * The gateway cannot do this. Its entry point answers a `ProblemDetail` instead of a 302
 * (`docs/08-identidad-y-seguridad.md` §6), so there is no saved request and `oauth2Login` always
 * returns the browser to `/` — the destination is remembered on this side, and restored on this
 * side, in one place.
 *
 * Nothing is restored without a session: coming back from a login that was abandoned and jumping
 * into a protected route would only start the round trip again. The destination stays pending until
 * there is someone to show it to.
 */
export function useRestoreReturnTo() {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }

    const path = takeReturnTo();
    if (path !== undefined && path !== location.pathname + location.search) {
      // `replace`: the trip through the authorization server is not a step anyone should be able to
      // walk back into with the back button.
      navigate(path, { replace: true });
    }
  }, [isAuthenticated, location.pathname, location.search, navigate]);
}
