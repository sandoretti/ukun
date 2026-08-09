import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import esCommon from './es/common.json';
import enCommon from './en/common.json';
import esErrors from './es/errors.json';
import enErrors from './en/errors.json';
import esCatalog from './es/catalog.json';
import enCatalog from './en/catalog.json';

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      es: {
        common: esCommon,
        errors: esErrors,
        catalog: esCatalog,
      },
      en: {
        common: enCommon,
        errors: enErrors,
        catalog: enCatalog,
      },
    },
    fallbackLng: 'es',
    supportedLngs: ['es', 'en'],
    nonExplicitSupportedLngs: true,
    interpolation: {
      escapeValue: false, // not needed for react as it escapes by default
    },
  });

export default i18n;
