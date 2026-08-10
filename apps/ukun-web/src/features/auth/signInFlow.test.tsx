import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import App from '../../app/App';
import { SIGN_IN_URL, rememberReturnTo } from '../../shared/auth/signIn';
import { navigateTo } from '../../shared/browser/navigateTo';
import { server } from '../../test/msw/server';
import { signedIn } from '../../test/msw/sessionHandlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import { ACCOUNT_PATH } from './routes';

vi.mock('../../shared/browser/navigateTo', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/browser/navigateTo')>()),
  navigateTo: vi.fn(),
}));

/**
 * The whole round trip, through the real router: a protected route sends the reader to the sign-in
 * screen, the screen is what leaves the site, and coming back lands on that same route instead of
 * the home page.
 *
 * There is no server-side saved request to lean on — the gateway's entry point answers a
 * `ProblemDetail` and `oauth2Login` always returns to `/` — so this is the only thing that makes the
 * destination survive.
 */
describe('sign-in flow', () => {
  beforeEach(() => {
    vi.mocked(navigateTo).mockClear();
  });

  it('sends a visitor asking for a protected route to the sign-in screen', async () => {
    renderWithProviders(<App />, { route: ACCOUNT_PATH });

    // It stays inside the SPA: what the guard does is redirect, and being told why is the whole
    // point of having a screen instead of a jump.
    expect(
      await screen.findByRole('heading', { name: 'Vuelve a entrar', level: 1 }),
    ).toBeDefined();
    expect(screen.getByRole('alert').textContent).toContain('Necesitas una sesión');
    expect(vi.mocked(navigateTo)).not.toHaveBeenCalled();
  });

  it('leaves the site only once the reader asks it to', async () => {
    renderWithProviders(<App />, { route: ACCOUNT_PATH });

    await userEvent.click(
      await screen.findByRole('button', { name: 'Continuar con tu cuenta Ukun' }),
    );

    await waitFor(() => expect(vi.mocked(navigateTo)).toHaveBeenCalledWith(SIGN_IN_URL));
  });

  it('comes back to the route that was asked for, not to the home page', async () => {
    // Lo que ocurrió antes de salir; y `/` es donde el gateway deja el navegador tras el callback.
    rememberReturnTo(ACCOUNT_PATH);
    server.use(signedIn());

    renderWithProviders(<App />, { route: '/' });

    expect(await screen.findByRole('heading', { name: 'Mi cuenta' })).toBeDefined();
  });

  it('leaves the public screens anonymous', async () => {
    renderWithProviders(<App />, { route: '/stores' });

    // The stores, the catalogue and the home page are read with no session (`permitAll` everywhere
    // but `/api/me/**` and `/api/admin/**`), so the probe's 401 must move nobody anywhere.
    expect(await screen.findByRole('heading', { name: 'Tiendas', level: 1 })).toBeDefined();
    expect(vi.mocked(navigateTo)).not.toHaveBeenCalled();
  });
});
