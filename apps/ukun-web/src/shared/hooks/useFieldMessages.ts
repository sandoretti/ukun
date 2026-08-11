import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';

/** The shape of one entry of react-hook-form's `formState.errors`, whatever the field's type is. */
type FieldError = { message?: string } | undefined;

/**
 * Turns what is in `formState.errors` into text the reader can act on.
 *
 * It rests on a rule this app does not bend: **whatever ends up in the form state is a translation
 * key, never a sentence**. The zod schema puts one there (`profileSchema`), and so does
 * `applyServerErrors` when the API rejects a field — because the API's own `message` is fixed English
 * written for the operator and is never painted (`docs/12-error-contract.md` §1). That is what lets a
 * single line resolve both, and why there is exactly one place a field error becomes words.
 */
export function useFieldMessages(namespace: string) {
  const { t } = useTranslation(namespace);

  return useCallback((error: FieldError) => (error?.message === undefined ? undefined : t(error.message)), [t]);
}
