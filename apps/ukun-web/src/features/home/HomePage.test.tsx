import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http as mswHttp, HttpResponse } from 'msw';
import { useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import HomePage from './HomePage';

/** Where a call to action landed, as plain text, so the assertion reads like the address bar. */
function LocationProbe() {
  const location = useLocation();
  return <span>{`${location.pathname}${location.search}`}</span>;
}

function renderHome() {
  return renderWithProviders(
    <>
      <HomePage />
      <LocationProbe />
    </>,
    { route: '/' },
  );
}

describe('HomePage', () => {
  it('leads with a single h1 and keeps the rest of the page under it', async () => {
    renderHome();

    const headingOne = screen.getAllByRole('heading', { level: 1 });
    expect(headingOne).toHaveLength(1);
    expect(headingOne[0].textContent).toBe('Diseño que cabe en tu casa y en tu presupuesto');

    // Everything else is a section heading: four `<h1>` on one page was the legacy home's defect.
    const headingTwo = await screen.findAllByRole('heading', { level: 2 });
    expect(headingTwo.map((heading) => heading.textContent)).toEqual([
      'Empieza por una categoría',
      'Descubre el equilibrio perfecto',
      'Precios asequibles sin compromisos',
      'Crea ambientes que reflejen tu estilo',
      'Explora nuestra colección',
    ]);
  });

  it('sends both calls to action to the catalogue', async () => {
    renderHome();

    expect(screen.getByRole('link', { name: 'Ver el catálogo' }).getAttribute('href')).toBe('/products');
    expect(screen.getByRole('link', { name: 'Ir al catálogo' }).getAttribute('href')).toBe('/products');
  });

  it('lists the product types as links into the listing already filtered', async () => {
    renderHome();

    expect(screen.getByRole('status', { name: 'Cargando...' })).toBeDefined();

    const sofas = await screen.findByRole('link', { name: 'Sofás' });
    expect(sofas.getAttribute('href')).toBe('/products?typeId=1');
    expect(screen.getByRole('link', { name: 'Iluminación' }).getAttribute('href')).toBe('/products?typeId=5');
  });

  it('says so when the categories cannot be read, without showing the raw detail', async () => {
    server.use(
      mswHttp.get('/api/product-types', () =>
        HttpResponse.json(
          {
            type: 'https://ukun.dev/errors/internal-error',
            title: 'INTERNAL_ERROR',
            status: 500,
            detail: 'Unexpected failure while listing product types',
            code: 'INTERNAL_ERROR',
            traceId: 'abc',
          },
          { status: 500 },
        ),
      ),
    );

    renderHome();

    expect(await screen.findByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo')).toBeDefined();
    expect(screen.queryByText('Unexpected failure while listing product types')).toBeNull();
    // The rest of the page is not read data and stays up: one section failing is not the screen failing.
    expect(screen.getByRole('heading', { level: 1 })).toBeDefined();
  });

  it('shows the empty state when the catalogue has no types yet', async () => {
    server.use(mswHttp.get('/api/product-types', () => HttpResponse.json([])));

    renderHome();

    expect(await screen.findByText('Todavía no hay categorías publicadas')).toBeDefined();
  });

  it('searches from the landing page and lands on the filtered listing', async () => {
    const user = userEvent.setup();
    renderHome();

    const field = screen.getByRole('combobox', { name: 'Buscar productos' });
    await user.type(field, 'litera');

    // Same combobox as the navigation bar's, so the keyboard still drives it.
    const options = await screen.findAllByRole('option');
    expect(options.map((option) => option.textContent?.includes('Litera infantil'))).toContain(true);

    await user.keyboard('{ArrowDown}{Enter}');

    expect(screen.getByText('/products?name=Litera+infantil')).toBeDefined();
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('closes the suggestions with Escape without leaving the page', async () => {
    const user = userEvent.setup();
    renderHome();

    await user.type(screen.getByRole('combobox', { name: 'Buscar productos' }), 'mesa');
    await screen.findAllByRole('option');

    await user.keyboard('{Escape}');

    expect(screen.queryByRole('listbox')).toBeNull();
    expect(screen.getByText('/')).toBeDefined();
  });

  it('tells the story with drawings that carry no meaning of their own', async () => {
    renderHome();
    await screen.findByRole('link', { name: 'Sofás' });

    // Decorative illustrations: what they say is written next to them, so they are out of the
    // accessibility tree and nothing here is announced twice.
    expect(screen.queryAllByRole('img')).toHaveLength(0);

    const design = screen.getByRole('heading', { name: 'Descubre el equilibrio perfecto' })
      .closest('section');
    expect(design).not.toBeNull();
    expect(within(design!).getByText(/una selección corta y cuidada/)).toBeDefined();
  });
});
