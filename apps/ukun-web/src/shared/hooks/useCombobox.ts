import { useId, useState, type FocusEvent, type KeyboardEvent } from 'react';

/** Nothing highlighted: one more state of the cycle, not the absence of one. See `moveActive`. */
const NONE_ACTIVE = -1;

type UseComboboxOptions = {
  /** How many rows the list has right now. The hook never owns the list, only the pointer into it. */
  optionCount: number;
  /** Picking a row: the caller decides what that means (fill the field, navigate, both). */
  onSelect: (index: number) => void;
  /** `Escape` with the list already closed. The field's own reset — clearing the text, typically. */
  onCancel?: () => void;
};

/**
 * The behaviour of the ARIA combobox-with-listbox pattern, without a single element of markup.
 *
 * The input keeps the focus at all times and the selection travels in `aria-activedescendant`, which
 * is what makes the arrow keys work without the focus ever leaving the field — moving focus into the
 * list instead is the usual mistake, and it breaks typing.
 *
 * It is a hook and not part of `SearchBox` because this part is what every future picker needs (a
 * store, a saved address, a product on an order line) and the field around it is not.
 */
export function useCombobox({ optionCount, onSelect, onCancel }: UseComboboxOptions) {
  const baseId = useId();
  const [isOpen, setIsOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(NONE_ACTIVE);

  /** Open **and** with something to show: an empty list is not an expanded combobox. */
  const expanded = isOpen && optionCount > 0;

  function close() {
    setIsOpen(false);
    setActiveIndex(NONE_ACTIVE);
  }

  function open() {
    setIsOpen(true);
    setActiveIndex(NONE_ACTIVE);
  }

  function select(index: number) {
    close();
    onSelect(index);
  }

  function moveActive(step: 1 | -1) {
    setIsOpen(true);
    setActiveIndex((current) => {
      // `-1` (nothing highlighted) is one more state of the cycle, so going past either end
      // returns to the plain typed text instead of dead-ending on the first or last row.
      const state = current + 1;
      return ((state + step + optionCount + 1) % (optionCount + 1)) - 1;
    });
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (optionCount === 0) return;
      moveActive(event.key === 'ArrowDown' ? 1 : -1);
      return;
    }
    if (event.key === 'Enter' && expanded && activeIndex >= 0) {
      // Only when something is highlighted: with nothing chosen, Enter submits the search.
      event.preventDefault();
      select(activeIndex);
      return;
    }
    if (event.key === 'Escape') {
      // First Escape closes the list; with the list already closed it is the field's own reset.
      if (expanded) {
        event.preventDefault();
        close();
      } else {
        onCancel?.();
      }
    }
  }

  /** The list only closes when the focus leaves the whole component, not when it moves inside it. */
  function handleBlur(event: FocusEvent<HTMLElement>) {
    if (!event.currentTarget.contains(event.relatedTarget)) {
      close();
    }
  }

  return {
    /** Identifiers of the input, the listbox and each option: unique per instance, and paired up. */
    inputId: `${baseId}-input`,
    listboxId: `${baseId}-listbox`,
    optionId: (index: number) => `${baseId}-option-${index}`,
    expanded,
    activeIndex,
    /** What the input's `aria-activedescendant` has to say — `undefined` while nothing is chosen. */
    activeDescendantId:
      expanded && activeIndex >= 0 ? `${baseId}-option-${activeIndex}` : undefined,
    open,
    close,
    select,
    setActiveIndex,
    handleKeyDown,
    handleBlur,
  };
}
