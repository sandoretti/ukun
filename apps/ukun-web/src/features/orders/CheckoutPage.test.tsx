import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import App from '../../app/App';
import i18n from '../../i18n/config';
import {
  ATTEMPT_ID,
  checkoutAttemptFails,
  checkoutFlow,
  lampLine,
  placeOrderFails,
  sofaLine,
} from '../../test/msw/orderHandlers';
import { OPERATOR_DETAIL } from '../../test/msw/problem';
import { signedIn } from '../../test/msw/sessionHandlers';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import CheckoutPage from './CheckoutPage';

// The i18n instance is shared by the whole suite, so a test that switches language puts it back.
afterEach(async () => {
  await i18n.changeLanguage('es');
});

function renderCheckout() {
  return renderWithProviders(<CheckoutPage />, { route: '/checkout' });
}

const summary = () => screen.getByRole('complementary', { name: 'Total de la compra' });
const confirmButton = () => screen.getByRole('button', { name: 'Confirmar pedido' });

describe('CheckoutPage', () => {
  it('paints what the server froze in the attempt, and adds nothing up on its own', async () => {
    const checkout = checkoutFlow([sofaLine, lampLine]);
    server.use(signedIn(), ...checkout.handlers);

    renderCheckout();

    expect(await screen.findByText('Sofá Nordic 3 plazas')).toBeDefined();
    expect(screen.getByText('Lámpara Oslo')).toBeDefined();
    // 2 × 399,99 + 5 × 49,50, computed by the server and printed as it arrived.
    expect(within(summary()).getByText(/1047,48/)).toBeDefined();
    // One attempt for one visit: nothing refetches it behind the reader's back.
    expect(checkout.counters.attempts).toBe(1);
  });

  it('shows the address and the card the order is about to copy', async () => {
    server.use(signedIn(), ...checkoutFlow().handlers);

    renderCheckout();

    expect(await screen.findByText('Calle Uría 1')).toBeDefined();
    expect(screen.getByText(/33003 Oviedo/)).toBeDefined();
    // The card, as the profile shows it: brand, last four digits and expiry, and nothing else.
    expect(screen.getByText('Tarjeta Visa terminada en 4242, caduca en 12/27')).toBeDefined();
  });

  it('shows the same photos the cart showed, and an empty frame where there is none', async () => {
    server.use(signedIn(), ...checkoutFlow([sofaLine, lampLine]).handlers);

    renderCheckout();
    await screen.findByText('Sofá Nordic 3 plazas');

    // Decorative, so it has no accessible name: the name beside it is what identifies the line, and
    // the image is queried as the presentation-role element it is.
    const photo = screen.getByRole('presentation') as HTMLImageElement;
    expect(photo.getAttribute('src')).toBe('/media/products/sofa-nordic.jpg');
    // The line with no photo (RF-10) shows the frame's fallback instead of a broken image.
    expect(screen.getAllByRole('presentation').length).toBe(1);
    expect(screen.queryByRole('img')).toBeNull();
  });

  it('warns about a line short of stock without blocking the purchase', async () => {
    server.use(signedIn(), ...checkoutFlow([lampLine]).handlers);

    renderCheckout();

    // `available` reserves nothing (ADR-0016): it is a warning, and the button stays live.
    expect(await screen.findByText('Solo quedan 3 unidades de este producto.')).toBeDefined();
    expect(confirmButton().hasAttribute('disabled')).toBe(false);
  });

  it('sends back the attemptId the server issued, and never a key of its own', async () => {
    const user = userEvent.setup();
    const checkout = checkoutFlow();
    server.use(signedIn(), ...checkout.handlers);

    renderCheckout();
    await screen.findByText('Sofá Nordic 3 plazas');

    await user.click(confirmButton());

    // The whole point of ADR-0016: the browser echoes the server's identifier verbatim.
    await waitFor(() => expect(checkout.seenKeys).toEqual([ATTEMPT_ID]));
  });

  it('tells an empty cart apart from a failure, and offers the way out', async () => {
    server.use(signedIn(), checkoutAttemptFails('EMPTY_CART', 422));

    renderCheckout();

    expect(await screen.findByText('Tu carrito está vacío')).toBeDefined();
    expect(screen.getByRole('link', { name: 'Volver al carrito' })).toBeDefined();
    // The operator's `detail` is written for the logs and never reaches the screen.
    expect(screen.queryByText(OPERATOR_DETAIL)).toBeNull();
  });

  it.each([
    ['CHECKOUT_EXPIRED', 410, {}, 'Tu compra ha tardado demasiado y este resumen ya no vale. Vuelve a empezar'],
    [
      'INSUFFICIENT_STOCK',
      409,
      { sku: 'LAMP-OSLO', available: 3, requested: 5 },
      'Solo quedan 3 unidades de LAMP-OSLO',
    ],
    ['IDEMPOTENCY_IN_PROGRESS', 409, {}, 'Ya estamos procesando esta compra. Espera un momento'],
    [
      'IDEMPOTENCY_MISMATCH',
      422,
      {},
      'Esta compra ya se envió con otros datos. Vuelve a empezar',
    ],
  ] as const)('explains %s in the reader\'s words, never with the raw detail', async (code, status, extensions, message) => {
    const user = userEvent.setup();
    server.use(signedIn(), placeOrderFails(code, status, extensions), ...checkoutFlow().handlers);

    renderCheckout();
    await screen.findByText('Sofá Nordic 3 plazas');

    await user.click(confirmButton());

    expect(await screen.findByText(message)).toBeDefined();
    expect(screen.queryByText(OPERATOR_DETAIL)).toBeNull();
    // A failure never wipes what was on screen: the lines are still there to try again from.
    expect(screen.getByText('Sofá Nordic 3 plazas')).toBeDefined();
  });

  it('says the same in English, code by code', async () => {
    const user = userEvent.setup();
    await i18n.changeLanguage('en');
    server.use(signedIn(), placeOrderFails('CHECKOUT_EXPIRED', 410), ...checkoutFlow().handlers);

    renderCheckout();
    await screen.findByRole('button', { name: 'Place order' });

    await user.click(screen.getByRole('button', { name: 'Place order' }));

    expect(
      await screen.findByText('Your checkout took too long and this summary is no longer valid. Please start again'),
    ).toBeDefined();
  });

  it('opens a new attempt when the old one is gone, and only when asked to', async () => {
    const user = userEvent.setup();
    const checkout = checkoutFlow();
    server.use(signedIn(), placeOrderFails('CHECKOUT_EXPIRED', 410), ...checkout.handlers);

    renderCheckout();
    await screen.findByText('Sofá Nordic 3 plazas');
    await user.click(confirmButton());
    await screen.findByText(/Tu compra ha tardado demasiado/);

    // Still one: a `410` does not silently reopen anything, or the reader would never learn that
    // the prices they were looking at are not the prices any more.
    expect(checkout.counters.attempts).toBe(1);

    await user.click(screen.getByRole('button', { name: 'Empezar de nuevo' }));

    await waitFor(() => expect(checkout.counters.attempts).toBe(2));
    // The message goes with the attempt it was about.
    await waitFor(() => expect(screen.queryByText(/Tu compra ha tardado demasiado/)).toBeNull());
  });
});

/**
 * The flow through the real router, which is where "confirming twice" can be asked at all: the
 * screen navigates to the order it just created, and that navigation is part of what is being
 * tested.
 */
describe('checkout flow', () => {
  it('places one order when the button is pressed twice, and lands on it', async () => {
    const user = userEvent.setup();
    const checkout = checkoutFlow();
    server.use(signedIn(), ...checkout.handlers);

    renderWithProviders(<App />, { route: '/checkout' });
    await screen.findByText('Sofá Nordic 3 plazas');

    const confirm = confirmButton();
    await user.click(confirm);
    await user.click(confirm);

    expect(await screen.findByRole('heading', { name: 'Pedido n.º 1001', level: 1 })).toBeDefined();
    expect(checkout.counters.orders).toBe(1);
    // Landed on the order, and told so: the banner belongs to this visit, not to the order.
    expect(screen.getByText('Pedido confirmado')).toBeDefined();
  });
});
