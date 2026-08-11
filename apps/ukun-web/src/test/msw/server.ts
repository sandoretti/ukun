import { setupServer } from 'msw/node';

import { cartHandlers } from './cartHandlers';
import { catalogHandlers } from './catalogHandlers';
import { profileHandlers } from './profileHandlers';
import { sessionHandlers } from './sessionHandlers';
import { storeHandlers } from './storeHandlers';

/**
 * The HTTP boundary of the test run, and the **only** place mocks exist in this app: the browser
 * always talks to the real API (`docs/13-testing.md` §2). Stubbing at the protocol edge instead of
 * at axios keeps the whole client under test — interceptors, CSRF header, `ProblemDetail` mapping.
 *
 * One module per group of endpoints, all registered here: the stores are read by two screens, so
 * their doubles do not belong to either one's file.
 */
export const server = setupServer(
  ...catalogHandlers,
  ...storeHandlers,
  ...sessionHandlers,
  ...profileHandlers,
  ...cartHandlers,
);
