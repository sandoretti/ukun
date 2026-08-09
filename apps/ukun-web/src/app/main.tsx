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
 * There is one backend: the real one. `VITE_API_URL` empty means same origin — the gateway in
 * production, the Vite proxy in development (`vite.config.ts`). Mocks live only in the test run
 * (`src/test/msw/`) and never ship in a bundle.
 */
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
);
