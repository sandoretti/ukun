/**
 * The account screen, named once because the router and the navigation bar both point at it.
 *
 * It is not `/login`, and it could not be: the gateway reserves that prefix along with `/logout`
 * (`ReservedPaths`), so the SPA fallback never answers either of them — a React route with those
 * names works under `npm run dev` and turns into a 404 the moment the gateway serves the bundle.
 */
export const ACCOUNT_PATH = '/account';
