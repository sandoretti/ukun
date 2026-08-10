import { lazy } from 'react';
import { Route, Routes } from 'react-router-dom';

import { AppLayout } from './AppLayout';

// One chunk per route: this is the SPA equivalent of the route-level code splitting the external
// React skills describe with `next/dynamic`.
const HomePage = lazy(() => import('../features/home/HomePage'));
const CatalogPage = lazy(() => import('../features/catalog/CatalogPage'));
const StoresPage = lazy(() => import('../features/stores/StoresPage'));

function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<HomePage />} />
        <Route path="/products" element={<CatalogPage />} />
        <Route path="/stores" element={<StoresPage />} />
      </Route>
    </Routes>
  );
}

export default App;
