import { currentPath, navigateTo } from '../browser/navigateTo';

/**
 * Where the browser goes to authenticate, and where it comes back to. **The only place in the app
 * that decides either**, which is what keeps "take me back to what I asked for" from being spread
 * over the interceptor, the navigation bar and the route guard.
 *
 * The SPA never sees a token (ADR-0003): it starts the flow by navigating, and the gateway does the
 * rest — authorization code with PKCE against `ukun-auth`, tokens kept in its own session. There is
 * nothing to store here beyond the destination.
 *
 * There are two ways out, and the difference is whether anything needs explaining first. Someone who
 * presses "sign in" has already said what they want, so they go straight to the authorization
 * server, which since v0.5 answers with a page of this shop's own. Someone who was *thrown out* — by
 * a protected route or by a session that died mid-visit — goes through `/signin` instead, because
 * "you have to sign in" and "your session expired" are things only the SPA can say.
 *
 * Remembering the destination and leaving are separate functions for that reason: the second path
 * splits them across two screens.
 */

/**
 * The endpoint of the gateway's `oauth2Login` filter, not a client-side route: `/login` and
 * `/logout` are reserved prefixes of the gateway (`ReservedPaths`) and answer 404 to the SPA
 * fallback, so React must not own either of them.
 */
export const SIGN_IN_URL = '/oauth2/authorization/ukun';

/**
 * The screen that asks before leaving, which **is** a route of this router — `/signin` and not
 * `/login` for the reason above. It lives next to `SIGN_IN_URL` rather than in
 * `features/auth/routes.ts` because the axios interceptor needs it and `shared/` never imports from
 * a feature; and because the two halves of "where you go to authenticate" belong together.
 */
export const SIGN_IN_PATH = '/signin';

/** Why the screen is being shown, when it is not simply because someone asked for it. */
export const SIGN_IN_REASONS = ['expired', 'required'] as const;

export type SignInReason = (typeof SIGN_IN_REASONS)[number];

export const REASON_PARAM = 'reason';

/**
 * The reason carried by the URL, narrowed to the closed set above. Anything else — a hand-typed
 * value, a stale link — reads as no reason at all rather than as a missing translation.
 */
export function readReason(params: URLSearchParams): SignInReason | undefined {
  const value = params.get(REASON_PARAM);

  return SIGN_IN_REASONS.find((reason) => reason === value);
}

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

/**
 * Marks where the browser is, so the trip through the sign-in screen and the authorization server
 * comes back to it. Exported because remembering and leaving are no longer the same step: the
 * navigation bar and the route guard remember here and then move within the router.
 */
export function rememberReturnTo(path: string): void {
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
 * Sends the browser to the sign-in screen with the reason it is being shown, remembering where it
 * was so it can be brought back.
 *
 * The navigation is a full page load and not a route change because the only caller without a
 * router underneath is the axios interceptor, and a dead session is precisely the moment to throw
 * away whatever the app had in flight. `returnTo` is optional for the same reason: the interceptor
 * reads the destination off the browser, while a component passes its own route — the one thing
 * `window.location` cannot tell apart under a `MemoryRouter`.
 */
export function sendToSignIn(reason: SignInReason, returnTo: string = currentPath()): void {
  rememberReturnTo(returnTo);
  navigateTo(`${SIGN_IN_PATH}?${REASON_PARAM}=${reason}`);
}

/**
 * Leaves the SPA for the authorization server: the one action of the sign-in screen.
 *
 * It does **not** remember anything. Whoever sent the browser here already did, and writing the
 * destination again would be writing `/signin` itself — which is both useless as a landing place
 * and enough to reset the loop brake below.
 */
export function continueToAuthorizationServer(): void {
  navigateTo(SIGN_IN_URL);
}

/**
 * The short way out, for someone who asked to sign in: remember the route and go.
 *
 * No screen in between, on purpose. `/signin` exists to explain something, and there is nothing to
 * explain to a reader who just pressed "sign in" — the page they land on is `ukun-auth`'s own, with
 * this shop's card and palette, so there is no jarring change of scenery to cushion either. An
 * interstitial here would be one more click and one more page load between the intention and the
 * password field.
 */
export function startSignIn(returnTo: string = currentPath()): void {
  rememberReturnTo(returnTo);
  continueToAuthorizationServer();
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
