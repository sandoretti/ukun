import { useEffect, type ReactNode } from 'react';

import { useAuth } from '../../shared/hooks/useAuth';
import { LoadingState } from '../../shared/ui/LoadingState';

type RequireAuthProps = {
  children: ReactNode;
};

/**
 * A route that only exists for someone with a session. Without one it sends the browser to
 * authenticate, remembering this very route so the trip back lands here and not on the home page.
 *
 * It asks the session probe rather than waiting for a screen to collect a 401: painting a protected
 * screen for a visitor means every read inside it fails, and the reader watches an error appear
 * before being sent away. The interceptor still covers the other half — a session that dies while
 * the screen is already open.
 */
export function RequireAuth({ children }: RequireAuthProps) {
  const { isAuthenticated, isPending, signIn } = useAuth();

  useEffect(() => {
    if (!isPending && !isAuthenticated) {
      signIn();
    }
  }, [isAuthenticated, isPending, signIn]);

  // Two waits with the same shape: the probe answering, and the browser leaving for the
  // authorization server. Neither is a state this route has anything to say about.
  if (!isAuthenticated) {
    return <LoadingState />;
  }

  return <>{children}</>;
}
