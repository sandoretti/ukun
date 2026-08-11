import { http, HttpResponse } from 'msw';

import type { components } from '../../types/api';
import { problem } from './problem';

/**
 * Test-only stand-in for `POST /api/signup` (RF-06), typed from the same generated OpenAPI types the
 * app uses, so a contract change breaks compilation here too.
 */

type SignupRequest = components['schemas']['SignupRequest'];
type ValidationError = components['schemas']['ValidationError'];

const base = import.meta.env.VITE_API_URL || '';

/**
 * Every body the signup was called with, in order. The screen sends the request and then leaves for
 * the authorization server, so what it sent is not visible anywhere on screen afterwards — and what
 * matters most about this request is precisely something absent from it: the full card number.
 *
 * Emptied by the handler's own setup in each test rather than globally: the array is the assertion.
 */
export const signupRequests: SignupRequest[] = [];

/** The saga completes and the account is active: `201`, no body. */
export const signupHandlers = [
  http.post(`${base}/api/signup`, async ({ request }) => {
    signupRequests.push((await request.json()) as SignupRequest);
    return new HttpResponse(null, { status: 201 });
  }),
];

/**
 * The other success: the customer is written but the activation was not confirmed (`202`). Repeating
 * the same signup finishes it, which is what the screen has to say instead of pretending it failed.
 */
export function signupActivationPending() {
  return http.post(`${base}/api/signup`, async ({ request }) => {
    signupRequests.push((await request.json()) as SignupRequest);
    return new HttpResponse(null, { status: 202 });
  });
}

/** `409 EMAIL_ALREADY_EXISTS`, propagated from `ukun-auth` through the saga (`docs/08` §3). */
export function signupEmailTaken() {
  return http.post(`${base}/api/signup`, () => problem('EMAIL_ALREADY_EXISTS', 409));
}

/** A `400 VALIDATION_FAILED` with the field paths the API really produces. */
export function signupValidationFailed(errors: ValidationError[]) {
  return http.post(`${base}/api/signup`, () => problem('VALIDATION_FAILED', 400, { errors }));
}
