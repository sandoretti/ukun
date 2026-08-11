import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import i18n from '../../i18n/config';
import { cartFails, cartState, lamp, lineOf, putCartItemFails, sofa } from '../../test/msw/cartHandlers';
import { signedIn } from '../../test/msw/sessionHandlers';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { LanguageSwitcher } from '../../shared/ui/LanguageSwitcher';
import CartPage from './CartPage';

// The i18n instance is shared by the whole suite, so a test that switches language puts it back.
afterEach(async () => {
  await i18n.changeLanguage('es');
});

/** Two lines: one comfortably in stock, and one asking for more units than the store has. */
const twoLines = [lineOf(sofa, 2), lineOf(lamp, 5)];

function renderCart() {
  return renderWithProviders(<CartPage />, { route: '/cart' });
}

const increase = (product: string) => screen.getByRole('button', { name: `Añadir una unidad de ${product}` });
const decrease = (product: string) => screen.getByRole('button', { name: `Quitar una unidad de ${product}` });
const remove = (product: string) => screen.getByRole('button', { name: `Eliminar ${product} del carrito` });

describe('CartPage', () => {
  it('says the cart is empty instead of treating it as an error', async () => {
    server.use(signedIn());

    renderCart();

    expect(await screen.findByText('Tu carrito está vacío')).toBeDefined();
    // Nothing to empty and nothing to add up: neither control belongs on an empty cart.
    expect(screen.queryByRole('button', { name: 'Vaciar carrito' })).toBeNull();
  });

  it('sends the new total quantity and paints the totals the server answered with', async () => {
    const user = userEvent.setup();
    server.use(signedIn(), ...cartState(twoLines));

    renderCart();
    await screen.findByText('Sofá Nordic 3 plazas');

    await user.click(increase('Sofá Nordic 3 plazas'));

    // 3 × 399,99 for the line, and the cart total that came with it — never recomputed here.
    expect(await screen.findByText(/1199,97/)).toBeDefined();
    expect(await screen.findByText(/1447,47/)).toBeDefined();
  });

  it('stops the stepper at one unit, where removing is the way out', async () => {
    server.use(signedIn(), ...cartState([lineOf(sofa, 1)]));

    renderCart();
    await screen.findByText('Sofá Nordic 3 plazas');

    expect(decrease('Sofá Nordic 3 plazas').hasAttribute('disabled')).toBe(true);
    expect(increase('Sofá Nordic 3 plazas').hasAttribute('disabled')).toBe(false);
  });

  it('removes one line and leaves the rest of the cart alone', async () => {
    const user = userEvent.setup();
    server.use(signedIn(), ...cartState(twoLines));

    renderCart();
    await screen.findByText('Lámpara Oslo');

    await user.click(remove('Lámpara Oslo'));

    await waitFor(() => expect(screen.queryByText('Lámpara Oslo')).toBeNull());
    expect(screen.getByText('Sofá Nordic 3 plazas')).toBeDefined();
    // The total came back down with the line, and it came from the server: 1047,48 is gone.
    await waitFor(() => expect(screen.queryByText(/1047,48/)).toBeNull());
    expect(screen.getAllByText(/799,98/).length).toBe(2);
  });

  it('asks before emptying the cart, and empties it once confirmed', async () => {
    const user = userEvent.setup();
    server.use(signedIn(), ...cartState(twoLines));

    renderCart();
    await screen.findByText('Sofá Nordic 3 plazas');

    await user.click(screen.getByRole('button', { name: 'Vaciar carrito' }));

    // The question is asked, and nothing has been deleted yet.
    expect(await screen.findByRole('dialog', { name: '¿Vaciar el carrito?' })).toBeDefined();
    expect(screen.getByText('Sofá Nordic 3 plazas')).toBeDefined();

    await user.click(screen.getByRole('button', { name: 'Sí, vaciar' }));

    expect(await screen.findByText('Tu carrito está vacío')).toBeDefined();
  });

  it('keeps the cart as it was when the confirmation is dismissed', async () => {
    const user = userEvent.setup();
    server.use(signedIn(), ...cartState(twoLines));

    renderCart();
    await screen.findByText('Sofá Nordic 3 plazas');

    await user.click(screen.getByRole('button', { name: 'Vaciar carrito' }));
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.getByText('Sofá Nordic 3 plazas')).toBeDefined();
    expect(screen.getByText('Lámpara Oslo')).toBeDefined();
  });

  it('warns when a line asks for more units than the store has, without blocking the stepper', async () => {
    server.use(signedIn(), ...cartState(twoLines));

    renderCart();

    // Availability is reported, not enforced: the barrier is order confirmation (RF-04).
    expect(await screen.findByText('Solo quedan 3 unidades en la tienda online')).toBeDefined();
    expect(increase('Lámpara Oslo').hasAttribute('disabled')).toBe(false);
    // The line that fits in stock says nothing.
    expect(screen.queryByText(/Solo quedan 7/)).toBeNull();
  });

  it('paints a failed read by its code, never the detail written for the operator', async () => {
    // An employee opening the cart: the API answers `403 FORBIDDEN`, and a 4xx is a final answer
    // that is not retried (`retryUnlessClientError`).
    server.use(signedIn(), cartFails('FORBIDDEN', 403));

    renderCart();

    expect(await screen.findByText('Tu cuenta no tiene acceso a esto')).toBeDefined();
    expect(screen.queryByText('Fixed English detail nobody should ever read on screen')).toBeNull();
  });

  it('reports a failed write without wiping the cart that is already on screen', async () => {
    const user = userEvent.setup();
    // The failing upsert goes first: `server.use` keeps the order it is given, and the state's own
    // `PUT` would answer before it otherwise.
    server.use(putCartItemFails('INTERNAL_ERROR', 500), signedIn(), ...cartState(twoLines));

    renderCart();
    await screen.findByText('Sofá Nordic 3 plazas');

    await user.click(increase('Sofá Nordic 3 plazas'));

    expect(await screen.findByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo')).toBeDefined();
    expect(screen.getByText('Sofá Nordic 3 plazas')).toBeDefined();
    expect(screen.getByText('Lámpara Oslo')).toBeDefined();
  });

  it('drops the previous failure once another action succeeds', async () => {
    const user = userEvent.setup();
    server.use(putCartItemFails('INTERNAL_ERROR', 500), signedIn(), ...cartState(twoLines));

    renderCart();
    await screen.findByText('Sofá Nordic 3 plazas');

    await user.click(increase('Sofá Nordic 3 plazas'));
    await screen.findByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo');

    await user.click(remove('Lámpara Oslo'));

    // The removal worked: leaving the older message up would describe a cart that is now right.
    await waitFor(() =>
      expect(screen.queryByText('Algo ha fallado por nuestra parte. Inténtalo de nuevo')).toBeNull(),
    );
    expect(screen.queryByText('Lámpara Oslo')).toBeNull();
  });

  it('formats the amounts in the active language', async () => {
    const user = userEvent.setup();
    server.use(signedIn(), ...cartState([lineOf(sofa, 2)]));

    renderWithProviders(
      <>
        <LanguageSwitcher />
        <CartPage />
      </>,
      { route: '/cart' },
    );
    // The line total and the cart total, both of them: one line, one amount.
    expect((await screen.findAllByText(/799,98/)).length).toBe(2);

    await user.click(screen.getByRole('button', { name: 'Inglés' }));

    // Same exact decimal string from the API, read as English money: separators are the frontend's.
    expect((await screen.findAllByText(/€799\.98/)).length).toBe(2);
    expect(screen.getByText('Your cart')).toBeDefined();
  });
});
