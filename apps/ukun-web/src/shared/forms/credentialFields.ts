import { z } from 'zod';

/**
 * The credentials asked for in **two** altas — a customer's (RF-06) and an employee's (RF-11) — with
 * their bounds and their rules written once.
 *
 * They are the same fields because they create the same thing: an account in `ukun-auth`. Two copies
 * of these rules would be two chances of drifting, and the day one of them drifts the symptom is a
 * screen that rejects what the API accepts, or worse, the other way round.
 *
 * The message is a **parameter** and not a constant, because each form resolves its field errors in
 * its own namespace (`signup`, `backoffice`): what these factories share is the rule, not the
 * sentence.
 */

/** The bounds of the contract, used by the schemas and by the controls' `maxLength`/`minLength`. */
export const EMAIL_MAX = 255;

/**
 * A real minimum and **no artificial maximum**, aligned with NIST SP 800-63B (`docs/08` §8). No
 * composition rules either: they push people towards `Password1!` and buy nothing.
 *
 * The number lives here and not in the OpenAPI-generated types because the rule belongs to
 * `ukun-auth`'s `PasswordPolicy` — the service that owns credentials — and the contract only
 * declares it on `SignupRequest`.
 */
export const PASSWORD_MIN = 12;

/**
 * An email address, in a single rule with a single sentence: "that is not an email address".
 * Splitting it into "missing" and "malformed" would be a taxonomy of ways to be wrong, not help.
 */
export const emailSchema = (message: string) =>
  z.string().trim().max(EMAIL_MAX, message).pipe(z.email(message));

/** A password, checked against the only rule the browser can check before the server does. */
export const passwordSchema = (message: string) => z.string().min(PASSWORD_MIN, message);
