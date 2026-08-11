import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { SIGN_IN_URL } from '../../shared/auth/signIn';
import { navigateTo } from '../../shared/browser/navigateTo';
import { server } from '../../test/msw/server';
import { signedIn } from '../../test/msw/sessionHandlers';
import {
  signupActivationPending,
  signupEmailTaken,
  signupRequests,
  signupValidationFailed,
} from '../../test/msw/signupHandlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import { SIGN_UP_PATH } from './routes';
import SignUpPage from './SignUpPage';

vi.mock('../../shared/browser/navigateTo', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/browser/navigateTo')>()),
  navigateTo: vi.fn(),
}));

/** A card number that passes Luhn, with a Visa IIN — the brand is read off it, never asked for. */
const VISA = '4242424242424242';

function renderSignUp() {
  return renderWithProviders(<SignUpPage />, { route: SIGN_UP_PATH });
}

/** The control by its label, which is also how a screen reader finds it. */
function field(label: string | RegExp) {
  return screen.getByLabelText(label);
}

/**
 * Fills in everything the contract requires, leaving the expiry at what the form already offers.
 *
 * It waits for the heading first: the screen asks the session probe whether there is already
 * somebody signed in, and while that is in flight there is no form to fill.
 */
async function fillTheForm(user: ReturnType<typeof userEvent.setup>, email = 'nueva@ukun.dev') {
  await screen.findByRole('heading', { name: 'Crea tu cuenta' });

  await user.type(field('Correo'), email);
  await user.type(field('Contraseña'), 'una frase larga de verdad');
  await user.type(field('Nombre'), 'Ana');
  await user.type(field('Apellidos'), 'García');
  await user.type(field('Calle y número'), 'Calle Uría 1');
  await user.type(field('Ciudad'), 'Oviedo');
  await user.type(field('Provincia'), 'Asturias');
  await user.type(field('Código postal'), '33003');
  await user.type(field('Número de la tarjeta'), VISA);
}

const submit = () => screen.getByRole('button', { name: 'Crear cuenta' });

describe('SignUpPage', () => {
  beforeEach(() => {
    vi.mocked(navigateTo).mockClear();
    signupRequests.length = 0;
  });

  it('creates the customer and takes them to sign in with the password they just chose', async () => {
    const user = userEvent.setup();
    renderSignUp();

    await fillTheForm(user);
    await user.click(submit());

    // `201` means the account is active, so there is nothing to announce and nothing to press: the
    // contract's own next step is the login.
    await waitFor(() => expect(vi.mocked(navigateTo)).toHaveBeenCalledWith(SIGN_IN_URL));
  });

  it('never sends the card number, and never asks for a CVV', async () => {
    const user = userEvent.setup();
    renderSignUp();

    await fillTheForm(user);
    await user.click(submit());

    await waitFor(() => expect(signupRequests).toHaveLength(1));
    const [request] = signupRequests;

    // What travels is what the contract carries — four digits and a brand read off the number, plus
    // the expiry — and the number itself is nowhere in the body (`docs/08` §5). The brand is not a
    // field of this form: nobody was asked for it.
    const now = new Date();
    expect(request.card).toEqual({
      last4: '4242',
      brand: 'VISA',
      expMonth: now.getMonth() + 1,
      expYear: now.getFullYear(),
    });
    expect(JSON.stringify(request)).not.toContain(VISA);
    expect(screen.queryByLabelText(/CVV/iu)).toBeNull();
  });

  it('says the account is created when the activation has not been confirmed yet', async () => {
    server.use(signupActivationPending());
    const user = userEvent.setup();
    renderSignUp();

    await fillTheForm(user);
    await user.click(submit());

    // `202` is a success too, and a different one: signing in may not work yet, so the way in is
    // offered instead of taken.
    expect((await screen.findByRole('alert')).textContent).toContain('Tu cuenta está creada');
    expect(vi.mocked(navigateTo)).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }));
    expect(vi.mocked(navigateTo)).toHaveBeenCalledWith(SIGN_IN_URL);
  });

  it('paints a taken email under the field that caused it', async () => {
    server.use(signupEmailTaken());
    const user = userEvent.setup();
    renderSignUp();

    await fillTheForm(user, 'joel.sandoval@email.com');
    await user.click(submit());

    // Uniqueness is the one rule this service cannot check locally, so it arrives as a `code` of its
    // own and not as a field error — and it is still painted where it can be acted on.
    expect(await screen.findByText('Ya hay una cuenta con ese correo')).toBeDefined();
    expect(document.activeElement).toBe(field('Correo'));
  });

  it('spreads a server rejection over the fields that caused it', async () => {
    server.use(
      signupValidationFailed([
        { field: 'address.postalCode', message: 'must be five digits' },
        { field: 'name', message: 'must not be blank' },
      ]),
    );
    const user = userEvent.setup();
    renderSignUp();

    await fillTheForm(user);
    await user.click(submit());

    // The server's own English `message` is dropped on purpose: what the reader sees is the same
    // sentence the client-side rule would have produced (`docs/12` §1).
    expect(await screen.findByText('El código postal son exactamente cinco cifras')).toBeDefined();
    expect(screen.getByText('Escribe un nombre de 1 a 100 caracteres')).toBeDefined();
  });

  it('refuses a mistyped card number before deriving anything from it', async () => {
    const user = userEvent.setup();
    renderSignUp();

    await fillTheForm(user);
    await user.clear(field('Número de la tarjeta'));
    // One digit off: the Luhn checksum every card number carries is what catches it.
    await user.type(field('Número de la tarjeta'), '4242 4242 4242 4243');
    await user.click(submit());

    expect(await screen.findByText('Ese número de tarjeta no es válido. Revísalo')).toBeDefined();
    expect(signupRequests).toHaveLength(0);
  });

  it('does not offer creating an account to somebody who already has one', async () => {
    server.use(signedIn());

    const { container } = renderSignUp();

    // It bounces home instead of painting a form that could only ever answer "that email is taken".
    await waitFor(() => expect(container.firstChild).toBeNull());
  });
});
