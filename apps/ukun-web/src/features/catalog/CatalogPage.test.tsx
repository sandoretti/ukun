import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http as mswHttp, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { scrollToSentinel } from '../../test/intersectionObserver';
import CatalogPage from './CatalogPage';

const productNames = () =>
  screen.getAllByRole('article').map((article) => within(article).getByRole('heading').textContent);

const loadMoreButton = () => screen.getByRole('button', { name: 'Ver más productos' });

describe('CatalogPage', () => {
  it('shows the loading state and then the first chunk ordered by price', async () => {
    renderWithProviders(<CatalogPage />, { route: '/products' });

    expect(screen.getByRole('status', { name: 'Cargando...' })).toBeDefined();

    expect(await screen.findByRole('heading', { name: 'Lámpara de mesa' })).toBeDefined();

    const names = productNames();
    // One chunk, not the whole page: the other 48 of this page arrive as the reader scrolls.
    expect(names).toHaveLength(24);
    expect(names[0]).toBe('Lámpara de mesa');
    expect(screen.getByText('Mostrando 24 de 105 productos')).toBeDefined();
  });

  it('paints availability per product, counting a missing stock row as unavailable', async () => {
    renderWithProviders(<CatalogPage />, { route: '/products' });

    // The status arrives with the second, parallel read: the card is on screen before it.
    const lamp = await screen.findByRole('article', { name: 'Lámpara de mesa' });
    expect(await within(lamp).findByText('En stock')).toBeDefined();

    // Stocked at zero.
    const chair = screen.getByRole('article', { name: 'Silla nórdica' });
    expect(within(chair).getByText('Sin stock online')).toBeDefined();

    // No stock row at all in the online store: unavailable, never "unknown" (RF-01).
    const chest = screen.getByRole('article', { name: 'Baúl 09' });
    expect(within(chest).getByText('Sin stock online')).toBeDefined();
  });

  it('links every card to its detail sheet, which is the only way in', async () => {
    renderWithProviders(<CatalogPage />, { route: '/products?name=sofa' });
    await screen.findByRole('article', { name: 'Sofá de cuero' });

    // Named by the title and not by the whole card, so tabbing through the grid is readable.
    expect(screen.getByRole('link', { name: 'Sofá de cuero' }).getAttribute('href')).toBe(
      '/products/1',
    );
  });

  it('filters by type the moment the option is picked, with no apply button', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CatalogPage />, { route: '/products?page=2' });
    await screen.findByRole('article', { name: 'Taburete 65' });

    await user.click(screen.getByRole('radio', { name: 'Sofás' }));

    // Back to the first page: staying on page 2 of a result set with one page shows an empty screen
    // for no reason.
    expect(await screen.findByRole('heading', { name: 'Sofá de cuero' })).toBeDefined();
    expect(productNames()).toEqual(['Sofá de cuero', 'Sofá cama', 'Sofá chaise longue']);
    expect(screen.getByText('3 productos')).toBeDefined();
    // Three results are one page: there is nothing to paginate and nothing left to load.
    expect(screen.queryByRole('navigation', { name: 'Paginación del catálogo' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Ver más productos' })).toBeNull();
  });

  it('filters by price when the slider is released, not while it is dragged', async () => {
    renderWithProviders(<CatalogPage />, { route: '/products' });
    await screen.findByRole('article', { name: 'Lámpara de mesa' });

    // The track ends at 750: the most expensive product is 749, rounded up to the next 50.
    const lowerThumb = await screen.findByRole('slider', { name: 'Precio mínimo' });
    expect(lowerThumb.getAttribute('max')).toBe('750');

    fireEvent.change(lowerThumb, { target: { value: '500' } });
    // Dragging moves the thumb and nothing else: a drag across the track is one request, not forty.
    expect(screen.getByText('105 productos')).toBeDefined();

    fireEvent.keyUp(lowerThumb);

    expect(await screen.findByText('3 productos')).toBeDefined();
    expect(productNames()).toEqual(['Cama de matrimonio', 'Sofá cama', 'Sofá chaise longue']);
    // A regular expression because `Intl` separates the amount from the € with a non-breaking space.
    expect(screen.getByRole('button', { name: /^Quitar el filtro Desde 500,00\s€$/ })).toBeDefined();
  });

  it('filters by price from the field too, which is where an exact amount is typed', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CatalogPage />, { route: '/products' });
    await screen.findByRole('article', { name: 'Lámpara de mesa' });

    const upperField = await screen.findByRole('spinbutton', { name: 'Hasta' });
    await user.clear(upperField);
    await user.type(upperField, '100{Enter}');

    expect(await screen.findByText('3 productos')).toBeDefined();
    expect(productNames()).toEqual(['Lámpara de mesa', 'Silla nórdica', 'Lámpara de pie']);
    // The slider follows the field: they are two views of the same two numbers.
    expect(screen.getByRole('slider', { name: 'Precio máximo' })).toHaveProperty('value', '100');
  });

  it('shows the name typed in the search bar as a removable chip', async () => {
    const user = userEvent.setup();
    // The field itself lives in the navigation bar; what reaches the listing is the URL.
    renderWithProviders(<CatalogPage />, { route: '/products?name=sofa' });

    // "sofa" without the accent matches the three "Sofá…", still ordered by price.
    expect(await screen.findByRole('heading', { name: 'Sofá cama' })).toBeDefined();
    expect(productNames()).toEqual(['Sofá de cuero', 'Sofá cama', 'Sofá chaise longue']);

    await user.click(screen.getByRole('button', { name: 'Quitar el filtro «sofa»' }));

    expect(await screen.findByText('105 productos')).toBeDefined();
    expect(screen.queryByRole('button', { name: 'Quitar el filtro «sofa»' })).toBeNull();
  });

  it('shows the empty state when nothing matches', async () => {
    renderWithProviders(<CatalogPage />, { route: '/products?name=tresillo' });

    expect(await screen.findByText('No hay productos que coincidan con la búsqueda')).toBeDefined();
    expect(screen.queryAllByRole('article')).toHaveLength(0);
  });

  it('adds a chunk per request until the page is full, and then stops offering more', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CatalogPage />, { route: '/products' });
    await screen.findByRole('article', { name: 'Lámpara de mesa' });

    await user.click(loadMoreButton());
    expect(await screen.findByText('Mostrando 48 de 105 productos')).toBeDefined();

    await user.click(loadMoreButton());
    expect(await screen.findByText('Mostrando 72 de 105 productos')).toBeDefined();

    // 72 is the page, not the catalogue: the rest is behind the pagination, so that neither the DOM
    // nor the cache grows without end.
    expect(productNames()).toHaveLength(72);
    expect(screen.queryByRole('button', { name: 'Ver más productos' })).toBeNull();
    expect(screen.getByText('Página 1 de 2')).toBeDefined();
    expect(screen.getByRole('button', { name: 'Anterior' }).hasAttribute('disabled')).toBe(true);
  });

  it('loads the next chunk on its own when the end of the grid comes into view', async () => {
    renderWithProviders(<CatalogPage />, { route: '/products' });
    await screen.findByRole('article', { name: 'Lámpara de mesa' });

    await scrollToSentinel();

    expect(await screen.findByText('Mostrando 48 de 105 productos')).toBeDefined();
    expect(productNames()).toHaveLength(48);
  });

  it('starts the next page from its own first chunk, with the same cap', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CatalogPage />, { route: '/products' });
    await screen.findByRole('article', { name: 'Lámpara de mesa' });

    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByText('Mostrando 24 de 105 productos')).toBeDefined();
    expect(screen.getByText('Página 2 de 2')).toBeDefined();
    // Page two picks up where page one ends, still ordered by price, and the two products tied at
    // 399.99 come back in `id` order — the tie-break the API applies (defect #23) is what makes the
    // boundary between chunks and pages stable.
    const names = productNames();
    expect(names[0]).toBe('Taburete 65');
    expect(names.slice(9, 11)).toEqual(['Sofá de cuero', 'Litera infantil']);

    await user.click(loadMoreButton());
    expect(await screen.findByText('Mostrando 33 de 105 productos')).toBeDefined();

    // The last page runs out of products before it runs out of room: 33 of them, and no chunk left.
    expect(screen.queryByRole('button', { name: 'Ver más productos' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Siguiente' }).hasAttribute('disabled')).toBe(true);
  });

  it('is entered directly on any page, which is what makes a result linkable', async () => {
    renderWithProviders(<CatalogPage />, { route: '/products?page=2' });

    expect(await screen.findByText('Mostrando 24 de 105 productos')).toBeDefined();
    expect(productNames()[0]).toBe('Taburete 65');
    expect(screen.getByText('Página 2 de 2')).toBeDefined();
    expect(screen.getByRole('button', { name: 'Anterior' }).hasAttribute('disabled')).toBe(false);
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

  it('keeps what is already loaded when a chunk fails, and lets it be retried', async () => {
    const user = userEvent.setup();
    server.use(
      // Only the second chunk fails: the first one is already on screen and must stay there.
      mswHttp.get('/api/products', ({ request }) => {
        if (new URL(request.url).searchParams.get('page') !== '1') {
          return undefined;
        }
        return HttpResponse.json(
          {
            type: 'https://ukun.dev/errors/internal-error',
            title: 'INTERNAL_ERROR',
            status: 500,
            code: 'INTERNAL_ERROR',
            traceId: 'abc',
          },
          { status: 500 },
        );
      }),
    );

    renderWithProviders(<CatalogPage />, { route: '/products' });
    await screen.findByRole('article', { name: 'Lámpara de mesa' });

    await user.click(loadMoreButton());

    expect(await screen.findByText('No hemos podido cargar más productos. Inténtalo de nuevo.')).toBeDefined();
    expect(productNames()).toHaveLength(24);
    // The button is still there: a failed chunk is something to try again, not a dead end.
    expect(loadMoreButton()).toBeDefined();

    server.resetHandlers();
    await user.click(loadMoreButton());

    expect(await screen.findByText('Mostrando 48 de 105 productos')).toBeDefined();
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

    expect(await screen.findByRole('article', { name: 'Lámpara de mesa' })).toBeDefined();
    expect(
      await screen.findByText('No hemos podido consultar la disponibilidad. El catálogo se muestra igualmente.'),
    ).toBeDefined();
    expect(screen.queryByText('En stock')).toBeNull();
  });
});
