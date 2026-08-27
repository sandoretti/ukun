import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { server } from '../../test/msw/server';
import { signedIn } from '../../test/msw/sessionHandlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import BackofficeHomePage from './BackofficeHomePage';

function renderHome() {
  renderWithProviders(<BackofficeHomePage />, { route: '/backoffice' });
}

describe('BackofficeHomePage', () => {
  it('offers an administrator everything the back-office has', async () => {
    server.use(signedIn(['EMPLOYEE', 'ADMIN']));

    renderHome();

    expect((await screen.findByRole('link', { name: /Stock/ })).getAttribute('href')).toBe(
      '/backoffice/stock',
    );
    expect(screen.getByRole('link', { name: /Productos/ }).getAttribute('href')).toBe(
      '/admin/products',
    );
    expect(screen.getByRole('link', { name: /Empleados/ }).getAttribute('href')).toBe(
      '/admin/employees',
    );
  });

  it('offers an employee who is not an administrator only what is theirs', async () => {
    server.use(signedIn(['EMPLOYEE']));

    renderHome();

    // The stock is any employee's; the catalogue and the staff talk to `/api/admin/**` and are an
    // administrator's alone (ADR-0020). Hiding them is UX and never the barrier — the gateway
    // refuses them, `ukun-api` checks the role again and `RequireRole` turns the route away.
    expect(await screen.findByRole('link', { name: /Stock/ })).toBeDefined();
    expect(screen.getAllByRole('link')).toHaveLength(1);
  });

  it('announces nothing it cannot open yet', async () => {
    server.use(signedIn(['EMPLOYEE', 'ADMIN']));

    renderHome();

    // Product types (RF-13) are still being built: a landing that lists screens which do not exist is
    // a landing that lies about the back-office.
    expect(await screen.findAllByRole('link')).toHaveLength(3);
  });
});
