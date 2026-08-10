import { useEffect, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';

import { REASON_PARAM, SIGN_IN_PATH } from '../../shared/auth/signIn';
import { useAuth } from '../../shared/hooks/useAuth';
import { LoadingState } from '../../shared/ui/LoadingState';

type RequireAuthProps = {
  children: ReactNode;
};

/**
 * A route that only exists for someone with a session. Without one it sends the reader to the
 * sign-in screen, remembering this very route so the trip back lands here and not on the home page.
 *
 * It asks the session probe rather than waiting for a screen to collect a 401: a guard that learns
 * about the missing session from a failed read has already painted half a screen that was never
 * going to work.
 *
 * The move is a route change and not a page load — unlike the axios interceptor, this one has a
 * router underneath — and it `replace`s, so the back button does not walk straight into the
 * protected route it just left.
 */
export function RequireAuth({ children }: RequireAuthProps) {
  const { isAuthenticated, isPending, rememberThisRoute } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (isPending || isAuthenticated) {
      return;
    }

    // Both in one effect and in this order: the destination has to be written before the screen
    // that will send the browser away is on screen.
    rememberThisRoute();
    void navigate(`${SIGN_IN_PATH}?${REASON_PARAM}=required`, { replace: true });
  }, [isAuthenticated, isPending, navigate, rememberThisRoute]);

  // Two waits with the same shape: the probe answering, and the redirect landing. Neither is a
  // state this route has anything to say about.
  if (!isAuthenticated) {
    return <LoadingState />;
  }

  return <>{children}</>;
}
