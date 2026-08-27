import type { FocusEvent, KeyboardEvent } from 'react';

/**
 * A text control that reports its value when the reader is **done** with it: on blur, or on Enter.
 *
 * It is how every typed filter of a listing behaves (`docs/07-frontend-ux-ui.md`): there is no
 * "apply" button, and there is no filtering per keystroke either — crossing the field is one
 * request, not forty.
 *
 * What it returns is the props of an **uncontrolled** control keyed by the committed value. That is
 * the part that is easy to get wrong: the input's own draft —what has been typed and not yet
 * committed— is the one piece of state that has no business in the address bar, and keying on the
 * committed value is what makes "clear filters" empty the box without a second copy of the value to
 * keep in sync.
 *
 * A hook and not a component. It had two consumers that did not look alike —a plain labelled field
 * and a search box with its magnifier— and today it has one, `SearchField`, since the back-office
 * catalogue stopped stacking its filters (issue #148) and `FilterTextField` went with the stack. It
 * stays a hook because what it holds is the *contract* of a typed filter, which is what the next
 * listing has to find rather than reinvent — the looks of the control are the part that varies.
 */
export function useCommittedInput(value: string, onCommit: (value: string) => void) {
  const commit = (next: string) => {
    if (next !== value) {
      onCommit(next);
    }
  };

  return {
    key: value,
    defaultValue: value,
    onBlur: (event: FocusEvent<HTMLInputElement>) => commit(event.target.value),
    onKeyDown: (event: KeyboardEvent<HTMLInputElement>) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        commit(event.currentTarget.value);
      }
    },
  };
}
