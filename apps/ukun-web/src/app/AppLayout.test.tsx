import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '../test/renderWithProviders';
import { AppLayout } from './AppLayout';

/** Every caption of the shell whose screen is not built yet. */
const NOT_BUILT_YET = ['Sobre Ukun'];

function renderShell() {
  renderWithProviders(<AppLayout />);
  // The footer repeats some of these captions, so every assertion is scoped to the bar. Both
  // landmarks being reachable by name is itself part of what is being checked.
  return within(screen.getByRole('navigation', { name: 'Navegación principal' }));
}

describe('AppLayout', () => {
  it('takes the brand back to the home page, named after it', () => {
    const bar = renderShell();

    const brand = bar.getByRole('link', { name: 'Ukun' });
    expect(brand.getAttribute('href')).toBe('/');
    // The logotype is a bitmap, so the link's only possible name is its alternative text.
    expect(within(brand).getByRole('img').getAttribute('alt')).toBe('Ukun');
  });

  it('only links the sections that exist', () => {
    const bar = renderShell();

    const links = bar.getAllByRole('link').map((link) => link.getAttribute('href'));
    expect(links).toEqual(['/', '/products', '/stores', '/signup']);
  });

  it('offers the way in to a visitor, and it is not a route of this router', () => {
    const bar = renderShell();

    // Signing in navigates out of the SPA (`/oauth2/authorization/ukun`), so the control is an
    // enabled button and never a link. It does not stop at `/signin` either: that screen is for
    // someone who was thrown out and is owed a reason, not for someone who just asked.
    const signIn = bar.getByRole('button', { name: 'Iniciar sesión' });
    expect(signIn.hasAttribute('disabled')).toBe(false);
  });

  it('offers the way up next to it, and that one is a route', () => {
    const bar = renderShell();

    // Signing up is the other half of the pair and behaves the opposite way: the customer is
    // created by `ukun-api` (RF-06, `docs/08` §3), so the screen belongs to this router and the
    // control is a link with an `href`.
    expect(bar.getByRole('link', { name: 'Crear cuenta' }).getAttribute('href')).toBe('/signup');
  });

  it.each(NOT_BUILT_YET)('offers %s without turning it into a dead link', (label) => {
    const bar = renderShell();

    // A destination that does not exist is never an `<a>`: a link to a blank screen is worse than
    // no link. It is a disabled button, and what the dimming says is also written for a screen
    // reader — the accessible name carries the "coming soon" qualifier.
    expect(bar.queryByRole('link', { name: new RegExp(label) })).toBeNull();

    const control = bar.getByRole('button', { name: `${label} (próximamente)` });
    expect(control.hasAttribute('disabled')).toBe(true);
  });
});
