import { z } from 'zod';

import { SUPPORTED_LANGUAGES } from '../../i18n/languages';
import type { FieldMessageKeys } from '../../shared/forms/applyServerErrors';
import type { components } from '../../types/api';
import type { CustomerProfile } from './api/profileKeys';

type Locale = components['schemas']['Locale'];
type CardBrand = components['schemas']['Card']['brand'];

/**
 * The card brands the contract accepts, in the order the select offers them.
 *
 * `satisfies` ties the list to the generated type: a brand renamed or dropped from the contract stops
 * this file from compiling. A brand *added* to the contract does not — that one shows up as a missing
 * key under `card.brands` in `profile.json`.
 */
export const CARD_BRANDS = ['VISA', 'MASTERCARD', 'AMEX', 'OTHER'] as const satisfies readonly CardBrand[];

/**
 * The language list of the interface **is** the contract's `Locale`, checked here rather than
 * assumed: `SUPPORTED_LANGUAGES` drives the switcher in the bar and this select saves into a column.
 */
const LOCALES = SUPPORTED_LANGUAGES satisfies readonly Locale[];

/** The bounds of the contract, written once and used by the schema and by the controls' `min`/`max`. */
export const NAME_MAX = 100;
export const ADDRESS_MAX = 255;
export const MONTH_MIN = 1;
export const MONTH_MAX = 12;
export const YEAR_MIN = 2024;
export const YEAR_MAX = 2100;

/**
 * One message per field, in the `profile` namespace, and **the same one for every rule of that
 * field**: whether the value is missing, too long or the wrong shape, what the reader needs is the
 * rule the field obeys ("the postcode is exactly five digits"), not a taxonomy of ways to break it.
 *
 * The paths are the ones the API uses in `errors[].field` —`address.postalCode`, `card.expMonth`—
 * which is also how react-hook-form names a nested field, so client and server validation land on
 * the very same key. That is the single place a field error is painted.
 */
export const FIELD_MESSAGE_KEYS = {
  name: 'validation.name',
  lastName: 'validation.lastName',
  locale: 'validation.locale',
  'address.street': 'validation.street',
  'address.city': 'validation.city',
  'address.province': 'validation.province',
  'address.postalCode': 'validation.postalCode',
  'card.last4': 'validation.last4',
  'card.brand': 'validation.brand',
  'card.expMonth': 'validation.expMonth',
  'card.expYear': 'validation.expYear',
  // The only rule of the form the browser cannot check: whether that store still exists. The API
  // answers it as a field error (`FieldValidationException`), so it is painted like any other.
  favoriteStoreId: 'validation.favoriteStore',
} as const satisfies FieldMessageKeys;

const key = FIELD_MESSAGE_KEYS;

const text = (message: string, max: number) => z.string().trim().min(1, message).max(max, message);

/**
 * The client-side half of RF-05, and **nothing more than the contract already says**: the same
 * lengths, the same patterns and the same bounds as `UpdateProfileRequest` in
 * `docs/api/openapi/ukun-api.yaml`. A rule invented here would be a rule the API does not enforce.
 *
 * There is no field for the card number or the CVV, here or anywhere else in this feature: storing
 * them is the PCI-DSS violation of the legacy schema (`docs/08-identidad-y-seguridad.md` §5), and a
 * field that collects a PAN "just to discard it" still puts it in memory, in the DOM and one bug
 * away from a log.
 */
export const profileSchema = z.object({
  name: text(key.name, NAME_MAX),
  lastName: text(key.lastName, NAME_MAX),
  locale: z.enum(LOCALES, { error: key.locale }),
  address: z.object({
    street: text(key['address.street'], ADDRESS_MAX),
    city: text(key['address.city'], ADDRESS_MAX),
    province: text(key['address.province'], ADDRESS_MAX),
    postalCode: z
      .string()
      .trim()
      .regex(/^[0-9]{5}$/, key['address.postalCode']),
  }),
  card: z.object({
    last4: z
      .string()
      .trim()
      .regex(/^[0-9]{4}$/, key['card.last4']),
    brand: z.enum(CARD_BRANDS, { error: key['card.brand'] }),
    // `valueAsNumber` on an empty control yields `NaN`, which `z.number()` rejects as a wrong type —
    // that is what the first message covers, before the bounds are ever reached.
    expMonth: z
      .number({ error: key['card.expMonth'] })
      .int(key['card.expMonth'])
      .min(MONTH_MIN, key['card.expMonth'])
      .max(MONTH_MAX, key['card.expMonth']),
    expYear: z
      .number({ error: key['card.expYear'] })
      .int(key['card.expYear'])
      .min(YEAR_MIN, key['card.expYear'])
      .max(YEAR_MAX, key['card.expYear']),
  }),
  // `null` is a value here and not a missing field: it is how the favourite store is cleared.
  favoriteStoreId: z.number().int().nullable(),
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
  };
}
