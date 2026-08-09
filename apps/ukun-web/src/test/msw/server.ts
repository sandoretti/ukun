import { setupServer } from 'msw/node';

import { catalogHandlers } from './catalogHandlers';

/**
 * The HTTP boundary of the test run, and the **only** place mocks exist in this app: the browser
 * always talks to the real API (`docs/13-testing.md` §2). Stubbing at the protocol edge instead of
 * at axios keeps the whole client under test — interceptors, CSRF header, `ProblemDetail` mapping.
 */
export const server = setupServer(...catalogHandlers);
