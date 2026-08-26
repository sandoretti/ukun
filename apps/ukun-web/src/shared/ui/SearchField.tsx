import { Form, InputGroup } from 'react-bootstrap';

import { useCommittedInput } from '../hooks/useCommittedInput';
import { useFieldAria } from '../hooks/useFieldAria';
import { Icon } from './Icon';

type SearchFieldProps = {
  /**
   * The accessible name of the field. Rendered **visually hidden**: inside a filter toolbar the
   * magnifier and the placement already say what the control is, and a label stacked on top of every
   * control is what turns one row into three. It is never dropped — a control without a name is a
   * control a screen reader cannot announce.
   */
  label: string;
  /** What is searched, said under the control. Not a placeholder: a placeholder disappears. */
  placeholder?: string;
  /** The committed value — what the URL holds, not what has been typed. */
  value: string;
  onCommit: (value: string) => void;
};

/**
 * The text field of a filter toolbar: a search box with its magnifier, committed on blur or Enter.
 *
 * It is **not** `SearchBox`, and the difference is the behaviour and not the looks: that one is the
 * catalogue's ARIA combobox with a suggestion list, a keyboard contract and a query behind it. This
 * one filters a table that is already on screen and has no list to open, so it stays a plain input —
 * a combobox with nothing to suggest is a promise the control does not keep.
 */
export function SearchField({ label, placeholder, value, onCommit }: SearchFieldProps) {
  const { controlId } = useFieldAria({});
  const committed = useCommittedInput(value, onCommit);

  return (
    <Form.Group>
      <Form.Label htmlFor={controlId} className="visually-hidden">
        {label}
      </Form.Label>
      <InputGroup>
        {/* Decorative: whatever the magnifier means is already in the label above it. */}
        <InputGroup.Text>
          <Icon name="search" size="sm" />
        </InputGroup.Text>
        <Form.Control
          id={controlId}
          type="search"
          autoComplete="off"
          spellCheck={false}
          placeholder={placeholder}
          {...committed}
        />
      </InputGroup>
    </Form.Group>
  );
}
