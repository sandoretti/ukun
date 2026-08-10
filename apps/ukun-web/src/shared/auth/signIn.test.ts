import { describe, expect, it, vi } from 'vitest';

import { navigateTo } from '../browser/navigateTo';
import { SIGN_IN_URL, forgetReturnTo, startSignIn, takeReturnTo } from './signIn';

// The browser's own navigation is the boundary of this module: `window.location` cannot be spied on
// in jsdom, so the one call that leaves the document is what gets doubled.
vi.mock('../browser/navigateTo', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../browser/navigateTo')>()),
  navigateTo: vi.fn(),
}));

describe('startSignIn', () => {
  it('sends the browser to the gateway, remembering where it was', () => {
    startSignIn('/account');

    expect(vi.mocked(navigateTo)).toHaveBeenCalledWith(SIGN_IN_URL);
    expect(takeReturnTo()).toBe('/account');
  });

  it('hands the destination over exactly once', () => {
    startSignIn('/account');

    expect(takeReturnTo()).toBe('/account');
    // A reload after coming back must not push the reader into a route they have already left.
    expect(takeReturnTo()).toBeUndefined();
  });

  it('does not walk into the same destination twice in a row', () => {
    startSignIn('/account');
    takeReturnTo();

    // The restored route threw the reader out again: retrying it would be an endless round trip
    // through the authorization server, so the destination is dropped and the way back is `/`.
    startSignIn('/account');

    expect(takeReturnTo()).toBeUndefined();
  });

  it('remembers a different destination even right after one failed', () => {
    startSignIn('/account');
    takeReturnTo();

    startSignIn('/products?name=sofa');

    expect(takeReturnTo()).toBe('/products?name=sofa');
  });

  it('forgets the destination when the session is dropped on purpose', () => {
    startSignIn('/account');

    forgetReturnTo();

    expect(takeReturnTo()).toBeUndefined();
  });
});
