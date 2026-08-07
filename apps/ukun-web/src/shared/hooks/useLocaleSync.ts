import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';

export function useLocaleSync() {
  const { i18n } = useTranslation();

  useEffect(() => {
    document.documentElement.lang = i18n.resolvedLanguage || 'es';
  }, [i18n.resolvedLanguage]);
}
