import type { FocusEvent, KeyboardEvent } from 'react';

import { TextField } from './TextField';

type FilterTextFieldProps = {
  label: string;
  hint?: string;
  /** The committed value — the one in the URL, which is where a listing's state lives. */
  value: string;
  /** Called only when what was typed is actually different from what is committed. */
  onCommit: (value: string) => void;
};

/**
 * A typed filter of a listing: it commits when you leave the field or press Enter, and never per
 * keystroke.
 *
 * That is what `docs/07-frontend-ux-ui.md` prescribes for a typed filter —crossing the field is one
 * request, not forty— and it was written twice, once for the stock table and once for the
 * back-office catalog, with a copy of the same two handlers each time. It lives in `shared/ui/`
 * because that is where a component two features reach for goes, and because the next listing with a
 * text filter should find it instead of writing the pair a third time.
 *
 * The control is uncontrolled and **keyed by the committed value**, which is the part that is easy to
 * get wrong: the input's own draft —what has been typed and not yet committed— is the one piece of
 * state that has no business in the address bar, and keying on the committed value is what makes
 * "clear filters" empty the box without a second copy of the value to keep in sync.
 */
export function FilterTextField({ label, hint, value, onCommit }: FilterTextFieldProps) {
  const commit = (typed: string) => {
    if (typed !== value) {
      onCommit(typed);
    }
  };

  return (
    <TextField
      key={value}
      label={label}
      hint={hint}
      type="search"
      autoComplete="off"
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
