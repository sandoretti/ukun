import { z } from 'zod';

import type { FieldMessageKeys } from '../../../shared/forms/applyServerErrors';
import { emailSchema, passwordSchema } from '../../../shared/forms/credentialFields';
import {
  SHARED_FIELD_MESSAGE_KEYS,
  lastNameSchema,
  nameSchema,
} from '../../../shared/forms/customerFields';
import type { CreateEmployeeRequest } from './api/employeeKeys';

/** How long a job title can be, as `CreateEmployeeRequest` declares it. */
export const JOB_TITLE_MAX = 50;

/**
 * What the API can reject, mapped to the sentence each field is painted with.
 *
 * The name and the last name come from `shared/forms/customerFields` already keyed into `common:`,
 * because they are the same two fields the customer screens ask for and the same rejection. The rest
 * are this form's own, resolved in `backoffice` and kept under `employees.` so they cannot collide
 * with the stock screens' `validation.*` in that same namespace.
 *
 * The paths are the ones the API writes in `errors[].field`, which is also how react-hook-form names
 * a field, so client and server validation land on the very same key.
 */
export const FIELD_MESSAGE_KEYS = {
  name: SHARED_FIELD_MESSAGE_KEYS.name,
  lastName: SHARED_FIELD_MESSAGE_KEYS.lastName,
  email: 'employees.validation.email',
  password: 'employees.validation.password',
  jobTitle: 'employees.validation.jobTitle',
} as const satisfies FieldMessageKeys;

/**
 * The client-side half of the alta of RF-11, and **nothing more than `CreateEmployeeRequest` already
 * says**: same lengths, same rules, same bounds. A rule invented here would be a rule the API does
 * not enforce — and one it does enforce and this omits comes back as a `400` with the field already
 * named, which is why both halves point at the same message keys.
 *
 * **There is no `storeId` and there must not be one** (`docs/08-identidad-y-seguridad.md` §3 and §4,
 * RF-11): the new employee's store is always the store of the admin creating them, resolved
 * server-side from their `account_id`. A field for it would turn an authorisation fact into
 * something the browser gets to choose.
 *
 * Nor is there a `roles` array: `EMPLOYEE` is implied and `ADMIN` is the only real choice inside an
 * employee (RF-12), which is exactly what the checkbox asks.
 */
export const employeeSchema = z.object({
  email: emailSchema(FIELD_MESSAGE_KEYS.email),
  password: passwordSchema(FIELD_MESSAGE_KEYS.password),
  name: nameSchema,
  lastName: lastNameSchema,
  jobTitle: z
    .string()
    .trim()
    .min(1, FIELD_MESSAGE_KEYS.jobTitle)
    .max(JOB_TITLE_MAX, FIELD_MESSAGE_KEYS.jobTitle),
  isAdmin: z.boolean(),
});

export type EmployeeFormValues = z.infer<typeof employeeSchema>;

/**
 * What the form starts as. `isAdmin` defaults to `false` like the contract does: an administrator is
 * the exception in a store, and a checkbox that arrives ticked is a privilege granted by inattention.
 */
export function emptyEmployeeValues(): EmployeeFormValues {
  return { email: '', password: '', name: '', lastName: '', jobTitle: '', isAdmin: false };
}

/**
 * The form, turned into the body of the `POST`.
 *
 * Every field is named rather than spread, and that is the point: a fresh object literal is what
 * TypeScript checks for excess properties, so the day this form grows a control the server never
 * asked for —a store, a role— this stops compiling instead of quietly putting it on the wire.
 */
export function toCreateRequest(values: EmployeeFormValues): CreateEmployeeRequest {
  return {
    email: values.email,
    password: values.password,
    name: values.name,
    lastName: values.lastName,
    jobTitle: values.jobTitle,
    isAdmin: values.isAdmin,
  };
}
