import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';
import i18next from 'eslint-plugin-i18next';

export default tseslint.config({
  extends: [
    js.configs.recommended,
    ...tseslint.configs.recommended,
  ],
  files: ['src/**/*.{ts,tsx}'],
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
