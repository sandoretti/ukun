import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http as mswHttp, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { server } from '../../mocks/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import CatalogPage from './CatalogPage';

const productNames = () =>
  screen.getAllByRole('article').map((article) => within(article).getByRole('heading').textContent);

describe('CatalogPage', () => {
  it('shows the loading state and then the first page ordered by price', async () => {
    renderWithProviders(<CatalogPage />, { route: '/products' });

    expect(screen.getByRole('status', { name: 'Cargando...' })).toBeDefined();

    expect(await screen.findByRole('heading', { name: 'Lámpara de mesa' })).toBeDefined();

    const names = productNames();
    expect(names).toHaveLength(12);
    expect(names[0]).toBe('Lámpara de mesa');
    // The two products tied at 399.99 come back in `id` order — the tie-break the API applies
    // (defect #23) is what makes the page boundary stable.
    expect(names.slice(8, 10)).toEqual(['Sofá de cuero', 'Litera infantil']);
  });

  it('paints availability per product, counting a missing stock row as unavailable', async () => {
    renderWithProviders(<CatalogPage />, { route: '/products' });

    // The badge arrives with the second, parallel read: the card is on screen before it.
    const sofa = await screen.findByRole('article', { name: 'Sofá de cuero' });
    expect(await within(sofa).findByText('Disponible en la tienda online')).toBeDefined();

    // Stocked at zero.
    const chair = screen.getByRole('article', { name: 'Silla nórdica' });
    expect(within(chair).getByText('Sin stock en la tienda online')).toBeDefined();

    // No stock row at all in the online store: unavailable, never "unknown" (RF-01).
    const shelf = screen.getByRole('article', { name: 'Estantería modular' });
    expect(within(shelf).getByText('Sin stock en la tienda online')).toBeDefined();
  });

  it('filters by name ignoring accents', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CatalogPage />, { route: '/products' });
    await screen.findByRole('article', { name: 'Sofá de cuero' });

    await user.type(screen.getByLabelText('Nombre'), 'sofa');
    await user.click(screen.getByRole('button', { name: 'Filtrar' }));

    // "sofa" without the accent matches the three "Sofá…", still ordered by price.
    expect(await screen.findByRole('heading', { name: 'Sofá cama' })).toBeDefined();
    expect(productNames()).toEqual(['Sofá de cuero', 'Sofá cama', 'Sofá chaise longue']);
  });

  it('seeds the form from the URL and shows the empty state when nothing matches', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CatalogPage />, { route: '/products?name=litera' });

    expect(await screen.findByRole('heading', { name: 'Litera infantil' })).toBeDefined();
    expect(screen.getByLabelText<HTMLInputElement>('Nombre').value).toBe('litera');

    await user.clear(screen.getByLabelText('Nombre'));
    await user.type(screen.getByLabelText('Nombre'), 'tresillo');
    await user.click(screen.getByRole('button', { name: 'Filtrar' }));

    expect(await screen.findByText('No hay productos que coincidan con la búsqueda')).toBeDefined();
    expect(screen.queryAllByRole('article')).toHaveLength(0);
  });

  it('moves between pages keeping the ordering', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CatalogPage />, { route: '/products' });
    await screen.findByRole('article', { name: 'Sofá de cuero' });

    expect(screen.getByText('Página 1 de 2 · 13 productos')).toBeDefined();
    expect(screen.getByRole('button', { name: 'Anterior' }).hasAttribute('disabled')).toBe(true);

    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByText('Página 2 de 2 · 13 productos')).toBeDefined();
    expect(productNames()).toEqual(['Sofá chaise longue']);
    expect(screen.getByRole('button', { name: 'Siguiente' }).hasAttribute('disabled')).toBe(true);
  });

  it('shows the error state with the translated code, never the raw detail', async () => {
    server.use(
      mswHttp.get('/api/products', () =>
        HttpResponse.json(
          {
            type: 'https://ukun.dev/errors/internal-error',
            title: 'INTERNAL_ERROR',
            status: 500,
            detail: 'Unexpected failure while listing products',
            code: 'INTERNAL_ERROR',
            traceId: 'abc',
          },
          { status: 500 },
        ),
      ),
    );

    renderWithProviders(<CatalogPage />, { route: '/products' });

    expect(await screen.findByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo')).toBeDefined();
    expect(screen.queryByText('Unexpected failure while listing products')).toBeNull();
  });

  it('keeps the catalog on screen when only availability fails', async () => {
    server.use(
      mswHttp.get('/api/availability', () =>
        HttpResponse.json(
          {
            type: 'https://ukun.dev/errors/internal-error',
            title: 'INTERNAL_ERROR',
            status: 500,
            code: 'INTERNAL_ERROR',
            traceId: 'abc',
          },
          { status: 500 },
        ),
      ),
    );

    renderWithProviders(<CatalogPage />, { route: '/products' });

    expect(await screen.findByRole('article', { name: 'Sofá de cuero' })).toBeDefined();
    expect(
      await screen.findByText('No hemos podido consultar la disponibilidad. El catálogo se muestra igualmente.'),
    ).toBeDefined();
    expect(screen.queryByText('Disponible en la tienda online')).toBeNull();
  });
});
