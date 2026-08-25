import { screen, waitFor } from '@testing-library/react';
import { http as mswHttp, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { cartOf, lineOf, sofa } from '../../../test/msw/cartHandlers';
import { currentUser } from '../../../test/msw/sessionHandlers';
import { server } from '../../../test/msw/server';
import { renderWithProviders } from '../../../test/renderWithProviders';
import type { components } from '../../../types/api';
import { CartIndicator } from './CartIndicator';

type Role = components['schemas']['Role'];

const cartLink = () => screen.queryByRole('link', { name: /carrito/i });

/**
 * Renders the indicator for a given set of roles and comes back once the session is **known**,
 * counting the reads of the cart along the way.
 *
 * Waiting for the session is the whole point of the helper. Before it resolves nobody is
 * authenticated and nobody holds a role, so the component renders nothing either way: asserting
 * straight after `render` passes for an employee even when the bug is present, which is exactly the
 * mistake this helper exists to make impossible. The cart read, if there is one, is fired on the
 * render that follows.
 */
async function renderForRoles(roles: readonly Role[], lines = [lineOf(sofa, 3)]) {
  const reads = { cart: 0, session: 0 };

  server.use(
    mswHttp.get('/api/cart', () => {
      reads.cart += 1;

      return HttpResponse.json(cartOf(lines));
    }),
    mswHttp.get('/api/me', () => {
      reads.session += 1;

      return HttpResponse.json({ ...currentUser, roles });
    }),
  );

  renderWithProviders(<CartIndicator />);
  await waitFor(() => expect(reads.session).toBe(1));

  return reads;
}

describe('CartIndicator', () => {
  it('counts units and not lines, which is what a cart badge is read as', async () => {
    const reads = await renderForRoles(['CUSTOMER']);

    expect(await screen.findByText('3')).toBeDefined();
    // The positive control of the test below: this is a harness that does see the read.
    expect(reads.cart).toBe(1);
  });

  it('renders nothing for a visitor, who has no cart to count', () => {
    renderWithProviders(<CartIndicator />);

    expect(cartLink()).toBeNull();
  });

  /**
   * An employee is signed in and still has no cart (`docs/02-requisitos.md` §3, ADR-0006), so the
   * condition is the role and never `isAuthenticated`.
   *
   * The second assertion is the one that matters. A badge that is not painted would be a cosmetic
   * fix; the request behind it answered `403`, and every one of those writes an
   * `auth.authorization.denied` business event (`docs/11-observabilidad-y-logging.md` §3) — so a
   * back-office session filed a denial into a closed registry on every page it painted.
   */
  it('neither renders nor asks for the cart of an employee, who has none', async () => {
    const reads = await renderForRoles(['EMPLOYEE']);

    await waitFor(() => expect(cartLink()).toBeNull());
    expect(reads.cart).toBe(0);
  });
});
