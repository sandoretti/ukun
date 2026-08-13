import { describe, expect, it } from 'vitest';

import { isStocked, isUnstocked } from './stockKeys';

/**
 * The contract describes both halves of RF-09 with one `StockLine` whose three interesting fields are
 * nullable. These two predicates are where that union is split, once, so no screen ever has to.
 */

const line = { productId: 1, sku: 'SOFA-CUERO-01', productName: 'Sofá de cuero' };

const stocked = { ...line, lineId: 10, quantity: 4, version: 2 };
const candidate = { ...line, lineId: null, quantity: null, version: null };

describe('isStocked', () => {
  it('accepts a line that has a row behind it', () => {
    expect(isStocked(stocked)).toBe(true);
  });

  it('accepts a quantity of zero, which is a line that exists and has none left', () => {
    expect(isStocked({ ...stocked, quantity: 0 })).toBe(true);
  });

  it.each([
    ['lineId', { ...stocked, lineId: null }],
    ['quantity', { ...stocked, quantity: null }],
    ['version', { ...stocked, version: null }],
  ])('rejects a line with no %s: there is nothing to address or to compare', (_field, broken) => {
    expect(isStocked(broken)).toBe(false);
  });
});

describe('isUnstocked', () => {
  it('accepts a catalog product with no row in the store', () => {
    expect(isUnstocked(candidate)).toBe(true);
  });

  it('rejects a line that does have a row', () => {
    expect(isUnstocked(stocked)).toBe(false);
  });

  it('is nobody both ways: the two halves of the listing never overlap', () => {
    for (const candidateLine of [stocked, candidate, { ...stocked, version: null }]) {
      expect(isStocked(candidateLine) && isUnstocked(candidateLine)).toBe(false);
    }
  });
});
