import { lazy } from 'react';
import { Route, Routes } from 'react-router-dom';

import { RequireAuth } from '../features/auth/RequireAuth';
import { ACCOUNT_PATH } from '../features/auth/routes';
import { CATALOG_PATH, PRODUCT_ROUTE } from '../features/catalog/routes';
import { SIGN_IN_PATH } from '../shared/auth/signIn';
import { AppLayout } from './AppLayout';
import { AuthLayout } from './AuthLayout';

// One chunk per route: this is the SPA equivalent of the route-level code splitting the external
// React skills describe with `next/dynamic`.
const HomePage = lazy(() => import('../features/home/HomePage'));
const CatalogPage = lazy(() => import('../features/catalog/CatalogPage'));
const ProductDetailPage = lazy(() => import('../features/catalog/ProductDetailPage'));
const StoresPage = lazy(() => import('../features/stores/StoresPage'));
const AccountPage = lazy(() => import('../features/auth/AccountPage'));
const SignInPage = lazy(() => import('../features/auth/SignInPage'));

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
        {/* La única ruta con sesión por ahora. */}
        <Route
          path={ACCOUNT_PATH}
          element={
            <RequireAuth>
              <AccountPage />
            </RequireAuth>
          }
        />
      </Route>

      {/* La entrada tiene marco propio: ni menú, ni buscador, ni pie. Se llama `/signin` y no
          `/login` porque ese prefijo, como `/logout`, lo reserva el gateway y responde 404 al
          fallback del SPA — una ruta con ese nombre funciona bajo `npm run dev` y se rompe en
          cuanto el bundle lo sirve el gateway. */}
      <Route element={<AuthLayout />}>
        <Route path={SIGN_IN_PATH} element={<SignInPage />} />
      </Route>
    </Routes>
  );
}

export default App;
