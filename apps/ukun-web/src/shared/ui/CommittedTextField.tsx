import type { ComponentProps, FocusEvent, KeyboardEvent } from 'react';

import { TextField } from './TextField';

type CommittedTextFieldProps = Omit<
  ComponentProps<typeof TextField>,
  'value' | 'defaultValue' | 'onBlur' | 'onKeyDown'
> & {
  /** The value that is **committed** — what the URL holds, not what has been typed. */
  value: string;
  /** Called when the reader leaves the field or presses Enter, and only if the value changed. */
  onCommit: (value: string) => void;
};

/**
 * A text field that reports its value when the reader is **done** with it: on blur, or on Enter.
 *
 * It is the control every typed filter of a listing uses (`docs/07-frontend-ux-ui.md`): there is no
 * "apply" button, and there is no filtering per keystroke either — crossing the field is one request,
 * not forty.
 *
 * The control is **uncontrolled and keyed by the committed value**, which is the part that is easy to
 * get wrong: the input's own draft —what has been typed and not yet committed— is the one piece of
 * state that has no business in the address bar, and keying on the committed value is what makes
 * "clear filters" empty the box without a second copy of the value to keep in sync.
 *
 * It lives in `shared/ui/` because the stock listing and the staff listing both filter this way, and
 * features never import each other. `type="search"` and `autoComplete="off"` are the defaults every
 * caller wanted and can still be overridden; the two handlers cannot, because they are what this
 * component *is*.
 */
export function CommittedTextField({ label, value, onCommit, ...field }: CommittedTextFieldProps) {
  const commit = (next: string) => {
    if (next !== value) {
      onCommit(next);
    }
  };

  return (
    <TextField
      key={value}
      label={label}
      type="search"
      autoComplete="off"
      {...field}
      defaultValue={value}
      onBlur={(event: FocusEvent<HTMLInputElement>) => commit(event.target.value)}
      onKeyDown={(event: KeyboardEvent<HTMLInputElement>) => {
        if (event.key === 'Enter') {
          event.preventDefault();
          commit(event.currentTarget.value);
        }
      }}
    />
  );
}
