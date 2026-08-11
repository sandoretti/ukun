import type { ComponentProps, ReactNode } from 'react';
import { Form } from 'react-bootstrap';

import { useFieldAria } from '../hooks/useFieldAria';

type SelectFieldProps = Omit<ComponentProps<typeof Form.Select>, 'id' | 'isInvalid'> & {
  label: string;
  /**
   * Whether the label is drawn. `hidden` still renders it for assistive technology — it is never
   * dropped, only taken off screen — and is for a control that sits inside a group that already names
   * it, like the month and the year of a card expiry under a single «Expiry» heading.
   */
  labelVisibility?: 'visible' | 'hidden';
  /** The error **already resolved to text**: `shared/ui` knows nothing about translation keys. */
  error?: string;
  hint?: string;
  /** The `<option>`s. They are the caller's because only it knows what the closed list is. */
  children: ReactNode;
};

/**
 * The `TextField` of a closed list of values. Same shape, same wiring, a different control — two
 * components instead of a `<TextField as="select">`, because what changes is the kind of thing being
 * asked for and not how it looks (`docs/07-frontend-ux-ui.md` §Cómo se diseña un componente base).
 */
export function SelectField({
  label,
  labelVisibility = 'visible',
  error,
  hint,
  children,
  ...control
}: SelectFieldProps) {
  const { controlId, errorId, hintId, isInvalid, describedBy } = useFieldAria({ error, hint });

  return (
    <Form.Group className="mb-3">
      <Form.Label htmlFor={controlId} className={labelVisibility === 'hidden' ? 'visually-hidden' : undefined}>
        {label}
      </Form.Label>
      <Form.Select id={controlId} isInvalid={isInvalid} aria-describedby={describedBy} {...control}>
        {children}
      </Form.Select>
      {hint !== undefined && (
        <Form.Text id={hintId} className="d-block">
          {hint}
        </Form.Text>
      )}
      <Form.Control.Feedback id={errorId} type="invalid">
        {error}
      </Form.Control.Feedback>
    </Form.Group>
  );
}
