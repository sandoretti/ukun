import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http as mswHttp, HttpResponse } from 'msw';
import { useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { server } from '../../test/msw/server';
import { stores } from '../../test/msw/storeHandlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import StoresPage from './StoresPage';

/** Where the screen left the address bar, as plain text, so the assertion reads like the URL. */
function LocationProbe() {
  const location = useLocation();
  return <span>{`${location.pathname}${location.search}`}</span>;
}

function renderStores(route = '/stores') {
  return renderWithProviders(
    <>
      <StoresPage />
      <LocationProbe />
    </>,
    { route },
  );
}

const storeNames = () =>
  screen.getAllByRole('article').map((article) => within(article).getByRole('heading').textContent);

const filterField = () => screen.getByRole('searchbox', { name: 'Filtrar por nombre o ciudad' });

describe('StoresPage', () => {
  it('shows the loading state and then every store, in the order the API sent them', async () => {
    renderStores();

    expect(screen.getByRole('status', { name: 'Cargando...' })).toBeDefined();

    expect(await screen.findByRole('article', { name: 'Ukun Avilés' })).toBeDefined();
    // Sorted by name by the API (`listStores`), and not re-sorted here.
    expect(storeNames()).toEqual(['Ukun Avilés', 'Ukun Gijón', 'Ukun León', 'Ukun Online', 'Ukun Oviedo']);
    expect(screen.getByText('5 tiendas')).toBeDefined();
  });

  it('carries the address and an actionable phone number for each store (RF-02)', async () => {
    renderStores();

    const gijon = await screen.findByRole('article', { name: 'Ukun Gijón' });
    expect(within(gijon).getByText('Avenida de la Costa 22')).toBeDefined();
    expect(within(gijon).getByText('33205 Gijón')).toBeDefined();
    expect(within(gijon).getByText('Asturias')).toBeDefined();

    // The dialler gets the raw E.164 value; the reader gets it grouped. The accessible name says
    // which store is being called, so a list of links is not five identical numbers.
    const call = within(gijon).getByRole('link', { name: 'Llamar a Ukun Gijón' });
    expect(call.getAttribute('href')).toBe('tel:+34900000002');
    expect(call.textContent).toBe('+34 900 000 002');
  });

  it('marks the online store without offering it as a shop to visit', async () => {
    renderStores();

    const online = await screen.findByRole('article', { name: 'Ukun Online' });
    expect(within(online).getByText('Tienda online')).toBeDefined();
    expect(
      within(online).getByText(
        'Es la tienda desde la que se preparan y se envían los pedidos. No es un local que se pueda visitar.',
      ),
    ).toBeDefined();

    // Exactly one store is the online one, and it is told apart by `isOnline`, never by its name.
    expect(screen.getAllByText('Tienda online')).toHaveLength(1);
    const oviedo = screen.getByRole('article', { name: 'Ukun Oviedo' });
    expect(within(oviedo).queryByText('Tienda online')).toBeNull();
  });

  it('filters by name ignoring accents, and writes the term to the URL', async () => {
    const user = userEvent.setup();
    renderStores();
    await screen.findByRole('article', { name: 'Ukun Avilés' });

    await user.type(filterField(), 'gijon');

    expect(storeNames()).toEqual(['Ukun Gijón']);
    expect(screen.getByText('1 tienda')).toBeDefined();
    // A linkable result: the term is in the address bar, not in a `useState`.
    expect(screen.getByText('/stores?q=gijon')).toBeDefined();
  });

  it('filters by city too, which is not always part of the name', async () => {
    const user = userEvent.setup();
    renderStores();
    await screen.findByRole('article', { name: 'Ukun Avilés' });

    // No store is called «Llanera»: this only matches through the address.
    await user.type(filterField(), 'llanera');

    expect(storeNames()).toEqual(['Ukun Online']);
  });

  it('filters on the cached list instead of asking the API again', async () => {
    const user = userEvent.setup();
    const queries: string[] = [];
    server.use(
      mswHttp.get('/api/stores', ({ request }) => {
        queries.push(new URL(request.url).search);
        return HttpResponse.json(stores);
      }),
    );

    renderStores();
    await screen.findByRole('article', { name: 'Ukun Avilés' });

    await user.type(filterField(), 'oviedo');
    expect(storeNames()).toEqual(['Ukun Oviedo']);

    // One read for the whole screen, with no `q`: closed reference data cached for the session, and
    // the contract's parameter deliberately unused (`useStores()` has no term to key on).
    expect(queries).toEqual(['']);
  });

  it('is entered already filtered, and clearing the field brings the rest back', async () => {
    const user = userEvent.setup();
    renderStores('/stores?q=leon');

    expect(await screen.findByRole('article', { name: 'Ukun León' })).toBeDefined();
    expect(storeNames()).toEqual(['Ukun León']);
    expect(filterField()).toHaveProperty('value', 'leon');

    await user.click(screen.getByRole('button', { name: 'Limpiar el filtro' }));

    expect(storeNames()).toHaveLength(5);
    // The parameter leaves the URL rather than staying behind as an empty one.
    expect(screen.getByText('/stores')).toBeDefined();
  });

  it('shows the empty state when the filter matches nothing', async () => {
    const user = userEvent.setup();
    renderStores();
    await screen.findByRole('article', { name: 'Ukun Avilés' });

    await user.type(filterField(), 'santander');

    expect(screen.getByText('No hay ninguna tienda que coincida con «santander»')).toBeDefined();
    expect(screen.queryAllByRole('article')).toHaveLength(0);
    expect(screen.getByText('0 tiendas')).toBeDefined();
  });

  it('shows the error state with the translated code, never the raw detail', async () => {
    server.use(
      mswHttp.get('/api/stores', () =>
        HttpResponse.json(
          {
            type: 'https://ukun.dev/errors/internal-error',
            title: 'INTERNAL_ERROR',
            status: 500,
            detail: 'Unexpected failure while listing stores',
            code: 'INTERNAL_ERROR',
            traceId: 'abc',
          },
          { status: 500 },
        ),
      ),
    );

    renderStores();

    expect(await screen.findByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo')).toBeDefined();
    expect(screen.queryByText('Unexpected failure while listing stores')).toBeNull();
  });
});
