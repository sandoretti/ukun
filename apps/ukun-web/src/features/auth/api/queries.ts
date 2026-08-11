import { useMutation } from '@tanstack/react-query';

import { http } from '../../../shared/api/http';
import type { components } from '../../../types/api';

/** The body of `POST /api/signup` (RF-06). The password travels here and nowhere else. */
export type SignupRequest = components['schemas']['SignupRequest'];

/**
 * How far the signup saga got. Both are successes and the difference between them is real
 * (`docs/08-identidad-y-seguridad.md` §3):
 *
 * - `completed` (`201`) — the account is active and the person can sign in right now.
 * - `activationPending` (`202`) — the customer is written but the activation was not confirmed.
 *   Repeating the same signup finishes it, and nothing has been duplicated.
 */
export type SignupOutcome = 'completed' | 'activationPending';

/**
 * Signing up, which this service orchestrates rather than `ukun-auth` (ADR-0006, `docs/08` §3): it
 * crosses two databases and is resolved as a saga, so what comes back is how far that saga got and
 * not a customer.
 *
 * Neither answer has a body, on purpose — the customer's own data comes from `/api/me/profile` once
 * there is a token, and echoing an id to an anonymous caller would hand out an identifier before
 * there is anybody entitled to it. So the outcome is read off the **status**.
 *
 * Nothing is invalidated here and no cache entry is written: there is no session yet, and the reader
 * is about to leave for the authorization server. `409 EMAIL_ALREADY_EXISTS` and `400
 * VALIDATION_FAILED` reach the form as the `ProblemDetailError` the axios instance already builds.
 */
export function useSignup() {
  return useMutation({
    mutationFn: (request: SignupRequest): Promise<SignupOutcome> =>
      http
        .post<void>('/api/signup', request)
        .then((response) => (response.status === 202 ? 'activationPending' : 'completed')),
  });
}
