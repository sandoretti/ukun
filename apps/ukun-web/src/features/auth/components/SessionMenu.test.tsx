import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { navigateTo } from '../../../shared/browser/navigateTo';
import { SIGN_IN_URL, takeReturnTo } from '../../../shared/auth/signIn';
import { server } from '../../../test/msw/server';
import { currentUser, signedIn } from '../../../test/msw/sessionHandlers';
import { renderWithProviders } from '../../../test/renderWithProviders';
import { SessionMenu } from './SessionMenu';

vi.mock('../../../shared/browser/navigateTo', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/browser/navigateTo')>()),
  navigateTo: vi.fn(),
}));

/**
 * Renders the bar's account block and opens it: react-bootstrap only mounts the menu when open.
 * Nothing else is on screen here, so what the menu holds is what the queries find.
 */
async function openSessionMenu() {
  renderWithProviders(<SessionMenu />);

  const toggle = await screen.findByRole('button', { name: currentUser.email });
  await userEvent.click(toggle);
}

describe('SessionMenu', () => {
  beforeEach(() => {
    vi.mocked(navigateTo).mockClear();
  });

  it('offers the way in to a visitor, and takes it straight to the authorization server', async () => {
    renderWithProviders(<SessionMenu />, { route: '/products' });

    // A navigation out of the SPA, so it is a button and never a link: there is no route of this
    // router behind it, and `/signin` is not a stop on this path either.
    await userEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }));

    expect(vi.mocked(navigateTo)).toHaveBeenCalledWith(SIGN_IN_URL);
    // And the route being left is marked, so the trip back lands on it.
    expect(takeReturnTo()).toBe('/products');
  });

  it('names the session by the account it belongs to', async () => {
    server.use(signedIn());

    renderWithProviders(<SessionMenu />);

    expect(await screen.findByRole('button', { name: currentUser.email })).toBeDefined();
  });

  it('signs out by submitting a form to the gateway, with the CSRF token of the cookie', async () => {
    document.cookie = 'XSRF-TOKEN=a-token';
    server.use(signedIn());

    await openSessionMenu();

    // A navigation and not an XHR: the answer is a redirect to another origin, and only the browser
    // can follow it far enough to end the session at `ukun-auth` as well.
    const form = screen.getByRole('button', { name: /Cerrar sesión/ }).closest('form');
    expect(form?.getAttribute('action')).toBe('/logout');
    expect(form?.getAttribute('method')).toBe('post');
    // Cookie and field are the same string, literally: the gateway compares them without un-XORing.
    expect(form?.querySelector('input[name="_csrf"]')?.getAttribute('value')).toBe('a-token');
  });

  it('offers the back office to staff only', async () => {
    server.use(signedIn(['EMPLOYEE']));

    await openSessionMenu();

    expect(screen.getByRole('button', { name: /Back office/ })).toBeDefined();
  });

  it('does not offer the back office to a customer', async () => {
    server.use(signedIn(['CUSTOMER']));

    await openSessionMenu();

    // Hiding it is UX, never the barrier: the gateway refuses `/api/admin/**` without the role and
    // `ukun-api` checks it again.
    expect(screen.queryByRole('button', { name: /Back office/ })).toBeNull();
  });
});
