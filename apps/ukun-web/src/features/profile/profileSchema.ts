import { z } from 'zod';

import { SUPPORTED_LANGUAGES } from '../../i18n/languages';
import type { FieldMessageKeys } from '../../shared/forms/applyServerErrors';
import {
  SHARED_FIELD_MESSAGE_KEYS,
  addressSchema,
  cardToSave,
  favoriteStoreIdSchema,
  lastNameSchema,
  nameSchema,
  optionalCardNumber,
  savedCardSchema,
} from '../../shared/forms/customerFields';
import type { components } from '../../types/api';
import type { CustomerProfile, UpdateProfileRequest } from '../../shared/api/profile';

type Locale = components['schemas']['Locale'];

/**
 * The language list of the interface **is** the contract's `Locale`, checked here rather than
 * assumed: `SUPPORTED_LANGUAGES` drives the switcher in the bar and this select saves into a column.
 */
const LOCALES = SUPPORTED_LANGUAGES satisfies readonly Locale[];

/**
 * What the API can reject, mapped to the sentence each field is painted with.
 *
 * Everything this form shares with the signup comes from `shared/forms/customerFields` already keyed
 * into `common`; the only one that is this screen's own is the language, because the signup does not
 * ask for it (`SignupRequest` has no `locale`).
 */
export const FIELD_MESSAGE_KEYS = {
  ...SHARED_FIELD_MESSAGE_KEYS,
  locale: 'validation.locale',
} as const satisfies FieldMessageKeys;

/**
 * The client-side half of RF-05, and **nothing more than the contract already says**: the same
 * lengths, the same patterns and the same bounds as `UpdateProfileRequest` in
 * `docs/api/openapi/ukun-api.yaml`. A rule invented here would be a rule the API does not enforce.
 *
 * There is no field for the CVV, here or anywhere else: storing it is forbidden under any
 * circumstance (`docs/08-identidad-y-seguridad.md` §5), so there is nothing to collect it for. The
 * card number **is** asked for —somebody replacing their card types the number, not its last four
 * digits— and it is the one field of this schema that never becomes part of a request: `toRequest`
 * is where it stops.
 */
export const profileSchema = z.object({
  name: nameSchema,
  lastName: lastNameSchema,
  locale: z.enum(LOCALES, { error: FIELD_MESSAGE_KEYS.locale }),
  address: addressSchema,
  card: savedCardSchema,
  // `null` is a value here and not a missing field: it is how the favourite store is cleared.
  favoriteStoreId: favoriteStoreIdSchema,
  /**
   * The number of a **new** card, and the only field of this form that never reaches the API.
   *
   * It is empty unless somebody is replacing their card, and what survives it is `last4` and the
   * brand — see `cardToSave`. Empty is valid: most saves of this form have nothing to do with the
   * card.
   */
  cardNumber: optionalCardNumber,
});

/**
 * What the form holds — and, unchanged, the body of the `PUT`: `useUpdateMyProfile` takes the
 * contract's `UpdateProfileRequest`, so the day the schema and the contract disagree, the call site
 * stops compiling.
 */
export type ProfileFormValues = z.infer<typeof profileSchema>;

/**
 * The profile as the form holds it. `email` is deliberately left out: it does not travel in
 * `UpdateProfileRequest` because the credential lives in `ukun-auth`, and a field that is not part
 * of the request has no business being part of the form state either — the screen paints it read-only
 * straight from the query.
 */
export function toFormValues(profile: CustomerProfile): ProfileFormValues {
  return {
    name: profile.name,
    lastName: profile.lastName,
    locale: profile.locale,
    address: { ...profile.address },
    card: { ...profile.card },
    favoriteStoreId: profile.favoriteStoreId,
    // Always empty, and emptied again on every save: there is no card number to show back, because
    // nobody kept one.
    cardNumber: '',
  };
}

/**
 * The form, turned into the body of the `PUT`.
 *
 * **This is the one place the card number stops.** It is destructured out here and the return type is
 * the contract's own `UpdateProfileRequest`, so a number typed into this screen cannot reach the
 * network by accident: it would have to be added to a type that has nowhere to put it. What travels
 * is what the contract has always carried — four digits, a brand and an expiry — and the rest of the
 * number does not leave the browser.
 */
export function toRequest({ cardNumber, ...profile }: ProfileFormValues): UpdateProfileRequest {
  return { ...profile, card: cardToSave(profile.card, cardNumber) };
}
