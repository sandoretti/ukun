import type { FormEvent, ReactNode } from 'react';
import { Button, Form, InputGroup } from 'react-bootstrap';

import { useCombobox } from '../hooks/useCombobox';
import { Icon } from './Icon';

export type SearchSuggestion = {
  id: string | number;
  /** What goes into the input when this one is chosen, and what is read out loud. */
  label: string;
  /** Secondary column of the row (a price, a category). Decorative. */
  meta?: ReactNode;
};

type SearchBoxProps = {
  /** Accessible name of the field and of the suggestion list. Rendered visually hidden. */
  label: string;
  submitLabel: string;
  placeholder?: string;
  value: string;
  onChange: (value: string) => void;
  onSubmit: (value: string) => void;
  suggestions: SearchSuggestion[];
  onSelect: (suggestion: SearchSuggestion) => void;
  /** Line under the list ("press Enter to see every result"). Purely visual. */
  hint?: string;
  /** Announced in a polite live region, e.g. "3 suggestions". */
  status?: string;
  /**
   * Named variant, not a `big` boolean: `md` is the one in the navigation bar and `lg` the one the
   * landing page leads with, where the field is the main action of the screen and not a corner of
   * the chrome (`docs/07-frontend-ux-ui.md`).
   */
  size?: 'md' | 'lg';
  className?: string;
};

/**
 * The search field with its suggestion list. The keyboard, the open/closed state and the pairing of
 * identifiers are `useCombobox`'s; what is left here is the markup and how it looks.
 *
 * It holds no server state of its own — it receives the suggestions already resolved. Who fetches
 * them is `ProductSearchBox`; this component is the base piece and can serve another list later.
 */
export function SearchBox({
  label,
  submitLabel,
  placeholder,
  value,
  onChange,
  onSubmit,
  suggestions,
  onSelect,
  hint,
  status,
  size = 'md',
  className = '',
}: SearchBoxProps) {
  const combobox = useCombobox({
    optionCount: suggestions.length,
    onSelect: (index) => onSelect(suggestions[index]),
    // Escape on a closed list clears the field; on an empty one there is nothing to clear.
    onCancel: () => {
      if (value !== '') {
        onChange('');
      }
    },
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    combobox.close();
    onSubmit(value);
  }

  // `md` is Bootstrap's own default size and has no class of its own, so it travels as `undefined`.
  const controlSize = size === 'lg' ? 'lg' : undefined;

  return (
    <div className={`search-box search-box--${size} ${className}`} onBlur={combobox.handleBlur}>
      <Form role="search" onSubmit={handleSubmit}>
        <Form.Label htmlFor={combobox.inputId} className="visually-hidden">
          {label}
        </Form.Label>
        <InputGroup size={controlSize}>
          <Form.Control
            id={combobox.inputId}
            type="search"
            role="combobox"
            autoComplete="off"
            aria-expanded={combobox.expanded}
            aria-controls={combobox.listboxId}
            aria-autocomplete="list"
            aria-activedescendant={combobox.activeDescendantId}
            placeholder={placeholder}
            value={value}
            onChange={(event) => {
              onChange(event.target.value);
              combobox.open();
            }}
            onKeyDown={combobox.handleKeyDown}
          />
          {/* En la barra el campo va sobre el verde de marca y un botón verde se pierde en él, así
              que ahí es neutro; en la portada va sobre banda clara y es la llamada a la acción. */}
          <Button type="submit" variant={size === 'lg' ? 'primary' : 'light'} aria-label={submitLabel}>
            <Icon name="search" size={size === 'lg' ? 'md' : 'sm'} />
          </Button>
        </InputGroup>
      </Form>

      {combobox.expanded && (
        <div className="search-suggestions">
          <ul id={combobox.listboxId} role="listbox" aria-label={label} className="list-unstyled mb-0">
            {suggestions.map((suggestion, index) => (
              <li
                key={suggestion.id}
                id={combobox.optionId(index)}
                role="option"
                aria-selected={index === combobox.activeIndex}
                className="search-suggestion d-flex justify-content-between gap-3 px-3 py-2"
                // The mouse must not blur the input before the click lands, or the list closes first.
                onMouseDown={(event) => event.preventDefault()}
                onMouseEnter={() => combobox.setActiveIndex(index)}
                onClick={() => combobox.select(index)}
              >
                <span className="text-truncate">{suggestion.label}</span>
                {suggestion.meta !== undefined && (
                  <span className="text-body-secondary text-tabular-nums flex-shrink-0">
                    {suggestion.meta}
                  </span>
                )}
              </li>
            ))}
          </ul>
          {hint !== undefined && (
            <p className="border-top small text-body-secondary mb-0 px-3 py-2">{hint}</p>
          )}
        </div>
      )}

      {/* What changes without the user looking at it has to be announced (docs/07 §Accesibilidad). */}
      <span className="visually-hidden" role="status">
        {status}
      </span>
    </div>
  );
}
