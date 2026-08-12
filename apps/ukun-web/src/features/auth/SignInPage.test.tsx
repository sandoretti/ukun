import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { SIGN_IN_PATH, SIGN_IN_URL, takeReturnTo } from '../../shared/auth/signIn';
import { navigateTo } from '../../shared/browser/navigateTo';
import { server } from '../../test/msw/server';
import { signedIn } from '../../test/msw/sessionHandlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import SignInPage from './SignInPage';

vi.mock('../../shared/browser/navigateTo', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/browser/navigateTo')>()),
  navigateTo: vi.fn(),
}));

/** The screen as the router reaches it, with whatever reason the URL carries. */
function renderSignIn(search = '') {
  return renderWithProviders(<SignInPage />, { route: `${SIGN_IN_PATH}${search}` });
}

describe('SignInPage', () => {
  beforeEach(() => {
    vi.mocked(navigateTo).mockClear();
  });

  it('leaves the SPA for the authorization server when asked to', async () => {
    renderSignIn();

    await userEvent.click(
      await screen.findByRole('button', { name: 'Continuar con tu cuenta Ukun' }),
    );

    expect(vi.mocked(navigateTo)).toHaveBeenCalledWith(SIGN_IN_URL);
  });

  it('does not touch the destination someone else remembered', async () => {
    renderSignIn();

    await userEvent.click(
      await screen.findByRole('button', { name: 'Continuar con tu cuenta Ukun' }),
    );

    // The screen is not where the route to come back to is decided: writing `/signin` here would
    // both lose the real destination and reset the loop brake of `signIn.ts`.
    expect(takeReturnTo()).toBeUndefined();
  });

  it('says where the button leads before it is pressed', async () => {
    renderSignIn();

    // A control that takes the browser off the site warns first. It is also the honest answer to
    // "why is there no password field here" (ADR-0003).
    expect(await screen.findByText(/servidor de identidad/)).toBeDefined();
  });

  it.each([
    ['?reason=expired', 'Tu sesión ha caducado'],
    ['?reason=required', 'Necesitas una sesión'],
  ])('explains why it is being shown when the URL says %s', async (search, message) => {
    renderSignIn(search);

    expect((await screen.findByRole('alert')).textContent).toContain(message);
  });

  it.each([
    ['nobody was thrown out', ''],
    ['the reason is not one of ours', '?reason=because'],
  ])('shows no warning when %s', async (_case, search) => {
    renderSignIn(search);

    await screen.findByRole('heading', { name: 'Vuelve a entrar' });
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('does not offer signing in to someone who already has a session', async () => {
    server.use(signedIn());

    const { container } = renderSignIn();

    // It bounces home instead of painting a card nobody needs. `Navigate` renders nothing, so the
    // screen going empty is the redirect having happened — the landing itself belongs to the
    // router, and `signInFlow` is where the whole trip is checked.
    await waitFor(() => expect(container.firstChild).toBeNull());
  });

  it('offers the account somebody without one needs, as a route of this router', async () => {
    renderSignIn();

    // The two ways out of this screen are not the same kind of thing, and the markup says so:
    // signing in leaves the SPA and is a button, signing up is a screen of ours and is a link.
    const signUp = await screen.findByRole('link', { name: 'Crear cuenta' });
    expect(signUp.getAttribute('href')).toBe('/signup');
  });

  it('leaves a way back to the store, which this layout has none of', async () => {
    renderSignIn();

    const back = await screen.findByRole('link', { name: 'Volver a la tienda' });
    expect(back.getAttribute('href')).toBe('/');
  });
});
