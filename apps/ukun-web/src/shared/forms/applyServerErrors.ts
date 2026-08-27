import type { FieldPath, FieldValues, UseFormSetError } from 'react-hook-form';

import { toProblem } from '../api/http';

/**
 * Which translation key each field path is painted with, for the fields the server can reject.
 *
 * The keys are paths as the API writes them — `address.postalCode`, `card.expMonth` — which is also
 * how react-hook-form names a nested field, so nothing has to be translated between the two. That is
 * not a coincidence to rely on blindly: `GlobalExceptionHandler` copies Spring's `FieldError.getField()`
 * untouched, and `@Valid` on the nested DTOs is what makes it a dotted path.
 */
export type FieldMessageKeys = Readonly<Record<string, string>>;

/**
 * Spreads the `errors[]` of a `VALIDATION_FAILED` over the form fields that caused them, and says
 * whether every one of them found a home.
 *
 * **The server's `message` is deliberately dropped.** It is fixed English written for the operator
 * (`docs/12-error-contract.md` §1, and the javadoc of `ValidationError` says it again for this very
 * field); what the reader sees is resolved from `messageKeys`, in their language. What travels into
 * the form state is therefore a translation key, exactly like the ones the zod schema sets — one
 * place paints a field error, whoever produced it.
 *
 * A `field` nobody claims is not invented into the form: it would sit in the form state forever,
 * invisible and blocking every later submit. It is reported back instead, so the caller can fall
 * back to the form-level message.
 *
 * The first field that gets an error also gets the focus — `docs/07-frontend-ux-ui.md`
 * §Accesibilidad: "al enviar con errores el foco va al primero".
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  messageKeys: FieldMessageKeys,
): boolean {
  const problem = toProblem(error);

  if (problem.code !== 'VALIDATION_FAILED' || problem.errors.length === 0) {
    return false;
  }

  let placed = 0;

  for (const validationError of problem.errors) {
    const messageKey = messageKeys[validationError.field];
    if (messageKey === undefined) {
      continue;
    }

    setError(
      validationError.field as FieldPath<T>,
      { type: 'server', message: messageKey },
      { shouldFocus: placed === 0 },
    );
    placed += 1;
  }

  return placed === problem.errors.length;
}

/**
 * Paints a `409 DUPLICATE_VALUE` on the field that actually clashed, and says whether it found one.
 *
 * It is separate from {@link applyServerErrors} because it is a different answer: that one spreads a
 * `400 VALIDATION_FAILED` over its `errors[]`, while this is a single unique constraint the use case
 * checked *before* writing so it could name the column — the distinction `docs/12-error-contract.md`
 * draws between `DUPLICATE_VALUE` and `REFERENTIAL_INTEGRITY`. What they share, and the reason both
 * live here, is the invariant: **whatever reaches the form state is a translation key**, so
 * `useFieldMessages` resolves a server rejection and a zod one with the same line.
 *
 * The sentence comes from `messageKeys` and not from `errors:DUPLICATE_VALUE`: the registry's phrase
 * is written for any unique field of any resource and cannot say which one clashed, while the screen
 * knows that a taken `sku` belongs to another product and a taken `name` to another type.
 *
 * **The `field` extension is read, never assumed.** A `field` nobody claims returns `false` rather
 * than being placed on some other control: an error invented on a field the reader is not editing
 * sits in the form state invisible and blocks every later submit.
 */
export function applyDuplicateValue<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  messageKeys: FieldMessageKeys,
): boolean {
  const problem = toProblem(error);

  if (problem.code !== 'DUPLICATE_VALUE') {
    return false;
  }

  const field = problem.extensions.field;
  const messageKey = typeof field === 'string' ? messageKeys[field] : undefined;

  if (messageKey === undefined) {
    return false;
  }

  setError(field as FieldPath<T>, { type: 'server', message: messageKey }, { shouldFocus: true });

  return true;
}
