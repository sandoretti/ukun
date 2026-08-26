import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { renderWithProviders } from '../../test/renderWithProviders';
import type { FilterChip } from './ActiveFilterChips';
import { FilterToolbar } from './FilterToolbar';
import { SearchField } from './SearchField';

/**
 * A stand-in for whatever control a screen puts in the bar. It travels as a variable and not as text
 * in the markup because the i18n rule that guards the app —no literal in JSX— guards the tests too,
 * and this suite is not the place to make an exception to it.
 */
const CHILD = 'un filtro';

const NO_CHIPS: FilterChip[] = [];

describe('FilterToolbar', () => {
  it('names the region instead of writing a heading over the controls', () => {
    renderWithProviders(
      <FilterToolbar label="Filtros del stock" chips={NO_CHIPS} onRemove={() => {}} onClear={() => {}}>
        <button type="button">{CHILD}</button>
      </FilterToolbar>,
    );

    const bar = screen.getByRole('search', { name: 'Filtros del stock' });

    expect(within(bar).getByRole('button', { name: CHILD })).toBeDefined();
    // The word "Filter" over a row of controls that already look like filters is space, not help.
    expect(screen.queryByText('Filtrar')).toBeNull();
  });

  it('says what can be searched under the bar, where it misaligns nothing', () => {
    renderWithProviders(
      <FilterToolbar
        label="Filtros"
        hint="Se busca por SKU o por nombre."
        chips={NO_CHIPS}
        onRemove={() => {}}
        onClear={() => {}}
      >
        <button type="button">{CHILD}</button>
      </FilterToolbar>,
    );

    expect(screen.getByText('Se busca por SKU o por nombre.')).toBeDefined();
  });

  it('shows nothing at all while nothing is filtered', () => {
    renderWithProviders(
      <FilterToolbar label="Filtros" chips={NO_CHIPS} onRemove={() => {}} onClear={() => {}}>
        <button type="button">{CHILD}</button>
      </FilterToolbar>,
    );

    // A row saying "no filters" is a row that only takes up space.
    expect(screen.queryByRole('group', { name: 'Filtros activos' })).toBeNull();
    expect(screen.queryByRole('button', { name: /Limpiar/ })).toBeNull();
  });

  it('removes one filter by its own chip, saying which one in the name', async () => {
    const user = userEvent.setup();
    const onRemove = vi.fn();

    renderWithProviders(
      <FilterToolbar
        label="Filtros"
        chips={[{ key: 'q', label: 'Nombre: cama' }]}
        onRemove={onRemove}
        onClear={() => {}}
      >
        <button type="button">{CHILD}</button>
      </FilterToolbar>,
    );

    await user.click(screen.getByRole('button', { name: 'Quitar el filtro Nombre: cama' }));

    expect(onRemove).toHaveBeenCalledExactlyOnceWith('q');
  });

  it('offers to clear the lot only once there is more than one thing to clear', async () => {
    const user = userEvent.setup();
    const onClear = vi.fn();
    const one = [{ key: 'q', label: 'Nombre: cama' }];
    const two = [...one, { key: 'sku', label: 'SKU: ABC-123' }];

    const { rerender } = renderWithProviders(
      <FilterToolbar label="Filtros" chips={one} onRemove={() => {}} onClear={onClear}>
        <button type="button">{CHILD}</button>
      </FilterToolbar>,
    );

    // With a single chip, "clear all" would be a second button doing what the first one does.
    expect(screen.queryByRole('button', { name: 'Limpiar todo' })).toBeNull();

    rerender(
      <FilterToolbar label="Filtros" chips={two} onRemove={() => {}} onClear={onClear}>
        <button type="button">{CHILD}</button>
      </FilterToolbar>,
    );

    await user.click(screen.getByRole('button', { name: 'Limpiar todo' }));
    expect(onClear).toHaveBeenCalledOnce();
  });
});

describe('SearchField', () => {
  it('commits when the reader leaves the field, not on every keystroke', async () => {
    const user = userEvent.setup();
    const onCommit = vi.fn();

    renderWithProviders(<SearchField label="Buscar" value="" onCommit={onCommit} />);

    await user.type(screen.getByLabelText('Buscar'), 'sofa');
    expect(onCommit).not.toHaveBeenCalled();

    await user.tab();
    expect(onCommit).toHaveBeenCalledExactlyOnceWith('sofa');
  });

  it('commits on Enter without submitting anything', async () => {
    const user = userEvent.setup();
    const onCommit = vi.fn();

    renderWithProviders(<SearchField label="Buscar" value="" onCommit={onCommit} />);

    await user.type(screen.getByLabelText('Buscar'), 'sofa{Enter}');

    expect(onCommit).toHaveBeenCalledExactlyOnceWith('sofa');
  });

  it('says nothing when the value did not change: leaving a field is not filtering', async () => {
    const user = userEvent.setup();
    const onCommit = vi.fn();

    renderWithProviders(<SearchField label="Buscar" value="sofa" onCommit={onCommit} />);

    await user.click(screen.getByLabelText('Buscar'));
    await user.tab();

    expect(onCommit).not.toHaveBeenCalled();
  });

  it('keeps its label reachable even though it is not drawn', () => {
    renderWithProviders(<SearchField label="Buscar empleados" value="" onCommit={() => {}} />);

    // Visually hidden is not absent: the control still has a name to be announced by.
    expect(screen.getByLabelText('Buscar empleados')).toBeDefined();
  });
});
