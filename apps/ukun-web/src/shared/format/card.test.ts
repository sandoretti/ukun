import { describe, expect, it } from 'vitest';

import { formatCardExpiry, maskedCardNumber } from './card';

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
