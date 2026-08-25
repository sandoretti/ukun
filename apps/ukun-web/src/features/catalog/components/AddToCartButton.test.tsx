import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http as mswHttp, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { navigateTo } from '../../../shared/browser/navigateTo';
import { SIGN_IN_URL, takeReturnTo } from '../../../shared/auth/signIn';
import { cartState, lineOf, sofa } from '../../../test/msw/cartHandlers';
import { OPERATOR_DETAIL, problem } from '../../../test/msw/problem';
import { currentUser, signedIn } from '../../../test/msw/sessionHandlers';
import { server } from '../../../test/msw/server';
import { renderWithProviders } from '../../../test/renderWithProviders';
import type { components } from '../../../types/api';
import { AddToCartButton } from './AddToCartButton';

vi.mock('../../../shared/browser/navigateTo', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../shared/browser/navigateTo')>()),
  navigateTo: vi.fn(),
}));

type UpdateCartItemRequest = components['schemas']['UpdateCartItemRequest'];

/** Captures the quantity the sheet asked for, and lets the stateful double answer afterwards. */
function capturingPut(sent: UpdateCartItemRequest[]) {
  return mswHttp.put('/api/cart/items/:productId', async ({ request }) => {
    sent.push((await request.clone().json()) as UpdateCartItemRequest);

    return undefined;
  });
}

const addButton = () => screen.getByRole('button', { name: 'Añadir al carrito' });

describe('AddToCartButton', () => {
  beforeEach(() => {
    vi.mocked(navigateTo).mockClear();
  });

  it('sends a visitor to authenticate instead of firing a request that can only fail', async () => {
    renderWithProviders(<AddToCartButton productId={sofa.productId} />, { route: '/products/1' });

    await userEvent.click(addButton());

    expect(vi.mocked(navigateTo)).toHaveBeenCalledWith(SIGN_IN_URL);
    // And the sheet is where the trip comes back to.
    expect(takeReturnTo()).toBe('/products/1');
  });

  it('adds to the quantity already in the line, because the endpoint takes the total', async () => {
    const sent: UpdateCartItemRequest[] = [];
    server.use(capturingPut(sent), signedIn(), ...cartState([lineOf(sofa, 2)]));

    renderWithProviders(<AddToCartButton productId={sofa.productId} />, { route: '/products/1' });
    // The button waits for the cart: what it sends depends on what is in it.
    expect(await screen.findByText('Ya tienes 2 unidades en el carrito.')).toBeDefined();

    await userEvent.click(addButton());

    expect(await screen.findByText('Ya tienes 3 unidades en el carrito.')).toBeDefined();
    expect(sent).toEqual([{ quantity: 3 }]);
  });

  it('starts the line at one unit for a product that is not in the cart yet', async () => {
    const sent: UpdateCartItemRequest[] = [];
    server.use(capturingPut(sent), signedIn(), ...cartState());

    renderWithProviders(<AddToCartButton productId={sofa.productId} />, { route: '/products/1' });

    await userEvent.click(addButton());

    expect(await screen.findByText('Ya tienes 1 unidad en el carrito.')).toBeDefined();
    expect(sent).toEqual([{ quantity: 1 }]);
  });

  it('reports a failed add by its code without losing the sheet', async () => {
    server.use(
      mswHttp.put('/api/cart/items/:productId', () =>
        problem('RESOURCE_NOT_FOUND', 404, { resource: 'Product', id: '1' }),
      ),
      signedIn(),
      ...cartState(),
    );

    renderWithProviders(<AddToCartButton productId={sofa.productId} />, { route: '/products/1' });

    await userEvent.click(addButton());

    // A discontinued product answers the same 404 as one that never existed.
    expect(await screen.findByText('No encontramos lo que buscas')).toBeDefined();
    expect(screen.queryByText(OPERATOR_DETAIL)).toBeNull();
    expect(addButton()).toBeDefined();
  });

  /**
   * The sheet stays public and readable for an employee; this one affordance is not theirs. They are
   * signed in and have no cart (`docs/02-requisitos.md` §3, ADR-0006), so the button could only
   * offer a `403` — and sending them to sign in again, which is what the visitor branch does, would
   * be worse: signing in again does not change anybody's roles.
   *
   * The wait is on the session, not on the button. Before the probe answers nobody holds a role, so
   * asserting straight after `render` would pass even with the condition on `isAuthenticated`.
   */
  it('offers nothing to an employee, who is signed in and still has no cart', async () => {
    const sent: UpdateCartItemRequest[] = [];
    let sessionReads = 0;

    server.use(
      capturingPut(sent),
      mswHttp.get('/api/me', () => {
        sessionReads += 1;

        return HttpResponse.json({ ...currentUser, roles: ['EMPLOYEE'] });
      }),
      ...cartState([lineOf(sofa, 2)]),
    );

    renderWithProviders(<AddToCartButton productId={sofa.productId} />, { route: '/products/1' });
    await waitFor(() => expect(sessionReads).toBe(1));

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Añadir al carrito' })).toBeNull());
    expect(sent).toEqual([]);
  });
});
