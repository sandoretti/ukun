import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '../test/renderWithProviders';
import { AppLayout } from './AppLayout';

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

  it('links every section of the bar, and offers no control that goes nowhere', () => {
    const bar = renderShell();

    const links = bar.getAllByRole('link').map((link) => link.getAttribute('href'));
    expect(links).toEqual(['/', '/products', '/stores', '/about', '/signup']);

    // What this replaced: the bar used to carry two captions rendered as disabled buttons, for
    // screens that did not exist. «Novedades» went for good and «Sobre Ukun» became a route, so the
    // only button left in the bar is the way in — asserted below to be enabled. A dimmed control
    // reappearing here means someone added an entry before its screen.
    const disabled = bar
      .getAllByRole('button')
      .filter((control) => control.hasAttribute('disabled'));
    expect(disabled).toEqual([]);
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

  it('sends «Sobre Ukun» to its own screen and not to the catalogue', () => {
    const bar = renderShell();

    // It was the last disabled button of the bar. The assertion is on the `href` and not merely on
    // the caption being a link, because the entry existing without a route was exactly the state
    // this replaced.
    expect(bar.getByRole('link', { name: 'Sobre Ukun' }).getAttribute('href')).toBe('/about');
  });
});
