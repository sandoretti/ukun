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
