import { lazy } from 'react';
import { Navigate, Outlet, Route, Routes } from 'react-router-dom';

import { RequireAuth } from '../features/auth/RequireAuth';
import { RequireRole } from '../features/auth/RequireRole';
import { ACCOUNT_PATH, SIGN_UP_PATH } from '../features/auth/routes';
import {
  ADMIN_PRODUCT_ROUTE,
  ADMIN_PRODUCTS_PATH,
  NEW_PRODUCT_PATH,
} from '../features/backoffice/adminRoutes';
import { ADMIN_EMPLOYEES_PATH, NEW_EMPLOYEE_PATH } from '../features/backoffice/employees/routes';
import { NEW_STOCK_LINE_PATH } from '../features/backoffice/routes';
import { PRODUCT_ROUTE } from '../features/catalog/routes';
import { SIGN_IN_PATH } from '../shared/auth/signIn';
import {
  ADMIN_PATH,
  BACKOFFICE_PATH,
  BACKOFFICE_STOCK_PATH,
  CART_PATH,
  CATALOG_PATH,
  CHECKOUT_PATH,
  ORDERS_PATH,
  PROFILE_PATH,
} from '../shared/routes';
import { AppLayout } from './AppLayout';
import { AuthLayout } from './AuthLayout';
import { BackofficeLayout } from './BackofficeLayout';

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
const BackofficeHomePage = lazy(() => import('../features/backoffice/BackofficeHomePage'));
const AdminProductsPage = lazy(() => import('../features/backoffice/AdminProductsPage'));
const NewProductPage = lazy(() => import('../features/backoffice/NewProductPage'));
const AdminProductPage = lazy(() => import('../features/backoffice/AdminProductPage'));
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
      </Route>

      {/* El back-office, con marco propio (`BackofficeLayout`) y no el de la tienda: un empleado
          ajustando stock no necesita el menú del catálogo, ni el buscador de productos, ni el
          carrito, ni el pie. Lo que sí necesita —moverse entre sus dos o tres pantallas— lo lleva el
          rail, y volver a la tienda también.

          `RequireAuth` se compone **una vez** aquí y no ocho veces, una por ruta. El rol de empleado
          lo exige el propio layout alrededor de su `Outlet`, para que la pantalla de «esta página no
          es para tu cuenta» se pinte dentro del cascarón y no suelta en mitad de la nada: sin
          navegación, la única salida sería el botón de atrás del navegador. Ninguna de las dos es la
          barrera — el gateway rechaza lo que protege y `ukun-api` vuelve a comprobar el rol. */}
      <Route
        element={
          <RequireAuth>
            <BackofficeLayout />
          </RequireAuth>
        }
      >
        {/* El índice: qué puede hacer esta cuenta aquí, decidido desde `BACKOFFICE_SECTIONS`. Es el
            destino de la única entrada del menú de sesión, sea cual sea el rol. */}
        <Route path={BACKOFFICE_PATH} element={<BackofficeHomePage />} />

        {/* El stock (RF-09) cuelga de `/backoffice/*` y no de `/admin/*`: esa rama es la de lo que
            habla con `/api/admin/**` (ADR-0020), y el stock es de cualquier empleado. La ruta no
            lleva identificador de tienda — la resuelve el servidor desde `account_id`. */}
        <Route path={BACKOFFICE_STOCK_PATH} element={<StockPage />} />
        <Route path={NEW_STOCK_LINE_PATH} element={<NewStockLinePage />} />

        {/* La rama de administración (RF-10 y RF-11; RF-13 se le añade). Pide `ADMIN` y no
            `EMPLOYEE`: el catálogo es global y los empleados son de admin, no de cualquier empleado.
            La guarda se compone **una vez** para las cuatro rutas, en una ruta sin path que solo
            existe para agruparlas. El alta va **antes** que `:id`, que también casaría con el literal
            `new`. Ninguna lleva identificador de tienda: la resuelve el servidor. */}
        <Route
          element={
            <RequireRole role="ADMIN">
              <Outlet />
            </RequireRole>
          }
        >
          <Route path={ADMIN_PRODUCTS_PATH} element={<AdminProductsPage />} />
          <Route path={NEW_PRODUCT_PATH} element={<NewProductPage />} />
          <Route path={ADMIN_PRODUCT_ROUTE} element={<AdminProductPage />} />
          <Route path={ADMIN_EMPLOYEES_PATH} element={<EmployeesPage />} />
          <Route path={NEW_EMPLOYEE_PATH} element={<NewEmployeePage />} />
        </Route>
      </Route>

      {/* `/admin` a secas fue un índice de tarjetas y ya no lo es: el rail del back-office es esa
          navegación, y dos formas de decir lo mismo era una de más. La rama `/admin/*` de las
          pantallas se queda donde está — ADR-0020 la hace bicondicional con `/api/admin/**`. Fuera
          del layout a propósito: no pinta nada, así que no necesita marco ni sesión para redirigir. */}
      <Route path={ADMIN_PATH} element={<Navigate to={BACKOFFICE_PATH} replace />} />

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
