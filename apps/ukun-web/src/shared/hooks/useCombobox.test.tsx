import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { useCombobox } from './useCombobox';

const OPTIONS = ['Sofás', 'Mesas', 'Sillas'];
const FIELD_LABEL = 'Buscar';
const INSIDE_LABEL = 'dentro';
const OUTSIDE_LABEL = 'fuera';

/**
 * The smallest markup the pattern needs, so the hook is driven the way a browser drives it — real
 * key events, real focus — without dragging in `SearchBox`'s own presentation. What this asserts is
 * the hook's contract; how the field looks is `SearchBox`'s business.
 */
function ComboboxProbe() {
  const [value, setValue] = useState('');
  const combobox = useCombobox({
    optionCount: OPTIONS.length,
    onSelect: (index) => setValue(OPTIONS[index]),
    onCancel: () => setValue(''),
  });

  return (
    <div>
      <div onBlur={combobox.handleBlur}>
        <label htmlFor={combobox.inputId}>{FIELD_LABEL}</label>
        <input
          id={combobox.inputId}
          role="combobox"
          aria-expanded={combobox.expanded}
          aria-controls={combobox.listboxId}
          aria-activedescendant={combobox.activeDescendantId}
          value={value}
          onChange={(event) => {
            setValue(event.target.value);
            combobox.open();
          }}
          onKeyDown={combobox.handleKeyDown}
        />
        <button type="button">{INSIDE_LABEL}</button>
        {combobox.expanded && (
          <ul id={combobox.listboxId} role="listbox">
            {OPTIONS.map((option, index) => (
              // El doble reproduce el patrón de `SearchBox`: el teclado va en el campo y la opción
              // solo atiende al ratón. Misma exención y por la misma razón.
              // eslint-disable-next-line jsx-a11y/click-events-have-key-events
              <li
                key={option}
                id={combobox.optionId(index)}
                role="option"
                aria-selected={index === combobox.activeIndex}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => combobox.select(index)}
              >
                {option}
              </li>
            ))}
          </ul>
        )}
      </div>
      <button type="button">{OUTSIDE_LABEL}</button>
    </div>
  );
}

const field = () => screen.getByRole('combobox', { name: FIELD_LABEL });

/** Which row the field points at right now, by name — never by index into the DOM. */
function activeOption(): string | null {
  const id = field().getAttribute('aria-activedescendant');
  return id === null ? null : (document.getElementById(id)?.textContent ?? null);
}

async function openList(user: ReturnType<typeof userEvent.setup>) {
  await user.type(field(), 'so');
  return screen.getByRole('listbox');
}

describe('useCombobox', () => {
  it('opens the list as soon as there is something typed, with nothing highlighted yet', async () => {
    const user = userEvent.setup();
    render(<ComboboxProbe />);

    await openList(user);

    expect(field().getAttribute('aria-expanded')).toBe('true');
    expect(activeOption()).toBeNull();
  });

  it('walks down the rows and back out to the typed text', async () => {
    const user = userEvent.setup();
    render(<ComboboxProbe />);
    await openList(user);

    await user.keyboard('{ArrowDown}');
    expect(activeOption()).toBe('Sofás');

    await user.keyboard('{ArrowDown}{ArrowDown}');
    expect(activeOption()).toBe('Sillas');

    // Past the last row the cycle returns to the plain typed text, not to the first row: what was
    // typed is one more state of the list, and losing it means never getting it back with the arrows.
    await user.keyboard('{ArrowDown}');
    expect(activeOption()).toBeNull();
  });

  it('walks up from the typed text into the last row', async () => {
    const user = userEvent.setup();
    render(<ComboboxProbe />);
    await openList(user);

    await user.keyboard('{ArrowUp}');

    expect(activeOption()).toBe('Sillas');
  });

  it('keeps the focus in the field while the selection moves', async () => {
    const user = userEvent.setup();
    render(<ComboboxProbe />);
    await openList(user);

    await user.keyboard('{ArrowDown}');

    // The whole point of `aria-activedescendant`: the row is chosen without the focus leaving the
    // input, which is what lets someone keep typing.
    expect(document.activeElement).toBe(field());
    expect(screen.getByRole('option', { name: 'Sofás' }).getAttribute('aria-selected')).toBe('true');
  });

  it('picks the highlighted row with Enter and closes the list', async () => {
    const user = userEvent.setup();
    render(<ComboboxProbe />);
    await openList(user);

    await user.keyboard('{ArrowDown}{Enter}');

    expect(field()).toHaveProperty('value', 'Sofás');
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('leaves Enter alone when nothing is highlighted, so the form can submit', async () => {
    const user = userEvent.setup();
    render(<ComboboxProbe />);
    await openList(user);

    await user.keyboard('{Enter}');

    expect(field()).toHaveProperty('value', 'so');
  });

  it('closes the list with the first Escape and clears the field with the second', async () => {
    const user = userEvent.setup();
    render(<ComboboxProbe />);
    await openList(user);

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('listbox')).toBeNull();
    expect(field()).toHaveProperty('value', 'so');

    await user.keyboard('{Escape}');
    expect(field()).toHaveProperty('value', '');
  });

  it('stays open while the focus moves inside the component', async () => {
    const user = userEvent.setup();
    render(<ComboboxProbe />);
    await openList(user);

    await user.click(screen.getByRole('button', { name: INSIDE_LABEL }));

    expect(screen.getByRole('listbox')).toBeDefined();
  });

  it('closes when the focus leaves the component altogether', async () => {
    const user = userEvent.setup();
    render(<ComboboxProbe />);
    await openList(user);

    await user.click(screen.getByRole('button', { name: OUTSIDE_LABEL }));

    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('picks a row with the mouse', async () => {
    const user = userEvent.setup();
    render(<ComboboxProbe />);
    await openList(user);

    await user.click(screen.getByRole('option', { name: 'Mesas' }));

    expect(field()).toHaveProperty('value', 'Mesas');
    expect(screen.queryByRole('listbox')).toBeNull();
  });
});
