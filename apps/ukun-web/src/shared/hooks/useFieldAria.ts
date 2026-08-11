import { useId } from 'react';

type FieldAriaInput = {
  /** The error already resolved to text, or `undefined` when the field is fine. */
  error?: string;
  /** The helper line under the control, when there is one. */
  hint?: string;
};

/**
 * The wiring every labelled control needs and nobody should retype: one identifier that pairs
 * `<label for>` with the control, and an `aria-describedby` that points at the hint, at the error, or
 * at both in reading order.
 *
 * It is a hook and not three components because the association is what has to stay right — a field
 * whose error text is on screen but not announced is the failure mode this prevents
 * (`docs/07-frontend-ux-ui.md` §Accesibilidad).
 */
export function useFieldAria({ error, hint }: FieldAriaInput) {
  const id = useId();
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;

  return {
    controlId: id,
    errorId,
    hintId,
    isInvalid: error !== undefined,
    // The hint first: it explains the field, and the error explains the attempt.
    describedBy:
      [hint === undefined ? null : hintId, error === undefined ? null : errorId]
        .filter((value) => value !== null)
        .join(' ') || undefined,
  };
}
