import { describe, it, expect } from 'vitest';

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
