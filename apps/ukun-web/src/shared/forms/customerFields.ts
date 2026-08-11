import { z } from 'zod';

import { cardDigits, detectCardBrand, isPlausibleCardNumber } from '../payments/card';
import type { components } from '../../types/api';
import type { FieldMessageKeys } from './applyServerErrors';

type Card = components['schemas']['Card'];
type CardBrand = Card['brand'];

/**
 * The fields a customer is asked for in **two** screens — the profile (RF-05) and the signup (RF-06) —
 * with their bounds, their schemas and their messages written once.
 *
 * They are the same fields because they are the same contract: `UpdateProfileRequest` and
 * `SignupRequest` share `Address` and `Card` in `docs/api/openapi/ukun-api.yaml`. Two copies of these
 * rules would be two chances of drifting from it, and the day one of them drifts the symptom is a
 * screen that rejects what the API accepts, or worse, the other way round.
 *
 * What is **not** here is anything a single screen owns: the language of the profile, the credentials
 * of the signup. Sharing those would be sharing a coincidence.
 */

/**
 * The card brands the contract accepts, in the order the select offers them.
 *
 * `satisfies` ties the list to the generated type: a brand renamed or dropped from the contract stops
 * this file from compiling. A brand *added* to the contract does not — that one shows up as a missing
 * key under `card.brands` in `common.json`.
 */
export const CARD_BRANDS = ['VISA', 'MASTERCARD', 'AMEX', 'OTHER'] as const satisfies readonly CardBrand[];

/** The bounds of the contract, written once and used by the schemas and by the controls' `min`/`max`. */
export const NAME_MAX = 100;
export const ADDRESS_MAX = 255;
export const MONTH_MIN = 1;
export const MONTH_MAX = 12;
export const YEAR_MIN = 2024;
export const YEAR_MAX = 2100;

/**
 * One message per field, and **the same one for every rule of that field**: whether the value is
 * missing, too long or the wrong shape, what the reader needs is the rule the field obeys ("the
 * postcode is exactly five digits"), not a taxonomy of ways to break it.
 *
 * The paths are the ones the API uses in `errors[].field` —`address.postalCode`, `card.expMonth`—
 * which is also how react-hook-form names a nested field, so client and server validation land on the
 * very same key. That is the single place a field error is painted.
 *
 * The keys carry their namespace (`common:`) because the screens that use them resolve field messages
 * in **their own** namespace: an explicit prefix is what lets the shared fieldsets and two different
 * feature schemas all land on the same sentence.
 */
export const SHARED_FIELD_MESSAGE_KEYS = {
  name: 'common:validation.name',
  lastName: 'common:validation.lastName',
  'address.street': 'common:validation.street',
  'address.city': 'common:validation.city',
  'address.province': 'common:validation.province',
  'address.postalCode': 'common:validation.postalCode',
  'card.last4': 'common:validation.last4',
  'card.brand': 'common:validation.brand',
  'card.expMonth': 'common:validation.expMonth',
  'card.expYear': 'common:validation.expYear',
  // Client-side only: the API never sees this field, so it can never send an error for it. It is in
  // the map because the map is also what the schemas build their messages from.
  cardNumber: 'common:validation.cardNumber',
  // The only rule of these forms the browser cannot check: whether that store still exists. The API
  // answers it as a field error (`FieldValidationException`), so it is painted like any other.
  favoriteStoreId: 'common:validation.favoriteStore',
} as const satisfies FieldMessageKeys;

const key = SHARED_FIELD_MESSAGE_KEYS;

const text = (message: string, max: number) => z.string().trim().min(1, message).max(max, message);

/** A person's name, as both screens ask for it. */
export const nameSchema = text(key.name, NAME_MAX);
export const lastNameSchema = text(key.lastName, NAME_MAX);

/** The postal address of `Address` in the contract. One country for now, so no country field. */
export const addressSchema = z.object({
  street: text(key['address.street'], ADDRESS_MAX),
  city: text(key['address.city'], ADDRESS_MAX),
  province: text(key['address.province'], ADDRESS_MAX),
  postalCode: z
    .string()
    .trim()
    .regex(/^[0-9]{5}$/u, key['address.postalCode']),
});

/**
 * The expiry, which is the only part of a card a reader picks by hand — the four digits and the brand
 * are read off the number they type.
 *
 * `valueAsNumber` on an empty control yields `NaN`, which `z.number()` rejects as a wrong type — that
 * is what the first message covers, before the bounds are ever reached.
 */
const expiryFields = {
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
};

/**
 * A card **already on file**: it has four digits and a brand, and they have to be there. This is the
 * profile's, where the form starts from what the API returned.
 */
export const savedCardSchema = z.object({
  last4: z
    .string()
    .trim()
    .regex(/^[0-9]{4}$/u, key['card.last4']),
  brand: z.enum(CARD_BRANDS, { error: key['card.brand'] }),
  ...expiryFields,
});

/**
 * A card **being registered for the first time**, where `last4` and `brand` are still empty because
 * nothing has been typed yet: both are derived from the number at submit time (`cardToSave`), so
 * validating them here would be rejecting a value this form never asks anybody for. What guarantees
 * they end up right is `requiredCardNumber` below — no plausible number, no submit.
 */
export const newCardSchema = z.object({
  last4: z.string(),
  brand: z.enum(CARD_BRANDS, { error: key['card.brand'] }),
  ...expiryFields,
});

export type CardValues = z.infer<typeof savedCardSchema>;

/**
 * The number of a card, which **never reaches the API**: what survives it is `last4` and the brand.
 *
 * Empty is valid when there is already a card on file — most saves of the profile have nothing to do
 * with the card — and invalid when there is not, because a signup with no payment method is a customer
 * who cannot check out.
 */
export const optionalCardNumber = z
  .string()
  .trim()
  .refine((value) => value === '' || isPlausibleCardNumber(cardDigits(value)), key.cardNumber);

export const requiredCardNumber = z
  .string()
  .trim()
  .refine((value) => isPlausibleCardNumber(cardDigits(value)), key.cardNumber);

/** The favourite store, optional in both screens: `null` is a value, and it means "none". */
export const favoriteStoreIdSchema = z.number().int().nullable();

/**
 * The card that will be saved: the one already on file, or what a newly typed number says about
 * itself.
 *
 * Nothing is derived from a number that does not check out — a half-typed one, or one with a mistyped
 * digit that fails Luhn — so a wrong `last4` never replaces a right one, neither on screen nor in the
 * request.
 */
export function cardToSave(card: CardValues, cardNumber: string): CardValues {
  const digits = cardDigits(cardNumber);

  return isPlausibleCardNumber(digits)
    ? { ...card, last4: digits.slice(-4), brand: detectCardBrand(digits) }
    : card;
}

/** The shape the shared card fieldset reads out of the form it is dropped into. */
export type CardFormValues = {
  card: CardValues;
  cardNumber: string;
};

/** The shape the shared address fieldset reads out of the form it is dropped into. */
export type AddressFormValues = {
  address: z.infer<typeof addressSchema>;
};
