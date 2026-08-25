import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { server } from '../../test/msw/server';
import { signedIn } from '../../test/msw/sessionHandlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import { RequireRole } from './RequireRole';

/** Stands in for whatever the guard is protecting. A constant so the JSX carries no literal. */
const GUARDED = 'El stock de la tienda';

function renderGuarded() {
  return renderWithProviders(
    <RequireRole role="EMPLOYEE">
      <h1>{GUARDED}</h1>
    </RequireRole>,
    { route: '/backoffice/stock' },
  );
}

describe('RequireRole', () => {
  it('lets an account with the role through', async () => {
    server.use(signedIn(['EMPLOYEE']));

    renderGuarded();

    expect(await screen.findByRole('heading', { name: GUARDED })).toBeDefined();
  });

  it('lets an administrator through, because ADMIN only ever travels with EMPLOYEE', async () => {
    server.use(signedIn(['EMPLOYEE', 'ADMIN']));

    renderGuarded();

    expect(await screen.findByRole('heading', { name: GUARDED })).toBeDefined();
  });

  it('explains itself to an account without the role instead of sending it to sign in again', async () => {
    server.use(signedIn(['CUSTOMER']));

    renderGuarded();

    expect(await screen.findByText('Esta página no es para tu cuenta')).toBeDefined();
    // Signing in again gives the same roles back, so there is nowhere to send anybody: the only way
    // out offered is the shop.
    expect(screen.getByRole('link', { name: 'Volver a la tienda' }).getAttribute('href')).toBe('/');
    expect(screen.queryByRole('heading', { name: GUARDED })).toBeNull();
  });

  it('decides nothing while the session probe is still in flight', () => {
    server.use(signedIn(['EMPLOYEE']));

    renderGuarded();

    // Neither the screen nor the refusal: the roles are not known yet, and guessing either way would
    // flash the wrong one on every page load.
    expect(screen.getByRole('status')).toBeDefined();
    expect(screen.queryByText('Esta página no es para tu cuenta')).toBeNull();
    expect(screen.queryByRole('heading', { name: GUARDED })).toBeNull();
  });
});
