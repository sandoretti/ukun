import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Route, Routes } from 'react-router-dom';

import { lampLine, orderHistory, orderOf, sofaLine } from '../../test/msw/orderHandlers';
import { OPERATOR_DETAIL } from '../../test/msw/problem';
import { signedIn } from '../../test/msw/sessionHandlers';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import OrderDetailPage from './OrderDetailPage';

const order = orderOf([sofaLine, lampLine], { id: 1001, placedAt: '2026-08-01T18:30:00Z' });

/** The route pattern, because the screen reads its id from the path and not from a prop. */
function renderOrder(id: number) {
  return renderWithProviders(
    <Routes>
      <Route path="/orders/:id" element={<OrderDetailPage />} />
    </Routes>,
    { route: `/orders/${id}` },
  );
}

describe('OrderDetailPage', () => {
  it('prints the lines and the total the order was closed with', async () => {
    server.use(signedIn(), ...orderHistory([order]));

    renderOrder(1001);

    expect(await screen.findByRole('heading', { name: 'Pedido n.º 1001', level: 1 })).toBeDefined();

    // Tabular data in a table, with a header per column and the total in the footer.
    const lines = screen.getByRole('table');
    expect(within(lines).getByRole('columnheader', { name: 'Precio unitario' })).toBeDefined();
    expect(within(lines).getByText('Sofá Nordic 3 plazas')).toBeDefined();
    expect(within(lines).getByText(/799,98/)).toBeDefined();
    expect(within(lines).getByText(/1047,48/)).toBeDefined();
  });

  it('says out loud that shipping and payment are snapshots', async () => {
    server.use(signedIn(), ...orderHistory([order]));

    renderOrder(1001);

    // The address the order froze (ADR-0011), and the sentence that explains it is frozen — which is
    // the legacy's worst behavioural bug not being reproduced (defect #19).
    expect(await screen.findByText('Calle Uría 1')).toBeDefined();
    expect(screen.getByText(/cambiar tu perfil no los altera/)).toBeDefined();
  });

  it('treats someone else\'s order as one that is not there', async () => {
    server.use(signedIn(), ...orderHistory([order]));

    renderOrder(4242);

    // A `404` and not a `403`: an existence oracle over other people's orders would leak how many
    // the shop has taken. The screen paints the code, never the operator's `detail`.
    expect(await screen.findByText('No encontramos lo que buscas')).toBeDefined();
    expect(screen.queryByText(OPERATOR_DETAIL)).toBeNull();
  });
});
