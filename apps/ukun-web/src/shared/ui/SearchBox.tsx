import { useId, useState, type FocusEvent, type FormEvent, type KeyboardEvent, type ReactNode } from 'react';
import { Button, Form, InputGroup } from 'react-bootstrap';

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
  className?: string;
};

/**
 * The search field with its suggestion list, following the ARIA combobox-with-listbox pattern:
 * the input keeps the focus at all times and `aria-activedescendant` moves the selection, so the
 * arrow keys work without the focus ever leaving the field.
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
  className = '',
}: SearchBoxProps) {
  const baseId = useId();
  const inputId = `${baseId}-input`;
  const listboxId = `${baseId}-listbox`;
  const optionId = (index: number) => `${baseId}-option-${index}`;

  const [isOpen, setIsOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);

  const expanded = isOpen && suggestions.length > 0;

  function close() {
    setIsOpen(false);
    setActiveIndex(-1);
  }

  function select(suggestion: SearchSuggestion) {
    close();
    onSelect(suggestion);
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    close();
    onSubmit(value);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (suggestions.length === 0) return;
      setIsOpen(true);
      const step = event.key === 'ArrowDown' ? 1 : -1;
      setActiveIndex((current) => {
        // `-1` (nothing highlighted) is one more state of the cycle, so going past either end
        // returns to the plain typed text instead of dead-ending on the first or last row.
        const total = suggestions.length;
        const state = current + 1;
        return ((state + step + total + 1) % (total + 1)) - 1;
      });
      return;
    }
    if (event.key === 'Enter' && expanded && activeIndex >= 0) {
      // Only when something is highlighted: with nothing chosen, Enter submits the search.
      event.preventDefault();
      select(suggestions[activeIndex]);
      return;
    }
    if (event.key === 'Escape') {
      // First Escape closes the list; with the list already closed it clears the field.
      if (expanded) {
        event.preventDefault();
        close();
      } else if (value !== '') {
        onChange('');
      }
    }
  }

  /** The list only closes when the focus leaves the whole component, not when it moves inside it. */
  function handleBlur(event: FocusEvent<HTMLDivElement>) {
    if (!event.currentTarget.contains(event.relatedTarget)) {
      close();
    }
  }

  return (
    <div className={`search-box ${className}`} onBlur={handleBlur}>
      <Form role="search" onSubmit={handleSubmit}>
        <Form.Label htmlFor={inputId} className="visually-hidden">
          {label}
        </Form.Label>
        <InputGroup>
          <Form.Control
            id={inputId}
            type="search"
            role="combobox"
            autoComplete="off"
            aria-expanded={expanded}
            aria-controls={listboxId}
            aria-autocomplete="list"
            aria-activedescendant={expanded && activeIndex >= 0 ? optionId(activeIndex) : undefined}
            placeholder={placeholder}
            value={value}
            onChange={(event) => {
              onChange(event.target.value);
              setIsOpen(true);
              setActiveIndex(-1);
            }}
            onKeyDown={handleKeyDown}
          />
          <Button type="submit" variant="light" aria-label={submitLabel}>
            <Icon name="search" size="sm" />
          </Button>
        </InputGroup>
      </Form>

      {expanded && (
        <div className="search-suggestions">
          <ul id={listboxId} role="listbox" aria-label={label} className="list-unstyled mb-0">
            {suggestions.map((suggestion, index) => (
              <li
                key={suggestion.id}
                id={optionId(index)}
                role="option"
                aria-selected={index === activeIndex}
                className="search-suggestion d-flex justify-content-between gap-3 px-3 py-2"
                // The mouse must not blur the input before the click lands, or the list closes first.
                onMouseDown={(event) => event.preventDefault()}
                onMouseEnter={() => setActiveIndex(index)}
                onClick={() => select(suggestion)}
              >
                <span className="text-truncate">{suggestion.label}</span>
                {suggestion.meta !== undefined && (
                  <span className="text-body-secondary text-tabular-nums flex-shrink-0">{suggestion.meta}</span>
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
