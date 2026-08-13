import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { OPERATOR_DETAIL } from '../../test/msw/problem';
import { server } from '../../test/msw/server';
import { signedIn } from '../../test/msw/sessionHandlers';
import { createStockFails, stockState } from '../../test/msw/stockHandlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import NewStockLinePage from './NewStockLinePage';

const SOFA_SKU = 'SOFA-CUERO-01';
const SOFA = 'Sofá de cuero';

const renderAdd = (route = '/backoffice/stock/new') =>
  renderWithProviders(<NewStockLinePage />, { route });

async function resolveSku(user: ReturnType<typeof userEvent.setup>, sku: string) {
  await user.type(await screen.findByLabelText('SKU del producto'), sku);
  await user.click(screen.getByRole('button', { name: 'Buscar' }));
}

async function typeQuantity(user: ReturnType<typeof userEvent.setup>, value: string) {
  await user.type(await screen.findByLabelText('Unidades en Ukun Gran Vía'), value);
}

describe('NewStockLinePage', () => {
  it('resolves the SKU and shows the product before asking for a quantity', async () => {
    const user = userEvent.setup();
    server.use(signedIn(['EMPLOYEE']), ...stockState().handlers);

    renderAdd();
    await resolveSku(user, SOFA_SKU);

    expect(await screen.findByText(SOFA)).toBeDefined();
    expect(screen.getByLabelText('Unidades en Ukun Gran Vía')).toBeDefined();
  });

  it('takes the SKU from the URL and resolves it on arrival', async () => {
    server.use(signedIn(['EMPLOYEE']), ...stockState().handlers);

    renderAdd(`/backoffice/stock/new?sku=${SOFA_SKU}`);

    // No typing and no clicking: this is the link every "not stocked" row points at.
    expect(await screen.findByText(SOFA)).toBeDefined();
  });

  it('says a discontinued SKU on the field, not as a failure of the screen', async () => {
    const user = userEvent.setup();
    server.use(signedIn(['EMPLOYEE']), ...stockState().handlers);

    renderAdd();
    await resolveSku(user, 'NO-EXISTE-01');

    expect(await screen.findByText(/No hay ningún producto activo con ese SKU/)).toBeDefined();
    // A `404` here is the contract's right answer, so nothing generic is painted over it.
    expect(screen.queryByText(OPERATOR_DETAIL)).toBeNull();
    expect(screen.queryByText('No encontramos lo que buscas')).toBeNull();
  });

  it('refuses a SKU the contract would reject without asking the server', async () => {
    const user = userEvent.setup();
    const state = stockState();
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderAdd();
    await resolveSku(user, 'ab');

    expect(await screen.findByText(/El SKU son de 3 a 32 caracteres/)).toBeDefined();
    expect(state.searches).toEqual([]);
  });

  it('creates the line and lands on the listing filtered by that SKU, where the new row is', async () => {
    const user = userEvent.setup();
    const state = stockState();
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderAdd();
    await resolveSku(user, SOFA_SKU);
    await typeQuantity(user, '12');
    await user.click(screen.getByRole('button', { name: 'Dar de alta' }));

    await waitFor(() => expect(state.counters.created).toBe(1));
    // The store came from `/api/me/employment`; the screen composed no identifier of its own.
    expect(state.urls).toEqual(['/api/stores/1/stock']);
  });

  it('offers the existing line when the product already has one here, instead of failing', async () => {
    const user = userEvent.setup();
    server.use(
      createStockFails('REFERENTIAL_INTEGRITY', 409),
      signedIn(['EMPLOYEE']),
      ...stockState().handlers,
    );

    renderAdd();
    await resolveSku(user, SOFA_SKU);
    await typeQuantity(user, '12');
    await user.click(screen.getByRole('button', { name: 'Dar de alta' }));

    expect(await screen.findByText('Ese producto ya tiene línea en esta tienda')).toBeDefined();
    expect(screen.getByRole('link', { name: 'Ir a la línea existente' }).getAttribute('href')).toBe(
      `/backoffice/stock?sku=${SOFA_SKU}`,
    );
  });

  it('validates the quantity in the browser: nothing is created', async () => {
    const user = userEvent.setup();
    const state = stockState();
    server.use(signedIn(['EMPLOYEE']), ...state.handlers);

    renderAdd();
    await resolveSku(user, SOFA_SKU);
    await typeQuantity(user, '-2');
    await user.click(screen.getByRole('button', { name: 'Dar de alta' }));

    expect(await screen.findByText('Las unidades son un número entero de 0 o más.')).toBeDefined();
    expect(state.counters.created).toBe(0);
  });

  it('lands a field the server rejects on that field', async () => {
    const user = userEvent.setup();
    server.use(
      createStockFails('VALIDATION_FAILED', 400, {
        errors: [{ field: 'quantity', message: 'must be greater than or equal to 0' }],
      }),
      signedIn(['EMPLOYEE']),
      ...stockState().handlers,
    );

    renderAdd();
    await resolveSku(user, SOFA_SKU);
    await typeQuantity(user, '3');
    await user.click(screen.getByRole('button', { name: 'Dar de alta' }));

    // The server's own `message` is English written for the operator and never reaches the screen.
    expect(await screen.findByText('Las unidades son un número entero de 0 o más.')).toBeDefined();
    expect(screen.queryByText(/must be greater than/)).toBeNull();
  });

  it('lets the employee go back and look for another SKU', async () => {
    const user = userEvent.setup();
    server.use(signedIn(['EMPLOYEE']), ...stockState().handlers);

    renderAdd();
    await resolveSku(user, SOFA_SKU);
    await screen.findByText(SOFA);

    await user.click(screen.getByRole('button', { name: 'Buscar otro SKU' }));

    expect(await screen.findByLabelText('SKU del producto')).toBeDefined();
    expect(screen.queryByLabelText('Unidades en Ukun Gran Vía')).toBeNull();
  });
});
