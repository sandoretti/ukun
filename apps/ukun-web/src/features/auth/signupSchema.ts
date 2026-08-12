import { z } from 'zod';

import type { FieldMessageKeys } from '../../shared/forms/applyServerErrors';
import {
  SHARED_FIELD_MESSAGE_KEYS,
  addressSchema,
  cardToSave,
  favoriteStoreIdSchema,
  lastNameSchema,
  nameSchema,
  newCardSchema,
  requiredCardNumber,
} from '../../shared/forms/customerFields';
import type { SignupRequest } from './api/queries';

/** The bounds of `SignupRequest`, used by the schema and by the controls' `maxLength`/`minLength`. */
export const EMAIL_MAX = 255;
export const PASSWORD_MIN = 12;

/**
 * What the API can reject, mapped to the sentence each field is painted with.
 *
 * Address and card come from `shared/forms/customerFields` already keyed into `common` — the profile
 * paints the very same rejections. The credentials are this screen's own: they are the only fields of
 * the signup that no other screen asks for, because the email and the password belong to `ukun-auth`
 * and the profile cannot edit them.
 */
export const FIELD_MESSAGE_KEYS = {
  ...SHARED_FIELD_MESSAGE_KEYS,
  email: 'validation.email',
  password: 'validation.password',
} as const satisfies FieldMessageKeys;

/**
 * The client-side half of RF-06, and **nothing more than `SignupRequest` already says**: same
 * lengths, same patterns, same bounds. A rule invented here would be a rule the API does not enforce
 * — and one it does enforce and this omits comes back as a `400` with the field already named, which
 * is why both halves point at the same message keys.
 *
 * Two things this form deliberately does not ask for:
 *
 * - **The CVV**, here or anywhere else. Storing it is forbidden under any circumstance
 *   (`docs/08-identidad-y-seguridad.md` §5), so there is nothing to collect it for. The number is
 *   asked for and stops in `toSignupRequest`, which returns the contract's own request and has
 *   nowhere to put it.
 * - **The language.** `SignupRequest` has no `locale` (ADR-0019): a new customer starts in the
 *   language the interface is already speaking, and changes it in the profile if it is wrong.
 */
export const signupSchema = z.object({
  email: z
    .string()
    .trim()
    .max(EMAIL_MAX, FIELD_MESSAGE_KEYS.email)
    // A single rule and a single sentence: "that is not an email address". Splitting it into
    // "missing" and "malformed" would be a taxonomy of ways to be wrong, not help.
    .pipe(z.email(FIELD_MESSAGE_KEYS.email)),
  /**
   * A real minimum and **no artificial maximum**, aligned with NIST SP 800-63B (`docs/08` §8). No
   * composition rules either: they push people towards `Password1!` and buy nothing.
   */
  password: z.string().min(PASSWORD_MIN, FIELD_MESSAGE_KEYS.password),
  name: nameSchema,
  lastName: lastNameSchema,
  address: addressSchema,
  // Neither `last4` nor the brand are asked for: they are read off the number below, and the card is
  // only complete once that number checks out.
  card: newCardSchema,
  cardNumber: requiredCardNumber,
  favoriteStoreId: favoriteStoreIdSchema,
});

export type SignupFormValues = z.infer<typeof signupSchema>;

/**
 * What the form starts as. The expiry defaults to the current month and year rather than to an empty
 * control: every card has one, the select cannot be blank, and this month is the only value that is
 * never a guess about somebody else's card.
 */
export function emptySignupValues(now: Date = new Date()): SignupFormValues {
  return {
    email: '',
    password: '',
    name: '',
    lastName: '',
    address: { street: '', city: '', province: '', postalCode: '' },
    card: { last4: '', brand: 'OTHER', expMonth: now.getMonth() + 1, expYear: now.getFullYear() },
    cardNumber: '',
    favoriteStoreId: null,
  };
}

/**
 * The form, turned into the body of the `POST`.
 *
 * **This is where the card number stops.** It is destructured out and the return type is the
 * contract's own `SignupRequest`, so a number typed into this screen cannot reach the network by
 * accident: it would have to be added to a type that has nowhere to put it. What travels is what the
 * contract carries — four digits, a brand and an expiry — derived here by `cardToSave` from a number
 * the schema has already checked.
 */
export function toSignupRequest({ cardNumber, ...values }: SignupFormValues): SignupRequest {
  return { ...values, card: cardToSave(values.card, cardNumber) };
}
