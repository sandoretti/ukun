import { describe, expect, it } from 'vitest';

import { formatMoney } from './money';

describe('formatMoney', () => {
  it('formats an amount in euros for each locale', () => {
    // Assertions stay tolerant of the ICU build's separators and whitespace: what is being checked
    // is the currency, the digits and the decimals, not the exact glyph between them.
    expect(formatMoney('12345.50', 'es').replace(/\s/g, ' ')).toMatch(/12\.345,50\s?€/);
    expect(formatMoney('12345.50', 'en').replace(/\s/g, ' ')).toMatch(/€\s?12,345\.50/);
  });

  it('keeps the exact decimals of the contract string', () => {
    expect(formatMoney('399.99', 'es')).toContain('399,99');
    expect(formatMoney('0.05', 'es')).toContain('0,05');
  });
});
