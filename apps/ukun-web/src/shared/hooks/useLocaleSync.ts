import { useEffect } from 'react';

import { useLocale } from './useLocale';

/** Keeps `<html lang>` on the active language — an accessibility requirement, not cosmetics. */
export function useLocaleSync() {
  const locale = useLocale();

  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);
}
