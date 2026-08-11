import { http, HttpResponse } from 'msw';

import type { components } from '../../types/api';

/**
 * Test-only stand-in for `GET`/`PUT /api/me/profile` (RF-05), typed from the same generated OpenAPI
 * types the app uses, so a contract change breaks compilation here too.
 */

type CustomerProfile = components['schemas']['CustomerProfile'];
type UpdateProfileRequest = components['schemas']['UpdateProfileRequest'];
type ValidationError = components['schemas']['ValidationError'];
type ErrorCode = components['schemas']['ErrorCode'];

const base = import.meta.env.VITE_API_URL || '';

/**
 * The profile every signed-in test starts from. `favoriteStoreId` is `2` on purpose: that is
 * `Ukun Gijón` in `storeHandlers`, so the screen has to resolve a **name** from an identifier
 * (ADR-0017 §2) instead of getting one handed to it.
 */
export const customerProfile: CustomerProfile = {
  name: 'Ana',
  lastName: 'García',
  email: 'customer@ukun.dev',
  locale: 'es',
  address: { street: 'Calle Uría 1', city: 'Oviedo', province: 'Asturias', postalCode: '33003' },
  // Four digits and an expiry, and nothing else: there is no PAN and no CVV in this contract.
  card: { last4: '4242', brand: 'VISA', expMonth: 12, expYear: 2027 },
  favoriteStoreId: 2,
};

export const profileHandlers = [
  http.get(`${base}/api/me/profile`, () => HttpResponse.json(customerProfile)),

  /**
   * The API answers with the updated profile, and with the very same `200` when nothing changed
   * (`ProfileChanges.between(...)` writes nothing) — which is why the screen cannot tell the two
   * apart from the response, and does not pretend to.
   */
  http.put(`${base}/api/me/profile`, async ({ request }) => {
    const body = (await request.json()) as UpdateProfileRequest;
    return HttpResponse.json({
      ...body,
      // The email is not in the request: it is a projection of the token claim.
      email: customerProfile.email,
      favoriteStoreId: body.favoriteStoreId ?? null,
    });
  }),
];

/**
 * An error of this contract, shaped like the real ones: `type` derived from the `code` exactly as
 * `docs/12-error-contract.md` §2 derives it, and a `detail` in fixed English for the operator — if
 * that sentence ever shows up on screen, something is broken.
 */
function problem(code: ErrorCode, status: number, extensions: Record<string, unknown> = {}) {
  return {
    type: `https://ukun.dev/errors/${code.toLowerCase().replaceAll('_', '-')}`,
    title: code,
    status,
    detail: 'Fixed English detail nobody should ever read on screen',
    code,
    traceId: `trace-${status}`,
    ...extensions,
  };
}

/** A `400 VALIDATION_FAILED` on the save, with the field paths the API really produces. */
export function profileValidationFailed(errors: ValidationError[]) {
  return http.put(`${base}/api/me/profile`, () =>
    HttpResponse.json(problem('VALIDATION_FAILED', 400, { errors }), { status: 400 }),
  );
}

/** Any other failure of the read, by `code` — an employee opening the screen gets `403 FORBIDDEN`. */
export function profileFails(code: ErrorCode, status: number) {
  return http.get(`${base}/api/me/profile`, () => HttpResponse.json(problem(code, status), { status }));
}
