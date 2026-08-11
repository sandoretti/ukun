import { useId, type ReactNode } from 'react';

type ReadOnlyFieldProps = {
  label: string;
  /** The value, as text. Not a control: there is nothing here to type into. */
  children: ReactNode;
  /** Why it cannot be changed here, and where it is changed instead. */
  hint?: string;
};

/**
 * A value the form shows but does not own — the email of the profile, the number of an order.
 *
 * The third member of the field family, next to `TextField` and `SelectField`, and the one that
 * replaces the trick of a `<input readOnly>`: an input that looks like every other input and refuses
 * every keystroke is a worse answer than a value presented as a value. It keeps the rhythm of the
 * form and the same `label → value → hint` reading order, so nothing looks out of place next to a
 * real field.
 *
 * It is not a `<dl>` because it lives inside a `<fieldset>` among controls; the pairing is carried by
 * `aria-labelledby`, which is what a screen reader announces when it lands on the value.
 */
export function ReadOnlyField({ label, children, hint }: ReadOnlyFieldProps) {
  const labelId = useId();
  const hintId = `${labelId}-hint`;

  return (
    <div className="mb-3">
      <span id={labelId} className="form-label d-block mb-1">
        {label}
      </span>
      <p
        className="mb-1"
        // A group with a name and a description, which is what a `<label>` gives a control.
        role="group"
        aria-labelledby={labelId}
        aria-describedby={hint === undefined ? undefined : hintId}
      >
        {children}
      </p>
      {hint !== undefined && (
        <span id={hintId} className="form-text d-block">
          {hint}
        </span>
      )}
    </div>
  );
}
