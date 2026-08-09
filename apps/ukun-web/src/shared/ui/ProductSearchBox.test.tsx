import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '../../test/renderWithProviders';
import { ProductSearchBox } from './ProductSearchBox';

/** Where the box navigated to, as plain text, so the assertion reads like the URL bar. */
function LocationProbe() {
  const location = useLocation();
  return <span>{`${location.pathname}${location.search}`}</span>;
}

function renderSearchBox(route = '/') {
  return renderWithProviders(
    <>
      <ProductSearchBox />
      <LocationProbe />
    </>,
    { route },
  );
}

const searchField = () => screen.getByRole('combobox', { name: 'Buscar productos' });

describe('ProductSearchBox', () => {
  it('suggests products once there are enough letters, and not before', async () => {
    const user = userEvent.setup();
    renderSearchBox();

    await user.type(searchField(), 's');
    expect(screen.queryByRole('listbox')).toBeNull();

    await user.type(searchField(), 'of');

    // `Intl` separates the amount from the € with a non-breaking space; the assertion reads it as
    // the plain space anyone would type.
    const readable = (text: string | null) => text?.replaceAll(' ', ' ');

    const options = await screen.findAllByRole('option');
    expect(options.map((option) => readable(option.textContent))).toEqual([
      'Sofá cama599,00 €',
      'Sofá chaise longue749,00 €',
      'Sofá de cuero399,99 €',
    ]);
  });

  it('goes to the listing filtered by the chosen suggestion, with the keyboard', async () => {
    const user = userEvent.setup();
    renderSearchBox();

    await user.type(searchField(), 'litera');
    await screen.findAllByRole('option');

    await user.keyboard('{ArrowDown}{Enter}');

    expect(screen.getByText('/products?name=Litera+infantil')).toBeDefined();
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('submits the typed text when no suggestion is highlighted', async () => {
    const user = userEvent.setup();
    renderSearchBox();

    await user.type(searchField(), 'sofa');
    await screen.findAllByRole('option');

    await user.keyboard('{Enter}');

    expect(screen.getByText('/products?name=sofa')).toBeDefined();
  });

  it('closes the list with Escape without navigating anywhere', async () => {
    const user = userEvent.setup();
    renderSearchBox('/products');

    await user.type(searchField(), 'mesa');
    await screen.findAllByRole('option');

    await user.keyboard('{Escape}');

    expect(screen.queryByRole('listbox')).toBeNull();
    expect(screen.getByText('/products')).toBeDefined();
  });

  it('is seeded from the filter already in the URL', async () => {
    renderSearchBox('/products?name=litera');

    expect(searchField()).toHaveProperty('value', 'litera');
  });
});
