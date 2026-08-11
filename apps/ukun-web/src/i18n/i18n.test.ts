import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it, expect } from 'vitest';
import { parse } from 'yaml';

import { resources, type Namespace } from './config';
import { DEFAULT_LANGUAGE, SUPPORTED_LANGUAGES } from './languages';

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

/**
 * Every namespace the app ships, read from the config itself: a namespace added there is checked
 * here without touching this file, which is the only way the test cannot fall behind the bundles.
 * Parity is measured against the fallback language — the one that answers when a key is missing.
 */
const namespaces = Object.keys(resources[DEFAULT_LANGUAGE]) as Namespace[];
const otherLanguages = SUPPORTED_LANGUAGES.filter((language) => language !== DEFAULT_LANGUAGE);

describe('i18n Translation Files Parity', () => {
  it.each(
    otherLanguages.flatMap((language) =>
      namespaces.map((namespace) => ({ language, namespace })),
    ),
  )('$namespace.json has identical keys in $language and the fallback language', ({ language, namespace }) => {
    expect(deepKeys(resources[language][namespace])).toEqual(
      deepKeys(resources[DEFAULT_LANGUAGE][namespace]),
    );
  });
});

/**
 * The closed registry of `docs/12-error-contract.md` §2, read from the contract itself rather than
 * copied here: the frontend resolves every error from its `code`, so a `code` the API can emit and
 * `errors.json` does not carry is a screen that paints `errors:SOMETHING` at a customer.
 *
 * Parsing the YAML is the whole point — a hand-written list in this file would fall behind the
 * registry exactly like the translations it is meant to police.
 */
const errorCodes: string[] = parse(
  // Same relative path the `generate:api` script uses, and from the same working directory.
  readFileSync(resolve(process.cwd(), '../../docs/api/openapi/ukun-api.yaml'), 'utf8'),
).components.schemas.ErrorCode.enum;

describe('Error code translation coverage', () => {
  it('reads a non-empty registry from the contract', () => {
    // Guards the test against itself: a renamed schema would silently turn the loop below into a
    // no-op that passes forever.
    expect(errorCodes.length).toBeGreaterThan(0);
  });

  it.each(SUPPORTED_LANGUAGES)('%s/errors.json covers every code of the registry', (language) => {
    expect(Object.keys(resources[language].errors).sort()).toEqual([...errorCodes].sort());
  });
});
