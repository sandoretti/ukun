import { useTranslation } from 'react-i18next';

import { DEFAULT_LANGUAGE } from '../../i18n/languages';

/**
 * The locale every `Intl` formatter in the app is fed with — `formatMoney`, `formatNumber` and the
 * date formats that come later.
 *
 * `resolvedLanguage` is what i18next actually settled on, which is not always what was asked for
 * (`es-ES` resolves to `es`), and it is `undefined` until the detector has run. Falling back to the
 * default in a single place is what keeps `?? 'es'` from being copied into every component that
 * formats a number.
 */
export function useLocale(): string {
  const { i18n } = useTranslation();

  return i18n.resolvedLanguage ?? DEFAULT_LANGUAGE;
}
