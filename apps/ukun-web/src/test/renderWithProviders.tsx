import type { ReactElement, ReactNode } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import i18n from '../i18n/config';

/**
 * The same providers `main.tsx` mounts, with a throwaway QueryClient per test (no cache bleeding
 * between tests) and retries off (an error test should not wait for three attempts).
 *
 * They travel as RTL's `wrapper` and not as a tree written around `ui`, because that is what makes
 * the returned `rerender` usable: given a wrapper, RTL re-applies it on every rerender, so the
 * component under test keeps its identity — and therefore its state, its refs and its effects —
 * across a change of props. Wrapping by hand instead replaces the root element, React sees a
 * different type there and remounts everything, which silently turns any test about *what a
 * component remembers* into a test of a freshly mounted one.
 */
export function renderWithProviders(ui: ReactElement, { route = '/' }: { route?: string } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  function Providers({ children }: { children: ReactNode }) {
    return (
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
        </QueryClientProvider>
      </I18nextProvider>
    );
  }

  return render(ui, { wrapper: Providers });
}
