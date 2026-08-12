import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { http as mswHttp, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { navigateTo } from '../../shared/browser/navigateTo';
import { server } from '../../test/msw/server';
import { profileWithFavoriteStore } from '../../test/msw/profileHandlers';
import { signedIn } from '../../test/msw/sessionHandlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import ProductDetailPage from './ProductDetailPage';

// The sheet is public, and what it must never do is leave the site. Mocked so a stray `401` on a
// public screen fails the test instead of silently reloading jsdom.
vi.mock('../../shared/browser/navigateTo', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/browser/navigateTo')>()),
  navigateTo: vi.fn(),
}));

/**
 * The screen reads its identifier from the path, so it is mounted under its own route instead of
 * being rendered bare: without the match, `useParams` has nothing to hand over.
 */
function renderDetail(route: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/products/:id" element={<ProductDetailPage />} />
    </Routes>,
    { route },
  );
}

describe('ProductDetailPage', () => {
  it('shows the loading state and then everything RF-01 asks for', async () => {
    renderDetail('/products/1');

    expect(screen.getByRole('status', { name: 'Cargando...' })).toBeDefined();

    expect(await screen.findByRole('heading', { level: 1, name: 'Sofá de cuero' })).toBeDefined();
    // A regular expression because `Intl` separates the amount from the € with a non-breaking space.
    expect(screen.getByText(/^399,99\s€$/)).toBeDefined();
    // The SKU stays on the sheet (RF-01), as a reference and not as the headline.
    expect(screen.getByText('Ref. SOFA-CUERO-01')).toBeDefined();
    expect(screen.getByText(/Estructura de madera maciza/)).toBeDefined();

    // The URL is the one the server composed (RF-10), never rebuilt from the identifier.
    const photo = screen.getByRole('img', { name: 'Sofá de cuero' });
    expect(photo.getAttribute('src')).toBe('/media/products/1.jpg');

    // Availability is the second, parallel read: the sheet is on screen before it arrives, and it
    // names the store it is talking about.
    expect(await screen.findByText('Disponible en Ukun Online')).toBeDefined();
  });

  it('offers the way back to the catalogue, and sideways to the type', async () => {
    renderDetail('/products/1');
    await screen.findByRole('heading', { level: 1, name: 'Sofá de cuero' });

    expect(screen.getByRole('link', { name: 'Catálogo' }).getAttribute('href')).toBe('/products');
    // The type is the way out to the rest of its kind, on the listing's own URL.
    expect(screen.getByRole('link', { name: 'Ver todo en Sofás' }).getAttribute('href')).toBe(
      '/products?typeId=1',
    );
  });

  it('counts a product with no stock row as unavailable, and says when there is no photo or text', async () => {
    // Product 11 has no stock row at all in the online store, no image and no description.
    renderDetail('/products/11');

    expect(await screen.findByRole('heading', { level: 1, name: 'Estantería modular' })).toBeDefined();
    expect(screen.queryByRole('img', { name: 'Estantería modular' })).toBeNull();
    expect(screen.getByText('Sin foto')).toBeDefined();
    expect(screen.getByText('Este producto todavía no tiene descripción.')).toBeDefined();
    // Nothing to enlarge, so nothing is offered.
    expect(screen.queryByRole('button', { name: /Ampliar la imagen/ })).toBeNull();

    // Never "unknown": a missing row is a product that cannot be bought online (RF-01).
    expect(await screen.findByText('Sin stock en Ukun Online')).toBeDefined();
  });

  it('opens the photo in a viewer that can be zoomed and left with the keyboard', async () => {
    const user = userEvent.setup();
    renderDetail('/products/1');
    await screen.findByRole('heading', { level: 1, name: 'Sofá de cuero' });

    await user.click(screen.getByRole('button', { name: 'Ampliar la imagen de Sofá de cuero' }));

    const viewer = await screen.findByRole('dialog', { name: 'Sofá de cuero' });
    expect(within(viewer).getByRole('img', { name: 'Sofá de cuero' })).toBeDefined();
    // It opens fitted, so there is nothing to reduce yet.
    expect(within(viewer).getByText('×1')).toBeDefined();
    expect(within(viewer).getByRole('button', { name: 'Reducir' }).hasAttribute('disabled')).toBe(true);

    await user.click(within(viewer).getByRole('button', { name: 'Ampliar' }));

    expect(await within(viewer).findByText('×1,5')).toBeDefined();
    expect(within(viewer).getByRole('button', { name: 'Reducir' }).hasAttribute('disabled')).toBe(false);

    // The viewer is a dialog, so Escape is the way out — nothing hand-rolled about it. It leaves
    // through the modal's own fade, hence waiting for it to go instead of asserting on the spot.
    await user.keyboard('{Escape}');

    // `waitFor` and not `waitForElementToBeRemoved`: that one demands the element still be there
    // when the wait starts, and under a loaded suite the 300ms fade can finish inside the awaited
    // keystroke. The assertion wanted is "it is gone", which is true either way.
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  });

  it('shows other products of the same type, without the one being looked at', async () => {
    renderDetail('/products/1');
    await screen.findByRole('heading', { level: 1, name: 'Sofá de cuero' });

    const related = await screen.findByRole('region', { name: 'También te puede interesar' });
    const names = within(related)
      .getAllByRole('article')
      .map((card) => within(card).getByRole('heading').textContent);

    expect(names).toEqual(['Sofá cama', 'Sofá chaise longue']);
  });

  it('drops the related strip when its read fails, and keeps the sheet whole', async () => {
    server.use(
      // Only the listing fails; the sheet's own read is a different endpoint.
      mswHttp.get('/api/products', () =>
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

    renderDetail('/products/1');

    expect(await screen.findByRole('heading', { level: 1, name: 'Sofá de cuero' })).toBeDefined();
    expect(await screen.findByText('Disponible en Ukun Online')).toBeDefined();
    // Secondary content that could not be read is content that is not there — not an error message.
    expect(screen.queryByText('También te puede interesar')).toBeNull();
    expect(screen.queryByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo')).toBeNull();
  });

  it('turns the 404 of a discontinued product into its own screen, with a way out', async () => {
    server.use(
      mswHttp.get('/api/products/:id', () =>
        HttpResponse.json(
          {
            type: 'https://ukun.dev/errors/resource-not-found',
            title: 'RESOURCE_NOT_FOUND',
            status: 404,
            detail: 'Product 999 is not active',
            code: 'RESOURCE_NOT_FOUND',
            traceId: 'abc',
          },
          { status: 404 },
        ),
      ),
    );

    renderDetail('/products/999');

    expect(await screen.findByText('No encontramos lo que buscas')).toBeDefined();
    // Not the generic failure, and not the operator's English detail.
    expect(screen.queryByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo')).toBeNull();
    expect(screen.queryByText('Product 999 is not active')).toBeNull();
    // A dead end is not a comprehensible screen.
    expect(screen.getByRole('link', { name: 'Volver al catálogo' }).getAttribute('href')).toBe(
      '/products',
    );
  });

  it('answers an identifier that is not one without asking the API', () => {
    renderDetail('/products/abc');

    // No `find*`: nothing was requested, so the screen is there on the first render.
    expect(screen.getByText('No encontramos lo que buscas')).toBeDefined();
    expect(screen.getByRole('link', { name: 'Volver al catálogo' })).toBeDefined();
  });

  it('keeps the sheet on screen when only availability fails', async () => {
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

    renderDetail('/products/1');

    expect(await screen.findByRole('heading', { level: 1, name: 'Sofá de cuero' })).toBeDefined();
    expect(
      await screen.findByText('No hemos podido consultar la disponibilidad de este producto.'),
    ).toBeDefined();
    // A discreet note, not the error state taking over a sheet that has plenty to show.
    expect(screen.queryByText('Disponible en Ukun Online')).toBeNull();
    expect(screen.getByText('Ref. SOFA-CUERO-01')).toBeDefined();
  });

  it('paints the error state when the product itself cannot be read', async () => {
    server.use(
      mswHttp.get('/api/products/:id', () =>
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

    renderDetail('/products/1');

    // A 5xx *is* worth one more attempt, so the message arrives one second later, after the retry's
    // backoff — unlike the 404 above, which is answered on the first reply.
    expect(
      await screen.findByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo', undefined, {
        timeout: 3000,
      }),
    ).toBeDefined();
    expect(screen.queryByRole('heading', { level: 1 })).toBeNull();
  });
});

/**
 * The second half of RF-01: "show availability in the online store and, if the customer is
 * authenticated and has a favourite store, in that one too".
 *
 * The fixtures carry the three shapes without inventing data — `customerProfile.favoriteStoreId` is
 * `2` (Ukun Gijón), and `stock[2]` holds products 1, 3 and 9 only, so product 2 is the case of a
 * favourite store with no row at all for the product being looked at.
 */
describe('ProductDetailPage · favourite store (RF-01)', () => {
  beforeEach(() => {
    vi.mocked(navigateTo).mockClear();
  });

  /**
   * The sheet's own stock block, which is the announced one — the cards of the related strip below
   * report the same two stores and land on the same sentence for a store with no stock, so a bare
   * `findByText` would match more than one element and say nothing about where.
   */
  function sheetStock() {
    const block = document.querySelector('[aria-live="polite"]');

    if (block === null) {
      throw new Error('the sheet is not on screen: it has no announced stock block');
    }
    return within(block as HTMLElement);
  }

  it('says nothing about a second store for a visitor, and never asks for a profile', async () => {
    const asked: string[] = [];
    server.events.on('request:start', ({ request }) => asked.push(new URL(request.url).pathname));

    renderDetail('/products/1');

    expect(await screen.findByText('Disponible en Ukun Online')).toBeDefined();
    expect(screen.queryByText(/Ukun Gijón/)).toBeNull();

    // The point of the whole guard. `/api/me/profile` answers 401 to a visitor, and a 401 without
    // `allowAnonymous` is a dead session to the interceptor: asking for it here would throw every
    // anonymous reader out of a public sheet. The default handler would answer 200 in this test, so
    // the assertion is on the request never leaving, not on what came back.
    expect(asked).not.toContain('/api/me/profile');
    expect(vi.mocked(navigateTo)).not.toHaveBeenCalled();

    server.events.removeAllListeners();
  });

  it('says nothing about a second store for a customer who never chose one', async () => {
    server.use(signedIn(), profileWithFavoriteStore(null));

    renderDetail('/products/1');

    expect(await screen.findByText('Disponible en Ukun Online')).toBeDefined();
    // Waited out rather than asserted on the spot: the profile lands after the sheet, and a line
    // that appears late would slip past a synchronous check.
    await waitFor(() => expect(screen.queryByText(/Ukun Gijón/)).toBeNull());
  });

  it('shows the favourite store next to the online one, never instead of it', async () => {
    server.use(signedIn());

    renderDetail('/products/1');

    expect(await screen.findByText('Disponible en Ukun Online')).toBeDefined();
    // Product 1 has stock in both: one unit in Gijón, four online.
    expect(await screen.findByText('Disponible en Ukun Gijón')).toBeDefined();
  });

  it('counts a favourite store with no row for the product as out of stock', async () => {
    server.use(signedIn());

    // Product 2 is stocked online and has no line at all in Gijón — the absent row, which RF-01
    // reads as "no", never as "unknown".
    renderDetail('/products/2');
    // The block only exists once the product has arrived; scoping to it before that finds nothing.
    await screen.findByRole('heading', { level: 1 });

    expect(await sheetStock().findByText('Disponible en Ukun Online')).toBeDefined();
    expect(await sheetStock().findByText('Sin stock en Ukun Gijón')).toBeDefined();
  });

  it('drops the favourite line in silence when its profile read fails', async () => {
    server.use(
      signedIn(),
      mswHttp.get('/api/me/profile', () =>
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

    renderDetail('/products/1');

    // The store that can actually be bought from is answered, so there is nothing to apologise for.
    expect(await screen.findByText('Disponible en Ukun Online')).toBeDefined();
    await waitFor(() => expect(screen.queryByText(/Ukun Gijón/)).toBeNull());
    expect(
      screen.queryByText('No hemos podido consultar la disponibilidad de este producto.'),
    ).toBeNull();
  });
});
