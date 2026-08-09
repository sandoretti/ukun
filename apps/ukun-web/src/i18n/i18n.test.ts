import { describe, it, expect } from 'vitest';
import esCommon from './es/common.json';
import enCommon from './en/common.json';
import esErrors from './es/errors.json';
import enErrors from './en/errors.json';
import esCatalog from './es/catalog.json';
import enCatalog from './en/catalog.json';
import esHome from './es/home.json';
import enHome from './en/home.json';

/** Namespaces are nested, so parity is checked on the full path, not just the first level. */
function deepKeys(value: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(value)
    .flatMap(([key, entry]) =>
      typeof entry === 'object' && entry !== null
        ? deepKeys(entry as Record<string, unknown>, `${prefix}${key}.`)
        : [`${prefix}${key}`],
    )
    .sort();
}

const namespaces: [string, Record<string, unknown>, Record<string, unknown>][] = [
  ['common', esCommon, enCommon],
  ['errors', esErrors, enErrors],
  ['catalog', esCatalog, enCatalog],
  ['home', esHome, enHome],
];

describe('i18n Translation Files Parity', () => {
  it.each(namespaces)('%s.json has identical keys in both languages', (_name, es, en) => {
    expect(deepKeys(es)).toEqual(deepKeys(en));
  });
});
