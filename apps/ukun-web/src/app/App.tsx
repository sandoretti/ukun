import { lazy } from 'react';
import { Route, Routes } from 'react-router-dom';

import { RequireAuth } from '../features/auth/RequireAuth';
import { ACCOUNT_PATH } from '../features/auth/routes';
import { CATALOG_PATH, PRODUCT_ROUTE } from '../features/catalog/routes';
import { AppLayout } from './AppLayout';

// One chunk per route: this is the SPA equivalent of the route-level code splitting the external
// React skills describe with `next/dynamic`.
const HomePage = lazy(() => import('../features/home/HomePage'));
const CatalogPage = lazy(() => import('../features/catalog/CatalogPage'));
const ProductDetailPage = lazy(() => import('../features/catalog/ProductDetailPage'));
const StoresPage = lazy(() => import('../features/stores/StoresPage'));
const AccountPage = lazy(() => import('../features/auth/AccountPage'));

function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<HomePage />} />
        {/* Los dos caminos del catálogo salen de la propia feature, que es quien los construye
            desde cuatro sitios; la de tiendas no compone ninguno y se queda con su literal. */}
        <Route path={CATALOG_PATH} element={<CatalogPage />} />
        <Route path={PRODUCT_ROUTE} element={<ProductDetailPage />} />
        <Route path="/stores" element={<StoresPage />} />
        {/* La única ruta con sesión por ahora. No hay `/login` ni `/logout` aquí y no puede
            haberlos: son prefijos reservados del gateway, que responde 404 al fallback del SPA. */}
        <Route
          path={ACCOUNT_PATH}
          element={
            <RequireAuth>
              <AccountPage />
            </RequireAuth>
          }
        />
      </Route>
    </Routes>
  );
}

export default App;
