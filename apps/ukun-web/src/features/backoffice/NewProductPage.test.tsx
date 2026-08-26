import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useParams } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import {
  adminProduct,
  adminProductState,
  createProductFails,
} from '../../test/msw/adminProductHandlers';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import NewProductPage from './NewProductPage';

/**
 * Stands in for the sheet, so that landing on it can be asserted without mounting the real one.
 *
 * A `data-testid` and not a role: this is not a screen, it is a marker saying which route the router
 * ended up on, and there is no accessible name to look it up by that would not be made up here.
 */
function SheetProbe() {
  const { id } = useParams();

  return <p data-testid="product-sheet">{id}</p>;
}

const renderNew = () =>
  renderWithProviders(
    <Routes>
      <Route path="/admin/products/new" element={<NewProductPage />} />
      <Route path="/admin/products/:id" element={<SheetProbe />} />
    </Routes>,
    { route: '/admin/products/new' },
  );

type Draft = { sku?: string; name?: string; description?: string; price?: string; type?: string };

/** Fills the form with something valid, letting one test override just the field it is about. */
async function fillForm(user: ReturnType<typeof userEvent.setup>, draft: Draft = {}) {
  const { sku = 'BUTACA-01', name = 'Butaca de lectura', description = 'Con reposapiés.', price = '199.00', type = 'Sofás' } =
    draft;

  await user.type(await screen.findByLabelText('SKU'), sku);
  await user.type(screen.getByLabelText('Nombre'), name);
  await user.type(screen.getByLabelText('Descripción'), description);
  await user.type(screen.getByLabelText('Precio'), price);
  await user.selectOptions(screen.getByLabelText('Tipo'), type);
}

const submit = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('button', { name: 'Crear producto' }));

describe('NewProductPage', () => {
  it('creates the product and lands on its sheet, which is where the photo is uploaded', async () => {
    const user = userEvent.setup();
    const state = adminProductState();
    server.use(...state.handlers);

    renderNew();
    await fillForm(user);
    await submit(user);

    expect(await screen.findByTestId('product-sheet')).toBeDefined();
    expect(state.created).toBe(1);
    // Exactly the five fields of `CreateProductRequest`: no image and no `isActive` — the product is
    // born active and without a photo, and neither travels.
    expect(state.bodies[0]).toEqual({
      sku: 'BUTACA-01',
      name: 'Butaca de lectura',
      description: 'Con reposapiés.',
      price: '199.00',
      typeId: 1,
    });
  });

  it('sends the price as the exact decimal string it is, comma and all', async () => {
    const user = userEvent.setup();
    const state = adminProductState();
    server.use(...state.handlers);

    renderNew();
    await fillForm(user, { price: '399,90' });
    await submit(user);

    await waitFor(() => expect(state.created).toBe(1));
    // Never a float on the way (defect #5), and the trailing zero survives.
    expect(state.bodies[0]).toMatchObject({ price: '399.90' });
  });

  it('upper-cases the SKU instead of treating it as a different value', async () => {
    const user = userEvent.setup();
    const state = adminProductState();
    server.use(...state.handlers);

    renderNew();
    await fillForm(user, { sku: 'butaca-02' });
    await submit(user);

    await waitFor(() => expect(state.created).toBe(1));
    expect(state.bodies[0]).toMatchObject({ sku: 'BUTACA-02' });
  });

  it('says a SKU that is taken on the field, and offers to go and see who has it', async () => {
    const user = userEvent.setup();
    const taken = adminProduct({ id: 4, sku: 'BUTACA-01', name: 'Butaca vieja', isActive: false });
    server.use(...adminProductState({ products: [taken] }).handlers);

    renderNew();
    await fillForm(user, { sku: 'BUTACA-01' });
    await submit(user);

    expect(await screen.findByText('Ese SKU ya es de otro producto.')).toBeDefined();
    // With `status=ALL`, because the product holding it may well be a discontinued one — as this is.
    expect(screen.getByRole('link', { name: 'Ver qué producto lo tiene' }).getAttribute('href')).toBe(
      '/admin/products?sku=BUTACA-01&status=ALL',
    );
  });

  it('says a type that stopped existing on the type field, not as a failed screen', async () => {
    const user = userEvent.setup();
    server.use(
      createProductFails('RESOURCE_NOT_FOUND', 404, { resource: 'productType', id: '1' }),
      ...adminProductState().handlers,
    );

    renderNew();
    await fillForm(user);
    await submit(user);

    expect(await screen.findByText('Ese tipo ya no existe. Elige otro.')).toBeDefined();
    expect(screen.queryByText('No encontramos lo que buscas')).toBeNull();
  });

  it('refuses what the contract would reject without asking the server', async () => {
    const user = userEvent.setup();
    const state = adminProductState();
    server.use(...state.handlers);

    renderNew();
    await fillForm(user, { sku: 'ab', price: '1.234' });
    await submit(user);

    expect(await screen.findByText(/El SKU son de 3 a 32 caracteres/)).toBeDefined();
    expect(screen.getByText(/El precio es un importe en euros/)).toBeDefined();
    expect(state.created).toBe(0);
  });

  it('lands a field the server rejects on that field', async () => {
    const user = userEvent.setup();
    server.use(
      createProductFails('VALIDATION_FAILED', 400, {
        errors: [{ field: 'name', message: 'must not be blank' }],
      }),
      ...adminProductState().handlers,
    );

    renderNew();
    await fillForm(user);
    await submit(user);

    // The server's own `message` is English written for the operator and never reaches the screen.
    expect(await screen.findByText(/El nombre es obligatorio/)).toBeDefined();
    expect(screen.queryByText(/must not be blank/)).toBeNull();
  });
});
