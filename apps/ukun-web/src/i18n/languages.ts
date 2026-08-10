/**
 * The languages of ADR-0019, in the order they are offered, and the one everything falls back to.
 *
 * One list, so that adding a third language is one edit and not five: `i18n/config.ts` builds
 * `supportedLngs` from here, `LanguageSwitcher` renders one button per entry, and `useLocale` uses
 * the default when i18next has not resolved a language yet.
 */
export const SUPPORTED_LANGUAGES = ['es', 'en'] as const;

export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

export const DEFAULT_LANGUAGE: SupportedLanguage = 'es';
