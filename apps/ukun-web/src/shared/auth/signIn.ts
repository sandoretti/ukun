import { currentPath, navigateTo } from '../browser/navigateTo';

/**
 * Where the browser goes to authenticate, and where it comes back to. **The only place in the app
 * that decides either**, which is what keeps "take me back to what I asked for" from being spread
 * over the interceptor, the navigation bar and the route guard.
 *
 * The SPA never sees a token (ADR-0003): it starts the flow by navigating, and the gateway does the
 * rest — authorization code with PKCE against `ukun-auth`, tokens kept in its own session. There is
 * nothing to store here beyond the destination.
 */

/**
 * The endpoint of the gateway's `oauth2Login` filter, not a client-side route: `/login` and
 * `/logout` are reserved prefixes of the gateway (`ReservedPaths`) and answer 404 to the SPA
 * fallback, so React must not own either of them.
 */
export const SIGN_IN_URL = '/oauth2/authorization/ukun';

const RETURN_TO_KEY = 'ukun.auth.returnTo';

/**
 * `tried` is the loop brake. The gateway's entry point answers a `ProblemDetail`, never a 302
 * (`docs/08-identidad-y-seguridad.md` §6), so there is no saved request on the server and
 * `oauth2Login` always returns the browser to `/`: the destination is the client's to keep. Which
 * means the client is also the one that has to stop the cycle when the restored destination throws
 * the user out again.
 */
type PendingReturn = { path: string; tried: boolean };

function read(): PendingReturn | undefined {
  // Session storage can be unavailable (a browser with storage blocked). Failing to remember where
  // to come back to must never be what stops someone from signing in.
  try {
    const stored = sessionStorage.getItem(RETURN_TO_KEY);
    return stored === null ? undefined : (JSON.parse(stored) as PendingReturn);
  } catch {
    return undefined;
  }
}

function write(pending: PendingReturn | undefined): void {
  try {
    if (pending === undefined) {
      sessionStorage.removeItem(RETURN_TO_KEY);
    } else {
      sessionStorage.setItem(RETURN_TO_KEY, JSON.stringify(pending));
    }
  } catch {
    // See `read`.
  }
}

function rememberReturnTo(path: string): void {
  const pending = read();

  // A destination that has already been restored once and is asking for authentication again is not
  // retried: it is forgotten, and the trip back from the login lands on the home page. Without this,
  // "return to where you were" is an endless round trip whenever that route answers 401 even with a
  // fresh session.
  if (pending?.path === path && pending.tried) {
    write(undefined);
    return;
  }

  write({ path, tried: false });
}

/**
 * Sends the browser to authenticate, remembering where it was so it can be brought back.
 *
 * `returnTo` is optional because the axios interceptor has no router underneath — it reads the
 * destination off the browser. A component passes its own route instead, which is the one thing
 * `window.location` cannot tell apart under a `MemoryRouter`.
 */
export function startSignIn(returnTo: string = currentPath()): void {
  rememberReturnTo(returnTo);
  navigateTo(SIGN_IN_URL);
}

/**
 * The destination to restore, once. It is handed over a single time — the mark stays behind so a
 * reload does not send the reader back to a route they already left, and so the same destination is
 * not walked into twice in a row.
 */
export function takeReturnTo(): string | undefined {
  const pending = read();
  if (pending === undefined || pending.tried) {
    return undefined;
  }

  write({ path: pending.path, tried: true });

  return pending.path;
}

/** Drops the destination altogether: signing out is not a trip anyone should be brought back from. */
export function forgetReturnTo(): void {
  write(undefined);
}
