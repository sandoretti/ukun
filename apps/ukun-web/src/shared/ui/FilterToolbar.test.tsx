import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { renderWithProviders } from '../../test/renderWithProviders';
import { FilterToolbar } from './FilterToolbar';
import { SearchField } from './SearchField';

/**
 * A stand-in for whatever control a screen puts in the bar. It travels as a variable and not as text
 * in the markup because the i18n rule that guards the app —no literal in JSX— guards the tests too,
 * and this suite is not the place to make an exception to it.
 */
const CHILD = 'un filtro';

describe('FilterToolbar', () => {
  it('names the region instead of writing a heading over the controls', () => {
    renderWithProviders(
      <FilterToolbar label="Filtros del stock" activeCount={0} onClear={() => {}}>
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
      <FilterToolbar label="Filtros" hint="Se busca por SKU o por nombre." activeCount={0} onClear={() => {}}>
        <button type="button">{CHILD}</button>
      </FilterToolbar>,
    );

    expect(screen.getByText('Se busca por SKU o por nombre.')).toBeDefined();
  });

  it('shows the clear action only when there is something to clear, and counts it', async () => {
    const user = userEvent.setup();
    const onClear = vi.fn();

    const { rerender } = renderWithProviders(
      <FilterToolbar label="Filtros" activeCount={0} onClear={onClear}>
        <button type="button">{CHILD}</button>
      </FilterToolbar>,
    );

    expect(screen.queryByRole('button', { name: /Quitar/ })).toBeNull();

    rerender(
      <FilterToolbar label="Filtros" activeCount={2} onClear={onClear}>
        <button type="button">{CHILD}</button>
      </FilterToolbar>,
    );

    await user.click(screen.getByRole('button', { name: 'Quitar los 2 filtros' }));
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
