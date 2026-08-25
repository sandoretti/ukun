import { z } from 'zod';

import type { FieldMessageKeys } from '../../shared/forms/applyServerErrors';
import { SKU_PATTERN, type CreateStockLineRequest, type UpdateStockLineRequest } from './api/stockKeys';

/**
 * What the API can reject on the quantity, mapped to the sentence it is painted with.
 *
 * The path is the one the API uses in `errors[].field`, which is also how react-hook-form names the
 * field, so client and server validation land on the very same key — one place where a field error is
 * painted, wherever it came from. It carries no namespace prefix because both forms resolve field
 * messages in `backoffice`, their own.
 *
 * The SKU is not in this map on purpose: the only form with a SKU field resolves it before there is
 * anything to create, so a `VALIDATION_FAILED` naming `sku` is not a field to correct — it is a
 * disagreement with the contract, and it gets reported as the error it is.
 */
export const FIELD_MESSAGE_KEYS = {
  quantity: 'validation.quantity',
} as const satisfies FieldMessageKeys;

/** How many units a store can hold of one product, as `int32` in the contract. */
const QUANTITY_MAX = 2_147_483_647;

/**
 * A quantity, and **nothing more than the contract already says**: a non-negative whole number
 * (RF-09). Zero is valid and means what it says — none left in this store, with the product still
 * managed here. Removing the row is `DELETE`, which is a different action and says so on screen.
 *
 * Every rule of the field carries the same sentence: what the reader needs is the rule the field
 * obeys, not a taxonomy of ways to break it.
 */
export const quantitySchema = z
  .number({ error: FIELD_MESSAGE_KEYS.quantity })
  .int(FIELD_MESSAGE_KEYS.quantity)
  .min(0, FIELD_MESSAGE_KEYS.quantity)
  .max(QUANTITY_MAX, FIELD_MESSAGE_KEYS.quantity);

/**
 * A SKU as the contract defines it (`Sku`, ADR-0015), upper-cased first because the pattern only
 * accepts capitals and typing them is a keystroke, not a different search.
 *
 * Checking it here is what keeps a half-typed SKU from becoming a request the API answers with a
 * `400` — which is a worse thing to show than the rule the field obeys.
 */
export const skuSchema = z.string().trim().toUpperCase().regex(SKU_PATTERN, 'validation.sku');

/**
 * The quantity form, shared by the two screens that write one: editing a line and adding one. It is
 * the same field with the same rule, so it is the same schema.
 */
export const quantityFormSchema = z.object({ quantity: quantitySchema });

export type QuantityFormValues = z.infer<typeof quantityFormSchema>;

/** The form that resolves a product before there is a line to create. */
export const skuFormSchema = z.object({ sku: skuSchema });

export type SkuFormValues = z.infer<typeof skuFormSchema>;

/**
 * A SKU arriving from outside the form — a query parameter, someone's pasted link — normalised or
 * dropped. Anything that does not match the contract's pattern becomes an empty string rather than a
 * request for the API to reject.
 */
export function readSku(value: string | null): string {
  const parsed = skuSchema.safeParse(value ?? '');

  return parsed.success ? parsed.data : '';
}

/**
 * The add form, turned into the body of the `POST`. The SKU comes from the product that was already
 * resolved, not from the form: creating a line is by business identifier (ADR-0015), and the one that
 * travels is the one the server itself answered with.
 */
export function toCreateRequest(sku: string, values: QuantityFormValues): CreateStockLineRequest {
  return { sku, quantity: values.quantity };
}

/**
 * The edit form plus the `version` that was read, turned into the body of the `PUT`.
 *
 * **The version is not part of the form**: it is not something the employee types, changes or should
 * ever see as a field. It travels from the row the screen is showing — which, after a conflict, is
 * the row the server just sent back — and that is what makes ADR-0008's optimistic check work rather
 * than merely happen.
 */
export function toUpdateRequest(values: QuantityFormValues, version: number): UpdateStockLineRequest {
  return { quantity: values.quantity, version };
}
