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
    expect(screen.getByRole('link', { name: /Tipos de producto/ }).getAttribute('href')).toBe(
      '/admin/product-types',
    );
    expect(screen.getByRole('link', { name: /^Productos/ }).getAttribute('href')).toBe(
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

    // The four sections `sections.ts` holds, and not one card more: a landing that lists a screen
    // which does not exist is a landing that lies about the back-office. The number is asserted, and
    // not only the four names, because that is the half a new entry with no route would break.
    expect(await screen.findAllByRole('link')).toHaveLength(4);
  });
});
