import { lazy } from 'react';
import { Route, Routes } from 'react-router-dom';

import { RequireAuth } from '../features/auth/RequireAuth';
import { RequireRole } from '../features/auth/RequireRole';
import { ACCOUNT_PATH, SIGN_UP_PATH } from '../features/auth/routes';
import { NEW_EMPLOYEE_PATH } from '../features/backoffice/employees/routes';
import { NEW_STOCK_LINE_PATH } from '../features/backoffice/routes';
import { PRODUCT_ROUTE } from '../features/catalog/routes';
import { SIGN_IN_PATH } from '../shared/auth/signIn';
import {
  ADMIN_EMPLOYEES_PATH,
  BACKOFFICE_STOCK_PATH,
  CART_PATH,
  CATALOG_PATH,
  CHECKOUT_PATH,
  ORDERS_PATH,
  PROFILE_PATH,
} from '../shared/routes';
import { AppLayout } from './AppLayout';
import { AuthLayout } from './AuthLayout';

// One chunk per route: this is the SPA equivalent of the route-level code splitting the external
// React skills describe with `next/dynamic`.
const HomePage = lazy(() => import('../features/home/HomePage'));
const CatalogPage = lazy(() => import('../features/catalog/CatalogPage'));
const ProductDetailPage = lazy(() => import('../features/catalog/ProductDetailPage'));
const StoresPage = lazy(() => import('../features/stores/StoresPage'));
const AccountPage = lazy(() => import('../features/auth/AccountPage'));
const ProfilePage = lazy(() => import('../features/profile/ProfilePage'));
const CartPage = lazy(() => import('../features/cart/CartPage'));
const CheckoutPage = lazy(() => import('../features/orders/CheckoutPage'));
const OrdersPage = lazy(() => import('../features/orders/OrdersPage'));
const OrderDetailPage = lazy(() => import('../features/orders/OrderDetailPage'));
const SignInPage = lazy(() => import('../features/auth/SignInPage'));
const SignUpPage = lazy(() => import('../features/auth/SignUpPage'));
const StockPage = lazy(() => import('../features/backoffice/StockPage'));
const NewStockLinePage = lazy(() => import('../features/backoffice/NewStockLinePage'));
const EmployeesPage = lazy(() => import('../features/backoffice/employees/EmployeesPage'));
const NewEmployeePage = lazy(() => import('../features/backoffice/employees/NewEmployeePage'));

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
        {/* Las rutas con sesión. La cuenta dice quién eres y cómo salir; el perfil son los datos de
            cliente de RF-05, que son otro recurso (`/api/me/profile`) y otra pantalla. */}
        <Route
          path={ACCOUNT_PATH}
          element={
            <RequireAuth>
              <AccountPage />
            </RequireAuth>
          }
        />
        <Route
          path={PROFILE_PATH}
          element={
            <RequireAuth>
              <ProfilePage />
            </RequireAuth>
          }
        />
        {/* El carrito exige sesión en las cuatro operaciones de su contrato, y lo resuelve el
            servidor desde `account_id`: la ruta no lleva ningún identificador de cliente. Exige
            además el rol: tener sesión no es ser cliente, y un empleado no tiene carrito
            (`docs/02-requisitos.md` §3, ADR-0006). Sin las dos guardas la pantalla se pintaba a
            medias y se llenaba de 403, que es justo lo que `RequireRole` existe para evitar. */}
        <Route
          path={CART_PATH}
          element={
            <RequireAuth>
              <RequireRole role="CUSTOMER">
                <CartPage />
              </RequireRole>
            </RequireAuth>
          }
        />
        {/* El checkout y los pedidos, con la misma condición y por la misma razón: el servidor
            resuelve de quién es el carrito y de quién es el pedido desde `account_id`, así que
            ninguna de estas rutas lleva identificador de cliente. El de `/orders/:id` identifica el
            pedido y no autoriza a verlo. */}
        <Route
          path={CHECKOUT_PATH}
          element={
            <RequireAuth>
              <CheckoutPage />
            </RequireAuth>
          }
        />
        <Route
          path={ORDERS_PATH}
          element={
            <RequireAuth>
              <OrdersPage />
            </RequireAuth>
          }
        />
        <Route
          path={`${ORDERS_PATH}/:id`}
          element={
            <RequireAuth>
              <OrderDetailPage />
            </RequireAuth>
          }
        />
        {/* El back-office del stock (RF-09). Cuelga de `/backoffice/*` y no de `/admin/*`: esa rama
            es la de lo que habla con `/api/admin/**` (ADR-0020), y el stock es de cualquier empleado.
            Las dos guardas se componen porque deniegan cosas distintas: sin sesión se va a
            identificarse, y con sesión sin el rol se explica aquí mismo, porque volver a entrar no
            cambia los roles. Ninguna de las dos es la barrera: el servidor comprueba el rol otra vez.
            La ruta tampoco lleva identificador de tienda — la resuelve el servidor desde
            `account_id`. */}
        <Route
          path={BACKOFFICE_STOCK_PATH}
          element={
            <RequireAuth>
              <RequireRole role="EMPLOYEE">
                <StockPage />
              </RequireRole>
            </RequireAuth>
          }
        />
        <Route
          path={NEW_STOCK_LINE_PATH}
          element={
            <RequireAuth>
              <RequireRole role="EMPLOYEE">
                <NewStockLinePage />
              </RequireRole>
            </RequireAuth>
          }
        />
        {/* La gestión de empleados (RF-11). Cuelga de `/admin/*` y no de `/backoffice/*` porque es
            lo único de estas dos ramas que habla con `/api/admin/**` (ADR-0020) y porque es de
            admin, no de cualquier empleado. Las guardas se componen igual que arriba y por lo mismo:
            sin sesión se va a identificarse, y con sesión sin el rol se explica aquí, porque volver
            a entrar no cambia los roles. Ninguna de las dos es la barrera — el gateway deniega
            `/api/admin/**` sin `ROLE_ADMIN` y `ukun-api` lo comprueba otra vez (docs/08 §4). Y
            ninguna de las dos rutas lleva identificador de tienda: la resuelve el servidor desde
            `account_id`. */}
        <Route
          path={ADMIN_EMPLOYEES_PATH}
          element={
            <RequireAuth>
              <RequireRole role="ADMIN">
                <EmployeesPage />
              </RequireRole>
            </RequireAuth>
          }
        />
        <Route
          path={NEW_EMPLOYEE_PATH}
          element={
            <RequireAuth>
              <RequireRole role="ADMIN">
                <NewEmployeePage />
              </RequireRole>
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
        {/* El alta comparte el marco desnudo de la entrada, y a diferencia de ella sí es una
            pantalla de este router: la cuenta la crea `ukun-api`, no el servidor de autorización. */}
        <Route path={SIGN_UP_PATH} element={<SignUpPage />} />
      </Route>
    </Routes>
  );
}

export default App;
