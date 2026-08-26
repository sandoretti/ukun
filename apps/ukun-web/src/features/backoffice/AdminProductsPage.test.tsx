import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { adminProduct, adminProductsFail, adminProductState } from '../../test/msw/adminProductHandlers';
import { OPERATOR_DETAIL } from '../../test/msw/problem';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import AdminProductsPage from './AdminProductsPage';

const sofa = adminProduct({ id: 1, sku: 'SOFA-CUERO-01', name: 'Sofá de cuero', price: '399.99', typeId: 1 });

const table = adminProduct({
  id: 3,
  sku: 'MESA-COMEDOR-01',
  name: 'Mesa de comedor',
  price: '249.50',
  typeId: 3,
});

const lamp = adminProduct({
  id: 5,
  sku: 'LAMPARA-PIE-01',
  name: 'Lámpara de pie',
  price: '89.00',
  typeId: 5,
  isActive: false,
});

const catalog = [sofa, table, lamp];

const renderCatalog = (route = '/admin/products') =>
  renderWithProviders(<AdminProductsPage />, { route });

const rowOf = (name: string) => screen.getByText(name).closest('tr') as HTMLElement;

describe('AdminProductsPage', () => {
  it('lists the catalog with what an admin manages it by', async () => {
    server.use(...adminProductState({ products: catalog }).handlers);

    renderCatalog();

    await screen.findByText('Sofá de cuero');
    const row = rowOf('Sofá de cuero');
    expect(within(row).getByText('SOFA-CUERO-01')).toBeDefined();
    expect(within(row).getByText('Sofás')).toBeDefined();
    expect(within(row).getByText('399,99 €')).toBeDefined();
    // The listing is the way in to everything else: the row links to the product's own sheet.
    expect(within(row).getAllByRole('link')[0].getAttribute('href')).toBe('/admin/products/1');
    // And there is no second control repeating that: the row itself is the destination.
    expect(within(row).queryByRole('button')).toBeNull();
  });

  it('opens the product from anywhere in the row, not only from its name', async () => {
    const user = userEvent.setup();
    server.use(...adminProductState({ products: catalog }).handlers);

    renderWithProviders(
      <Routes>
        <Route path="/admin/products" element={<AdminProductsPage />} />
        <Route path="/admin/products/:id" element={<p data-testid="product-sheet" />} />
      </Routes>,
      { route: '/admin/products' },
    );

    await screen.findByText('Sofá de cuero');
    // The type cell: text with no link and no button in it, which is the point.
    await user.click(within(rowOf('Sofá de cuero')).getByText('Sofás'));

    expect(await screen.findByTestId('product-sheet')).toBeDefined();
  });

  it('hides the discontinued ones by default and finds them when asked, which is how one is reactivated', async () => {
    const user = userEvent.setup();
    const state = adminProductState({ products: catalog });
    server.use(...state.handlers);

    renderCatalog();

    await screen.findByText('Sofá de cuero');
    expect(screen.queryByText('Lámpara de pie')).toBeNull();
    expect(state.searches[0]).toContain('status=ACTIVE');

    await user.selectOptions(screen.getByLabelText('Estado'), 'DISCONTINUED');

    expect(await screen.findByText('Lámpara de pie')).toBeDefined();
    expect(within(rowOf('Lámpara de pie')).getByText('Descatalogado')).toBeDefined();
    expect(screen.queryByText('Sofá de cuero')).toBeNull();
  });

  it('sends the name filter to the server instead of narrowing the table in the browser', async () => {
    const user = userEvent.setup();
    const state = adminProductState({ products: catalog });
    server.use(...state.handlers);

    renderCatalog();
    await screen.findByText('Sofá de cuero');

    await user.type(screen.getByLabelText('Nombre del producto'), 'mesa');
    await user.tab();

    await waitFor(() => expect(screen.queryByText('Sofá de cuero')).toBeNull());
    expect(state.searches.at(-1)).toContain('q=mesa');
    // The count is the server's `totalElements`, which a browser-side filter could not produce.
    expect(screen.getByText('1 producto')).toBeDefined();
  });

  it('reads the filters from the URL: a filtered table is linkable', async () => {
    const state = adminProductState({ products: catalog });
    server.use(...state.handlers);

    renderCatalog('/admin/products?sku=LAMPARA-PIE-01&status=ALL');

    await screen.findByText('Lámpara de pie');
    expect(screen.queryByText('Sofá de cuero')).toBeNull();
    expect(state.searches[0]).toContain('sku=LAMPARA-PIE-01');
    expect(state.searches[0]).toContain('status=ALL');
  });

  it('asks the server for the page the URL is on, counted from zero on the wire', async () => {
    const many = Array.from({ length: 25 }, (_, index) => adminProduct({ id: index + 1 }));
    const state = adminProductState({ products: many });
    server.use(...state.handlers);

    renderCatalog('/admin/products?page=2');

    await screen.findByText('Página 2 de 2');
    expect(state.searches[0]).toContain('page=1');
  });

  it('tells "there is no catalog yet" apart from "the search found nothing"', async () => {
    server.use(...adminProductState({ products: [] }).handlers);

    const { unmount } = renderCatalog();

    expect(await screen.findByText('Todavía no hay ningún producto en el catálogo.')).toBeDefined();
    unmount();

    renderCatalog('/admin/products?q=zzz');

    expect(await screen.findByText('Ningún producto coincide con la búsqueda.')).toBeDefined();
  });

  it('paints a failed read by its code, never with the detail written for the operator', async () => {
    server.use(adminProductsFail('FORBIDDEN', 403));

    renderCatalog();

    expect(await screen.findByText('Tu cuenta no tiene acceso a esto')).toBeDefined();
    expect(screen.queryByText(OPERATOR_DETAIL)).toBeNull();
  });

  it('waits with a spinner rather than with an empty table', () => {
    server.use(...adminProductState({ products: catalog }).handlers);

    renderCatalog();

    expect(screen.getByRole('status')).toBeDefined();
  });
});
