import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { server } from '../test/msw/server';
import { signedIn } from '../test/msw/sessionHandlers';
import { employmentFails } from '../test/msw/stockHandlers';
import { renderWithProviders } from '../test/renderWithProviders';
import { BackofficeLayout } from './BackofficeLayout';

/**
 * Stand-in screens. They are plain captions and not `t(...)` calls because they are not user-facing
 * text — they exist only to be found, or not found, by a query.
 */
const STOCK_SCREEN = 'Stock de prueba';
const HOME_SCREEN = 'Índice de prueba';

/**
 * The shell with a screen inside it, at a route of the back-office. The child is a stand-in on
 * purpose: what is under test is the frame and what the rail offers, not any one screen.
 */
function renderShell(route = '/backoffice/stock') {
  renderWithProviders(
    <Routes>
      <Route element={<BackofficeLayout />}>
        <Route path="/backoffice/stock" element={<h1>{STOCK_SCREEN}</h1>} />
        <Route path="/backoffice" element={<h1>{HOME_SCREEN}</h1>} />
      </Route>
    </Routes>,
    { route },
  );

  return screen.findByRole('navigation', { name: 'Navegación del back office' });
}

describe('BackofficeLayout', () => {
  it('gives an administrator every section, and each one only once', async () => {
    server.use(signedIn(['EMPLOYEE', 'ADMIN']));

    const rail = within(await renderShell());

    // Awaited, not queried straight away: the rail is drawn before `GET /api/me` answers, and until
    // it does there is no role to filter by — an empty rail is the honest first frame.
    expect((await rail.findByRole('link', { name: 'Inicio' })).getAttribute('href')).toBe(
      '/backoffice',
    );
    expect(rail.getByRole('link', { name: 'Stock' }).getAttribute('href')).toBe('/backoffice/stock');
    expect(rail.getByRole('link', { name: 'Productos' }).getAttribute('href')).toBe(
      '/admin/products',
    );
    expect(rail.getByRole('link', { name: 'Empleados' }).getAttribute('href')).toBe(
      '/admin/employees',
    );
  });

  it('gives an employee who is not an administrator a shorter rail', async () => {
    server.use(signedIn(['EMPLOYEE']));

    const rail = within(await renderShell());

    expect(await rail.findByRole('link', { name: 'Stock' })).toBeDefined();
    // The catalogue and the staff talk to `/api/admin/**` and are an administrator's alone
    // (ADR-0020). Hiding them is UX and never the barrier: the gateway refuses them and `ukun-api`
    // checks the role again.
    expect(rail.queryByRole('link', { name: 'Productos' })).toBeNull();
    expect(rail.queryByRole('link', { name: 'Empleados' })).toBeNull();
  });

  it('says which screen you are on, and not only by colouring it', async () => {
    server.use(signedIn(['EMPLOYEE']));

    const rail = within(await renderShell());

    expect((await rail.findByRole('link', { name: 'Stock' })).getAttribute('aria-current')).toBe(
      'page',
    );
    // The index would light up on every screen below it without `end`, and then two rows would claim
    // to be the one you are looking at.
    expect(rail.getByRole('link', { name: 'Inicio' }).hasAttribute('aria-current')).toBe(false);
  });

  it('keeps a way back to the shop and a way out of the session', async () => {
    server.use(signedIn(['EMPLOYEE']));

    const rail = within(await renderShell());

    // The back-office is deliberately not the shop's shell, so leaving has to be somewhere. Without
    // this the only way out was the browser's back button.
    // Its own caption, and not the «Volver a la tienda» of the no-access screen: the two can be on
    // screen together, and two links to the same place with the same name is one of them too many.
    expect((await rail.findByRole('link', { name: 'Ir a la tienda' })).getAttribute('href')).toBe('/');
    expect(rail.getByRole('button', { name: 'Cerrar sesión' }).closest('form')?.getAttribute('action')).toBe(
      '/logout',
    );
  });

  it('says which store this account works in, next to who it belongs to', async () => {
    server.use(signedIn(['EMPLOYEE', 'ADMIN']));

    await renderShell();

    // Next to the email and not over the screen: it is a fact about the session, not the subject of
    // what is being looked at — the catalogue of `/admin/products` is global and belongs to no store
    // (ADR-0020). And it is named for a screen reader, which would otherwise hear a store and an
    // email side by side with nothing saying which is which.
    expect(await screen.findByText('Tu tienda:')).toBeDefined();
    expect(screen.getByText('Ukun Gran Vía')).toBeDefined();
  });

  it('says one thing less rather than breaking when the store cannot be resolved', async () => {
    server.use(signedIn(['EMPLOYEE', 'ADMIN']), employmentFails('FORBIDDEN', 403));

    await renderShell();

    // The shell reads the query and never `EmploymentBoundary`: the boundary refuses to draw its
    // screen until the store is known, and a bar that did that would hold the whole back-office
    // hostage to one request that the screens already handle for themselves.
    expect(await screen.findByRole('link', { name: 'Stock' })).toBeDefined();
    expect(screen.queryByText('Tu tienda:')).toBeNull();
  });

  it('is not the shop: no catalogue menu, no product search, no cart', async () => {
    server.use(signedIn(['EMPLOYEE', 'ADMIN']));

    await renderShell();

    expect(screen.queryByRole('navigation', { name: 'Navegación principal' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Catálogo' })).toBeNull();
    expect(screen.queryByRole('searchbox', { name: /Buscar productos/ })).toBeNull();
  });

  it('names the captions of the mini rail even while they are off screen', async () => {
    server.use(signedIn(['EMPLOYEE']));

    const rail = within(await renderShell());

    // The rail opens narrow — it is a mini rail — and the caption is never dropped, only taken off
    // screen. That is what makes it readable without a single `aria-label` on the links.
    expect(await rail.findByRole('link', { name: 'Stock' })).toBeDefined();
    expect(rail.getByText('Stock').className).toContain('visually-hidden');
  });

  it('widens the rail, and the button says what it will do next', async () => {
    server.use(signedIn(['EMPLOYEE']));

    await renderShell();

    const widen = await screen.findByRole('button', { name: 'Ampliar el menú' });
    await screen.findByRole('link', { name: 'Stock' });
    await userEvent.click(widen);

    // Neither `aria-expanded` nor `aria-pressed`: nothing is disclosed, so what changes is the
    // action, and the name changes with it.
    expect(screen.getByRole('button', { name: 'Reducir el menú' })).toBeDefined();
    expect(screen.getByText('Stock').className).not.toContain('visually-hidden');
  });

  it('turns away an account without the employee role, inside the frame', async () => {
    server.use(signedIn(['CUSTOMER']));

    await renderShell();

    // Refused **inside** the shell and not instead of it: the rail is still there, and with it the
    // way back to the shop. Refused outside, the only way out would be the back button.
    expect(await screen.findByText('Esta página no es para tu cuenta')).toBeDefined();
    expect(screen.queryByRole('heading', { name: STOCK_SCREEN })).toBeNull();
    expect(screen.getByRole('link', { name: 'Ir a la tienda' })).toBeDefined();
  });
});
