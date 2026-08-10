import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { http as mswHttp, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import ProductDetailPage from './ProductDetailPage';

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
    expect(screen.getByText('Sofás')).toBeDefined();
    // A regular expression because `Intl` separates the amount from the € with a non-breaking space.
    expect(screen.getByText(/^399,99\s€$/)).toBeDefined();
    // The SKU belongs to the sheet, unlike the card of the listing.
    expect(screen.getByText('SKU')).toBeDefined();
    expect(screen.getByText('SOFA-CUERO-01')).toBeDefined();
    expect(screen.getByText(/Estructura de madera maciza/)).toBeDefined();

    // The URL is the one the server composed (RF-10), never rebuilt from the identifier.
    const photo = screen.getByRole('img', { name: 'Sofá de cuero' });
    expect(photo.getAttribute('src')).toBe('/media/products/1.jpg');

    // Availability is the second, parallel read: the sheet is on screen before it arrives.
    expect(await screen.findByText('En stock')).toBeDefined();
  });

  it('offers the way back to the catalogue from the breadcrumb', async () => {
    renderDetail('/products/1');
    await screen.findByRole('heading', { level: 1, name: 'Sofá de cuero' });

    expect(screen.getByRole('link', { name: 'Catálogo' }).getAttribute('href')).toBe('/products');
  });

  it('counts a product with no stock row as unavailable, and says when there is no photo or text', async () => {
    // Product 11 has no stock row at all in the online store, no image and no description.
    renderDetail('/products/11');

    expect(await screen.findByRole('heading', { level: 1, name: 'Estantería modular' })).toBeDefined();
    expect(screen.queryByRole('img', { name: 'Estantería modular' })).toBeNull();
    expect(screen.getByText('Sin foto')).toBeDefined();
    expect(screen.getByText('Este producto todavía no tiene descripción.')).toBeDefined();

    // Never "unknown": a missing row is a product that cannot be bought online (RF-01).
    expect(await screen.findByText('Sin stock online')).toBeDefined();
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
    expect(screen.queryByText('En stock')).toBeNull();
    expect(screen.getByText('SOFA-CUERO-01')).toBeDefined();
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
