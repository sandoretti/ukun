import { describe, expect, it } from 'vitest';

import {
  cardDigits,
  detectCardBrand,
  formatCardExpiry,
  isPlausibleCardNumber,
  maskedCardNumber,
} from './card';

// Test numbers published by the payment networks for exactly this. None of them is anybody's card.
const VISA = '4242424242424242';
const MASTERCARD_OLD = '5555555555554444';
const MASTERCARD_NEW = '2223003122003222';
const AMEX = '378282246310005';

describe('cardDigits', () => {
  it('keeps the digits of a number typed the way it is printed', () => {
    expect(cardDigits('4242 4242 4242 4242')).toBe(VISA);
    expect(cardDigits('4242-4242-4242-4242')).toBe(VISA);
  });
});

describe('detectCardBrand', () => {
  it('reads the brand off the number, so nobody has to be asked for it', () => {
    expect(detectCardBrand(VISA)).toBe('VISA');
    expect(detectCardBrand(MASTERCARD_OLD)).toBe('MASTERCARD');
    expect(detectCardBrand(AMEX)).toBe('AMEX');
  });

  it('knows the 2221-2720 range Mastercard added, not just the old 51-55 one', () => {
    expect(detectCardBrand(MASTERCARD_NEW)).toBe('MASTERCARD');
  });

  it('answers OTHER for a card outside the four the contract knows, instead of guessing', () => {
    // A Diners Club number: a real card, and not one of our four.
    expect(detectCardBrand('36227206271667')).toBe('OTHER');
  });
});

describe('isPlausibleCardNumber', () => {
  it('accepts the published test numbers of every brand', () => {
    expect(isPlausibleCardNumber(VISA)).toBe(true);
    expect(isPlausibleCardNumber(MASTERCARD_OLD)).toBe(true);
    expect(isPlausibleCardNumber(MASTERCARD_NEW)).toBe(true);
    expect(isPlausibleCardNumber(AMEX)).toBe(true);
  });

  it('catches a single mistyped digit through the checksum', () => {
    // The whole point: without this, a typo would be stored as somebody else's last four.
    expect(isPlausibleCardNumber('4242424242424243')).toBe(false);
  });

  it('refuses a number of the wrong length for its brand', () => {
    // Fifteen digits is an American Express length, not a Visa one, checksum or no checksum.
    expect(isPlausibleCardNumber('424242424242424')).toBe(false);
    expect(isPlausibleCardNumber('')).toBe(false);
  });
});

describe('maskedCardNumber', () => {
  it('gives a sixteen-digit card the shape of a sixteen-digit card', () => {
    expect(maskedCardNumber({ brand: 'VISA', last4: '4242' })).toBe('•••• •••• •••• 4242');
    expect(maskedCardNumber({ brand: 'MASTERCARD', last4: '5100' })).toBe('•••• •••• •••• 5100');
  });

  it('prints an American Express in its own 4-6-5 grouping, not in four groups of four', () => {
    // Fifteen digits, and the last group is five: `•3456`, not `3456` short of the line.
    expect(maskedCardNumber({ brand: 'AMEX', last4: '3456' })).toBe('•••• •••••• •3456');
  });

  it('falls back to the short form for a card whose length nobody knows', () => {
    expect(maskedCardNumber({ brand: 'OTHER', last4: '9999' })).toBe('•••• 9999');
  });
});

describe('formatCardExpiry', () => {
  it('prints the expiry the way the card itself does', () => {
    expect(formatCardExpiry({ expMonth: 12, expYear: 2027 })).toBe('12/27');
  });

  it('pads a single-digit month, so a column of cards lines up', () => {
    expect(formatCardExpiry({ expMonth: 1, expYear: 2030 })).toBe('01/30');
  });

  it('pads the turn of the century instead of dropping to one digit', () => {
    expect(formatCardExpiry({ expMonth: 9, expYear: 2100 })).toBe('09/00');
  });
});
