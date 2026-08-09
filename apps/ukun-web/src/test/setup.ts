import { afterAll, afterEach, beforeAll } from 'vitest';
import { cleanup } from '@testing-library/react';

import i18n from '../i18n/config';
import { server } from '../mocks/server';

beforeAll(async () => {
  // Any request the handlers do not cover is a bug in the test, not something to let through.
  server.listen({ onUnhandledRequest: 'error' });
  // The detector would pick whatever jsdom reports; assertions need one fixed language.
  await i18n.changeLanguage('es');
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
});

afterAll(() => {
  server.close();
});
