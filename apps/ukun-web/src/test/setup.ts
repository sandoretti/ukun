import { afterAll, afterEach, beforeAll } from 'vitest';
import { cleanup, configure } from '@testing-library/react';

import i18n from '../i18n/config';
import { server } from './msw/server';
import { installIntersectionObserverStub, resetIntersectionObservers } from './intersectionObserver';

/**
 * jsdom ships no `matchMedia`, and react-bootstrap asks for it whenever a component reacts to a
 * breakpoint (the catalog's responsive `Offcanvas`). Answering "no match" is the honest answer for
 * a window with no layout: the offcanvas then renders its static node, which is exactly what a
 * viewport from `lg` up would show.
 */
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string): MediaQueryList =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
      addListener: () => {},
      removeListener: () => {},
    }) as unknown as MediaQueryList,
});

/**
 * jsdom has no layout, so scrolling is not implemented and calling it prints an error that is not
 * one. The listing scrolls back to the top on every page change; here that is a no-op.
 */
Object.defineProperty(window, 'scrollTo', { writable: true, value: () => {} });

/**
 * How long a `findBy*` waits before giving up. The default second is not enough for the tests that
 * mount the whole router: a screen behind `React.lazy` has to load its chunk before it can answer,
 * and with the suite's files running in parallel that lands over the line often enough to fail a run
 * that has nothing wrong with it.
 *
 * It does not weaken any assertion — what is wrong still fails, it just takes longer to say so.
 */
configure({ asyncUtilTimeout: 4000 });

installIntersectionObserverStub();

beforeAll(async () => {
  // Any request the handlers do not cover is a bug in the test, not something to let through.
  server.listen({ onUnhandledRequest: 'error' });
  // The detector would pick whatever jsdom reports; assertions need one fixed language.
  await i18n.changeLanguage('es');
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetIntersectionObservers();
  // Where to come back to after signing in lives in session storage, and it outlives a test the way
  // it outlives a page load — which is the point of it, and a leak between tests if left behind.
  sessionStorage.clear();
});

afterAll(() => {
  server.close();
});
