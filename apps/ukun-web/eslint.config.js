import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';
import i18next from 'eslint-plugin-i18next';
import jsxA11y from 'eslint-plugin-jsx-a11y';

export default tseslint.config({
  extends: [
    js.configs.recommended,
    ...tseslint.configs.recommended,
    // El gate estático de accesibilidad, que corre en cada `npm run lint`. Es la mitad barata de la
    // auditoría de v1.0 (issue #54): coge lo que se puede ver sin renderizar —un `<div onClick>`, un
    // botón de solo icono sin nombre, un `alt` que sobra— incluso en una pantalla que ningún test
    // monta. La otra mitad, la que necesita el árbol de accesibilidad ya construido, es axe:
    // `src/test/a11y.ts` en jsdom y `@axe-core/playwright` en los tres recorridos.
    //
    // El paquete declara un peer de `eslint ^3..^9` y este repo va por la 10, así que `package.json`
    // lo fija con un `overrides` acotado (`{ "eslint": "$eslint" }`) en vez de un `legacy-peer-deps`
    // global, que desactivaría la verificación de peers de todas las dependencias del frontend.
    jsxA11y.flatConfigs.recommended,
  ],
  files: ['src/**/*.{ts,tsx}', 'e2e/**/*.ts', 'playwright.config.ts'],
  ignores: ['dist', 'node_modules', 'src/types/api.d.ts'],
  languageOptions: {
    ecmaVersion: 2020,
    globals: globals.browser,
  },
  plugins: {
    'react-hooks': reactHooks,
    'react-refresh': reactRefresh,
    'i18next': i18next,
  },
  rules: {
    ...reactHooks.configs.recommended.rules,
    'react-refresh/only-export-components': [
      'warn',
      { allowConstantExport: true },
    ],
    // `<RequireRole role="ADMIN">` es un componente de este repo y su `role` es el rol de negocio
    // (CUSTOMER/EMPLOYEE/ADMIN), no un rol ARIA. Sin esto la regla lee la prop de cualquier
    // componente propio como si fuese marcado y marca `App.tsx`, `BackofficeLayout.tsx` y sus tests.
    // Acotarla al DOM real es lo que la regla ofrece para exactamente este caso.
    'jsx-a11y/aria-role': ['error', { ignoreNonDOM: true }],
    'i18next/no-literal-string': [
      'error',
      {
        markupOnly: true,
        onlyAttribute: ['title', 'alt', 'aria-label', 'placeholder'],
        ignoreAttribute: ['style', 'className', 'to', 'href', 'type', 'name', 'id', 'role', 'data-testid', 'variant', 'size']
      }
    ],
  },
});
