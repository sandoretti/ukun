import { z } from 'zod';

import type { FieldMessageKeys } from '../../../shared/forms/applyServerErrors';
import type { ProductTypeRequest } from './api/productTypeKeys';

/**
 * Which sentence the one field the server can reject is painted with.
 *
 * The path is the one the API writes in `errors[].field`, which is also how react-hook-form names
 * the control, so client-side and server-side validation land on the very same key. No namespace
 * prefix: this form resolves its field messages in `backoffice`, its own.
 */
export const FIELD_MESSAGE_KEYS = {
  name: 'productTypes.validation.name',
} as const satisfies FieldMessageKeys;

/**
 * As long as the contract says (`ProductTypeRequest.name`, `@Size(max = 255)` on the record behind
 * it). Exported because the control stops the typing at the same number: one rule, read by the schema
 * that rejects and by the field that prevents.
 */
export const NAME_MAX = 255;

/**
 * The only field a type has (RF-13): required and unique.
 *
 * It is trimmed here for the same reason the use case trims it server-side — «Sofás» and «Sofás »
 * are the same category, and letting the second one through would create a duplicate the `UNIQUE`
 * index cannot catch. Uniqueness itself is **not** checked here: only the server can know it, and it
 * answers `409 DUPLICATE_VALUE` naming the field.
 */
export const productTypeFormSchema = z.object({
  name: z.string().trim().min(1, FIELD_MESSAGE_KEYS.name).max(NAME_MAX, FIELD_MESSAGE_KEYS.name),
});

export type ProductTypeFormValues = z.infer<typeof productTypeFormSchema>;

/** The form, turned into the body of the `POST` or the `PUT` — they take the same one. */
export function toRequest(values: ProductTypeFormValues): ProductTypeRequest {
  return { name: values.name };
}
