import type { ComponentProps } from 'react';
import { Form } from 'react-bootstrap';

import { useFieldAria } from '../hooks/useFieldAria';

type TextFieldProps = Omit<ComponentProps<typeof Form.Control>, 'id' | 'isInvalid'> & {
  /** Visible label, always. A placeholder is not a label and never replaces one. */
  label: string;
  /** The error **already resolved to text**: `shared/ui` knows nothing about translation keys. */
  error?: string;
  /** Helper line under the control, announced with the field. */
  hint?: string;
};

/**
 * One text control with its label, its hint and its inline error, wired together.
 *
 * The rest of the props travel to `Form.Control` untouched, which is what lets a caller spread
 * react-hook-form's `{...register('name')}` on top: `name`, `onChange`, `onBlur` and `ref` are just
 * props here — React 19, no `forwardRef` in our own code — and `type`, `autoComplete` or `inputMode`
 * are decided by whoever knows what the field holds, not by this component.
 */
export function TextField({ label, error, hint, ...control }: TextFieldProps) {
  const { controlId, errorId, hintId, isInvalid, describedBy } = useFieldAria({ error, hint });

  return (
    <Form.Group className="mb-3">
      <Form.Label htmlFor={controlId}>{label}</Form.Label>
      <Form.Control id={controlId} isInvalid={isInvalid} aria-describedby={describedBy} {...control} />
      {hint !== undefined && (
        <Form.Text id={hintId} className="d-block">
          {hint}
        </Form.Text>
      )}
      {/* `type="invalid"` is Bootstrap's styling hook; what makes the message reach a screen reader
          is the `aria-describedby` above, not the class. */}
      <Form.Control.Feedback id={errorId} type="invalid">
        {error}
      </Form.Control.Feedback>
    </Form.Group>
  );
}
