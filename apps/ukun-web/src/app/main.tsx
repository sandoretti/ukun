import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '../i18n/config';
import App from './App';
import '../shared/theme/bootstrap-overrides.scss';
// La fuente de iconos va autohospedada por npm, nunca por CDN (docs/07-frontend-ux-ui.md).
import 'material-symbols/outlined.css';

const queryClient = new QueryClient();

/**
 * Which backend the app talks to is a variable, not a code path: with `VITE_ENABLE_MSW=true` the
 * service worker answers from `mocks/`, and with it off the same axios instance reaches the real
 * API. Nothing above `shared/api/` knows the difference.
 */
async function enableMocking() {
  if (import.meta.env.VITE_ENABLE_MSW !== 'true') {
    return;
  }
  const { worker } = await import('../mocks/browser');
  await worker.start({ onUnhandledRequest: 'bypass' });
}

void enableMocking().then(() => {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </React.StrictMode>,
  );
});
