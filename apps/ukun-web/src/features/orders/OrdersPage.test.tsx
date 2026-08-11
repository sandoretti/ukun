import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import App from '../../app/App';
import { lampLine, orderHistory, orderOf, sofaLine } from '../../test/msw/orderHandlers';
import { signedIn } from '../../test/msw/sessionHandlers';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import OrdersPage from './OrdersPage';

const march = orderOf([sofaLine], { id: 1001, placedAt: '2026-03-14T10:00:00Z' });
const august = orderOf([lampLine], { id: 1002, placedAt: '2026-08-01T18:30:00Z' });

function renderOrders(route = '/orders') {
  return renderWithProviders(<OrdersPage />, { route });
}

/** The card of one order, to assert on its own figures without catching the other's. */
function cardOf(id: number): HTMLElement {
  const heading = screen.getByRole('link', { name: `Pedido n.º ${id}` });
  const card = heading.closest('article');
  if (card === null) {
    throw new Error(`No order card for ${id}`);
  }

  return card as HTMLElement;
}

describe('OrdersPage', () => {
  it('says there is nothing yet instead of treating it as an error', async () => {
    server.use(signedIn());

    renderOrders();

    expect(await screen.findByText('Todavía no has hecho ningún pedido.')).toBeDefined();
  });

  it('lists the orders with the figures the server sent', async () => {
    server.use(signedIn(), ...orderHistory([august, march]));

    renderOrders();

    expect(await screen.findByRole('link', { name: 'Pedido n.º 1002' })).toBeDefined();
    // 5 × 49,50, as it arrived — never multiplied here.
    expect(within(cardOf(1002)).getByText(/247,50/)).toBeDefined();
    expect(within(cardOf(1002)).getByText(/5 unidades/)).toBeDefined();
    // A closed enum is translated, never printed raw.
    expect(within(cardOf(1002)).getByText('Confirmado')).toBeDefined();
    expect(within(cardOf(1002)).queryByText('CONFIRMED')).toBeNull();
  });

  it('puts the date range in the URL, and asks the server for it', async () => {
    const user = userEvent.setup();
    server.use(signedIn(), ...orderHistory([august, march]));

    renderOrders();
    await screen.findByRole('link', { name: 'Pedido n.º 1001' });

    await user.type(screen.getByLabelText('Desde'), '2026-06-01');

    // The filtering is the server's: what the screen does is send the range and paint the answer.
    await waitFor(() => expect(screen.queryByRole('link', { name: 'Pedido n.º 1001' })).toBeNull());
    expect(screen.getByRole('link', { name: 'Pedido n.º 1002' })).toBeDefined();
  });

  it('reads the filter back from the URL, so a filtered history can be linked', async () => {
    server.use(signedIn(), ...orderHistory([august, march]));

    renderOrders('/orders?from=2026-06-01');

    expect(await screen.findByRole('link', { name: 'Pedido n.º 1002' })).toBeDefined();
    expect(screen.queryByRole('link', { name: 'Pedido n.º 1001' })).toBeNull();
    // The control shows what the URL says, and not an empty box over a filtered list.
    expect(screen.getByLabelText<HTMLInputElement>('Desde').value).toBe('2026-06-01');
  });

  it('offers no pagination while everything fits on one page', async () => {
    server.use(signedIn(), ...orderHistory([august, march]));

    renderOrders();
    await screen.findByRole('link', { name: 'Pedido n.º 1002' });

    expect(screen.queryByRole('navigation', { name: 'Paginación de mis pedidos' })).toBeNull();
  });

  it('opens an order from its heading', async () => {
    const user = userEvent.setup();
    server.use(signedIn(), ...orderHistory([august]));

    renderWithProviders(<App />, { route: '/orders' });

    await user.click(await screen.findByRole('link', { name: 'Pedido n.º 1002' }));

    expect(await screen.findByRole('heading', { name: 'Pedido n.º 1002', level: 1 })).toBeDefined();
    // Arriving from the history is not having just placed it.
    expect(screen.queryByText('Pedido confirmado')).toBeNull();
  });
});
