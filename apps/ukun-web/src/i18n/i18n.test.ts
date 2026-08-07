import { describe, it, expect } from 'vitest';
import esCommon from './es/common.json';
import enCommon from './en/common.json';
import esErrors from './es/errors.json';
import enErrors from './en/errors.json';

describe('i18n Translation Files Parity', () => {
  it('common.json should have identical keys in both languages', () => {
    const esKeys = Object.keys(esCommon).sort();
    const enKeys = Object.keys(enCommon).sort();
    expect(esKeys).toEqual(enKeys);
  });

  it('errors.json should have identical keys in both languages', () => {
    const esKeys = Object.keys(esErrors).sort();
    const enKeys = Object.keys(enErrors).sort();
    expect(esKeys).toEqual(enKeys);
  });
});
