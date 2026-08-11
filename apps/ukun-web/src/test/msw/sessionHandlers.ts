import { http, HttpResponse } from 'msw';

import type { components } from '../../types/api';

type CurrentUser = components['schemas']['CurrentUser'];
type Role = components['schemas']['Role'];

const base = import.meta.env.VITE_API_URL || '';

/**
 * Test-only stand-in for `GET /api/me`, the session probe. The shell asks for it on every render, so
 * a default handler is not optional: the run refuses any request nobody answers
 * (`onUnhandledRequest: 'error'`).
 *
 * **The default is the anonymous visitor**, which is the state most screens are tested in — the
 * catalogue, the product sheet and the stores are public and must work with no session at all.
 */
const anonymous = http.get(`${base}/api/me`, () => unauthenticated());

export const sessionHandlers = [anonymous];

/**
 * The 401 the gateway's entry point answers with, body included. It is exported because the
 * interceptor is tested against the same answer on a different endpoint: what tells a dead session
 * apart from an anonymous read is the request, never the response.
 */
export function unauthenticated() {
  return HttpResponse.json(
    {
      type: 'https://ukun.dev/errors/unauthenticated',
      title: 'UNAUTHENTICATED',
      status: 401,
      detail: 'no session',
      code: 'UNAUTHENTICATED',
    },
    { status: 401 },
  );
}

/** The account every signed-in test uses unless it needs a different set of roles. */
export const currentUser: CurrentUser = {
  accountId: '8f2c1b64-0f4a-4b2e-9f1e-6a1d9c3b7e55',
  email: 'customer@ukun.dev',
  roles: ['CUSTOMER'],
  locale: 'es',
};

/** Overrides the default for one test: `server.use(signedIn())`, or with the roles it needs. */
export function signedIn(roles: Role[] = currentUser.roles) {
  return http.get(`${base}/api/me`, () => HttpResponse.json({ ...currentUser, roles }));
}
