import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import { DEFAULT_LANGUAGE, SUPPORTED_LANGUAGES, type SupportedLanguage } from './languages';
import esCommon from './es/common.json';
import enCommon from './en/common.json';
import esErrors from './es/errors.json';
import enErrors from './en/errors.json';
import esCatalog from './es/catalog.json';
import enCatalog from './en/catalog.json';
import esHome from './es/home.json';
import enHome from './en/home.json';
import esStores from './es/stores.json';
import enStores from './en/stores.json';
import esProfile from './es/profile.json';
import enProfile from './en/profile.json';
import esCart from './es/cart.json';
import enCart from './en/cart.json';
import esCheckout from './es/checkout.json';
import enCheckout from './en/checkout.json';
import esOrders from './es/orders.json';
import enOrders from './en/orders.json';
import esSignup from './es/signup.json';
import enSignup from './en/signup.json';
import esBackoffice from './es/backoffice.json';
import enBackoffice from './en/backoffice.json';

/**
 * The namespaces of `docs/07-frontend-ux-ui.md §i18n`, one JSON per namespace and language. The
 * document lists the ones foreseen for the whole application; a screen that is not a feature of that
 * list still gets its own namespace rather than crowding `common` — `home` was the first, `stores`
 * the second.
 */
export type Namespace =
  | 'common'
  | 'errors'
  | 'catalog'
  | 'home'
  | 'stores'
  | 'profile'
  | 'cart'
  | 'checkout'
  | 'orders'
  | 'signup'
  | 'backoffice';

/**
 * Every bundle the app ships with. `satisfies` and not a bare object literal: it is what turns
 * "a language was added to `SUPPORTED_LANGUAGES` and nobody wrote its JSONs" into a compile error
 * instead of a screen that silently falls back to Spanish.
 *
 * Exported because the translation parity test reads its namespaces from here — the list of files
 * lives in one place, not in the config and again in the test.
 */
export const resources = {
  es: {
    common: esCommon,
    errors: esErrors,
    catalog: esCatalog,
    home: esHome,
    stores: esStores,
    profile: esProfile,
    cart: esCart,
    checkout: esCheckout,
    orders: esOrders,
    signup: esSignup,
    backoffice: esBackoffice,
  },
  en: {
    common: enCommon,
    errors: enErrors,
    catalog: enCatalog,
    home: enHome,
    stores: enStores,
    profile: enProfile,
    cart: enCart,
    checkout: enCheckout,
    orders: enOrders,
    signup: enSignup,
    backoffice: enBackoffice,
  },
} satisfies Record<SupportedLanguage, Record<Namespace, Record<string, unknown>>>;

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: DEFAULT_LANGUAGE,
    supportedLngs: SUPPORTED_LANGUAGES,
    nonExplicitSupportedLngs: true,
    interpolation: {
      escapeValue: false, // not needed for react as it escapes by default
    },
  });

export default i18n;
