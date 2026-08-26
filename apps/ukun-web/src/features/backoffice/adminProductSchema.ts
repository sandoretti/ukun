import { z } from 'zod';

import type { FieldMessageKeys } from '../../shared/forms/applyServerErrors';
import type { CreateProductRequest, UpdateProductRequest } from './api/adminProductKeys';
import { skuSchema } from './stockSchema';

/**
 * Which sentence each field the server can reject is painted with.
 *
 * The paths are the ones the API writes in `errors[].field`, which is also how react-hook-form names
 * the field, so client and server validation land on the very same key — one place where a field
 * error is painted, wherever it came from. No namespace prefix: these forms resolve field messages in
 * `backoffice`, their own.
 *
 * **The SKU is in the map here**, unlike in `stockSchema`: this is the one form in the app that
 * actually types a SKU into a request, so a `VALIDATION_FAILED` naming `sku` is a field to correct
 * and not a disagreement with the contract.
 */
export const FIELD_MESSAGE_KEYS = {
  // The sentence the stock screens already use for the same `Sku` of the same contract: `skuSchema`
  // sets that key on a client-side failure, and a server-side one has to read identically.
  sku: 'validation.sku',
  name: 'products.validation.name',
  description: 'products.validation.description',
  price: 'products.validation.price',
  typeId: 'products.validation.typeId',
} as const satisfies FieldMessageKeys;

/** As long as the column, and as long as the contract says (`CreateProductRequest.name`). */
const NAME_MAX = 255;

/**
 * The `pattern` the contract puts on `Money` (`ukun-api.yaml`, schema `Money`): up to eight whole
 * digits and at most two decimals, as an **exact decimal string**.
 *
 * It is checked here so a mistyped price is rejected before it becomes a request the server would
 * only answer with a `400`. And it never becomes a `number` on the way: money is a string end to end
 * (defect #5), so what the field holds is what travels.
 */
const MONEY_PATTERN = /^\d{1,8}(\.\d{1,2})?$/u;

const nameSchema = z.string().trim().min(1, FIELD_MESSAGE_KEYS.name).max(NAME_MAX, FIELD_MESSAGE_KEYS.name);

/** Required and free text (RF-10). Its line breaks are content, so it is not collapsed, only trimmed. */
const descriptionSchema = z.string().trim().min(1, FIELD_MESSAGE_KEYS.description);

/**
 * A price as the contract takes it, with the comma a Spanish keyboard produces accepted as the
 * decimal separator it is meant to be. Typing `399,99` is not a mistake to correct with an error
 * message; it is the same amount written the way half the readers of this screen write it.
 */
const priceSchema = z
  .string()
  .trim()
  .transform((value) => value.replace(',', '.'))
  .pipe(z.string().regex(MONEY_PATTERN, FIELD_MESSAGE_KEYS.price));

/** An existing type from `GET /api/product-types`. An empty `<select>` arrives as `NaN` and is rejected. */
const typeIdSchema = z
  .number({ error: FIELD_MESSAGE_KEYS.typeId })
  .int(FIELD_MESSAGE_KEYS.typeId)
  .positive(FIELD_MESSAGE_KEYS.typeId);

/**
 * The four fields both forms share. They are the whole of `UpdateProductRequest`, which is not a
 * coincidence: what an admin edits about a product is everything except the three things that are
 * decided elsewhere — the SKU (fixed at creation, ADR-0015), the catalog status (`:discontinue` /
 * `:reactivate`) and the photo (its own sub-resource).
 */
export const updateProductFormSchema = z.object({
  name: nameSchema,
  description: descriptionSchema,
  price: priceSchema,
  typeId: typeIdSchema,
});

export type UpdateProductFormValues = z.infer<typeof updateProductFormSchema>;

/**
 * The add form: the same four fields plus the SKU, which exists **only** here.
 *
 * `skuSchema` comes from `stockSchema` rather than being written again: it is the same `Sku` of the
 * same contract, upper-cased first because the pattern only accepts capitals and typing them is a
 * keystroke, not a different value.
 */
export const createProductFormSchema = updateProductFormSchema.extend({ sku: skuSchema });

export type CreateProductFormValues = z.infer<typeof createProductFormSchema>;

/** The add form, turned into the body of the `POST`. No image and no `isActive`: it is born with neither. */
export function toCreateRequest(values: CreateProductFormValues): CreateProductRequest {
  return {
    sku: values.sku,
    name: values.name,
    description: values.description,
    price: values.price,
    typeId: values.typeId,
  };
}

/** The edit form, turned into the body of the `PUT`. */
export function toUpdateRequest(values: UpdateProductFormValues): UpdateProductRequest {
  return {
    name: values.name,
    description: values.description,
    price: values.price,
    typeId: values.typeId,
  };
}
