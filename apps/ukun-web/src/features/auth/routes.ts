/**
 * The account screen, named once because the router and the navigation bar both point at it.
 *
 * It is not `/login`, and it could not be: the gateway reserves that prefix along with `/logout`
 * (`ReservedPaths`), so the SPA fallback never answers either of them — a React route with those
 * names works under `npm run dev` and turns into a 404 the moment the gateway serves the bundle.
 *
 * The sign-in screen is the other route of this feature, and it is **not** named here: it lives in
 * `shared/auth/signIn.ts` next to the gateway endpoint it leads to, because the axios interceptor
 * needs it and `shared/` cannot import from a feature.
 */
export const ACCOUNT_PATH = '/account';

/**
 * The signup screen (RF-06), which **is** a route of this router: creating a customer is
 * `ukun-api`'s job and not the authorization server's (`docs/08-identidad-y-seguridad.md` §3), so
 * unlike signing in, this one never leaves the SPA.
 *
 * It is `/signup` and not `/register` for no deeper reason than matching `/signin`, and it is
 * neither `/login` nor anything under it: those prefixes belong to the gateway (`ReservedPaths`).
 */
export const SIGN_UP_PATH = '/signup';
