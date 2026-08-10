import { describe, expect, it, vi } from 'vitest';

import { navigateTo } from '../browser/navigateTo';
import {
  SIGN_IN_PATH,
  SIGN_IN_URL,
  continueToAuthorizationServer,
  forgetReturnTo,
  readReason,
  sendToSignIn,
  startSignIn,
  takeReturnTo,
} from './signIn';

// The browser's own navigation is the boundary of this module: `window.location` cannot be spied on
// in jsdom, so the one call that leaves the document is what gets doubled.
vi.mock('../browser/navigateTo', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../browser/navigateTo')>()),
  navigateTo: vi.fn(),
}));

describe('sendToSignIn', () => {
  it('sends the browser to the sign-in screen with the reason, remembering where it was', () => {
    sendToSignIn('expired', '/account');

    expect(vi.mocked(navigateTo)).toHaveBeenCalledWith(`${SIGN_IN_PATH}?reason=expired`);
    expect(takeReturnTo()).toBe('/account');
  });

  it('hands the destination over exactly once', () => {
    sendToSignIn('required', '/account');

    expect(takeReturnTo()).toBe('/account');
    // A reload after coming back must not push the reader into a route they have already left.
    expect(takeReturnTo()).toBeUndefined();
  });

  it('does not walk into the same destination twice in a row', () => {
    sendToSignIn('required', '/account');
    takeReturnTo();

    // The restored route threw the reader out again: retrying it would be an endless round trip
    // through the authorization server, so the destination is dropped and the way back is `/`.
    sendToSignIn('expired', '/account');

    expect(takeReturnTo()).toBeUndefined();
  });

  it('remembers a different destination even right after one failed', () => {
    sendToSignIn('required', '/account');
    takeReturnTo();

    sendToSignIn('expired', '/products?name=sofa');

    expect(takeReturnTo()).toBe('/products?name=sofa');
  });

  it('forgets the destination when the session is dropped on purpose', () => {
    sendToSignIn('required', '/account');

    forgetReturnTo();

    expect(takeReturnTo()).toBeUndefined();
  });
});

describe('continueToAuthorizationServer', () => {
  it('leaves the SPA for the gateway endpoint that starts the flow', () => {
    continueToAuthorizationServer();

    expect(vi.mocked(navigateTo)).toHaveBeenCalledWith(SIGN_IN_URL);
  });

  it('leaves the remembered destination untouched', () => {
    sendToSignIn('required', '/account');

    continueToAuthorizationServer();

    // Writing `/signin` here would both replace a good destination and reset the loop brake.
    expect(takeReturnTo()).toBe('/account');
  });
});

describe('startSignIn', () => {
  it('goes straight to the authorization server, remembering where it was', () => {
    startSignIn('/products?name=sofa');

    // No stop at `/signin`: the reader asked for this, and the page they land on already wears the
    // shop's card.
    expect(vi.mocked(navigateTo)).toHaveBeenCalledWith(SIGN_IN_URL);
    expect(takeReturnTo()).toBe('/products?name=sofa');
  });

  it('shares the loop brake with the long way round', () => {
    sendToSignIn('expired', '/account');
    takeReturnTo();

    startSignIn('/account');

    // One memory, one brake, whichever way out was taken — otherwise a route that keeps throwing
    // the reader out would loop through whichever of the two paths was not counting.
    expect(takeReturnTo()).toBeUndefined();
  });
});

describe('readReason', () => {
  it.each(['expired', 'required'])('reads %s off the URL', (reason) => {
    expect(readReason(new URLSearchParams({ reason }))).toBe(reason);
  });

  it.each([
    ['a value outside the set', new URLSearchParams({ reason: 'because' })],
    ['no reason at all', new URLSearchParams()],
  ])('reads %s as no reason', (_case, params) => {
    // A hand-typed or stale value has to read as "you asked for this screen", never as a missing
    // translation key on screen.
    expect(readReason(params)).toBeUndefined();
  });
});
