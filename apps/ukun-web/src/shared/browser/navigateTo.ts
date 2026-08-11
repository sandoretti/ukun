/**
 * A full page navigation, as opposed to a route change: the browser leaves this document and the
 * server answers the next one. It is what the OAuth2 flow needs — the authorization server is
 * another origin, and no router can walk there.
 *
 * It is a module of its own for one reason: `window.location` is `[Unforgeable]` in jsdom, so
 * neither the object nor its `assign` can be redefined, and a test cannot observe the navigation
 * any other way. Keeping the single call behind this boundary is what lets a test assert "this
 * sends the browser to the authorization server" instead of asserting on the leftovers it happens
 * to leave in storage.
 */
export function navigateTo(url: string): void {
  window.location.assign(url);
}

/** Where the browser currently is, path and query, as the SPA would name that route. */
export function currentPath(): string {
  return window.location.pathname + window.location.search;
}
