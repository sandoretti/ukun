import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import esCommon from './es/common.json';
import enCommon from './en/common.json';
import esErrors from './es/errors.json';
import enErrors from './en/errors.json';
import esCatalog from './es/catalog.json';
import enCatalog from './en/catalog.json';
import esHome from './es/home.json';
import enHome from './en/home.json';

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      es: {
        common: esCommon,
        errors: esErrors,
        catalog: esCatalog,
        home: esHome,
      },
      en: {
        common: enCommon,
        errors: enErrors,
        catalog: enCatalog,
        home: enHome,
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
