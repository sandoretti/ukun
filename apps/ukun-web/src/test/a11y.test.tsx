import type { ReactElement } from 'react';
import { screen, within } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import type { RequestHandler } from 'msw';

import { expectNoA11yViolations } from './a11y';
import { renderWithProviders } from './renderWithProviders';
import { server } from './msw/server';
import { signedIn } from './msw/sessionHandlers';
import { cartState, lineOf, sofa } from './msw/cartHandlers';
import { checkoutFlow, lampLine, orderHistory, orderOf, sofaLine } from './msw/orderHandlers';
import { adminProduct, adminProductState } from './msw/adminProductHandlers';
import { productTypeState, seededTypes } from './msw/productTypeHandlers';
import { employeeState, staff } from './msw/employeeHandlers';
import { stockState } from './msw/stockHandlers';

import HomePage from '../features/home/HomePage';
import AboutPage from '../features/about/AboutPage';
import CatalogPage from '../features/catalog/CatalogPage';
import ProductDetailPage from '../features/catalog/ProductDetailPage';
import StoresPage from '../features/stores/StoresPage';
import SignUpPage from '../features/auth/SignUpPage';
import CartPage from '../features/cart/CartPage';
import CheckoutPage from '../features/orders/CheckoutPage';
import OrdersPage from '../features/orders/OrdersPage';
import OrderDetailPage from '../features/orders/OrderDetailPage';
import ProfilePage from '../features/profile/ProfilePage';
import StockPage from '../features/backoffice/StockPage';
import NewStockLinePage from '../features/backoffice/NewStockLinePage';
import BackofficeHomePage from '../features/backoffice/BackofficeHomePage';
import AdminProductsPage from '../features/backoffice/AdminProductsPage';
import AdminProductPage from '../features/backoffice/AdminProductPage';
import NewProductPage from '../features/backoffice/NewProductPage';
import ProductTypesPage from '../features/backoffice/productTypes/ProductTypesPage';
import EmployeesPage from '../features/backoffice/employees/EmployeesPage';
import NewEmployeePage from '../features/backoffice/employees/NewEmployeePage';
import { AppLayout } from '../app/AppLayout';
import { AuthLayout } from '../app/AuthLayout';
import { BackofficeLayout } from '../app/BackofficeLayout';

const EMPLOYEE = ['EMPLOYEE'] as const;
const ADMIN = ['EMPLOYEE', 'ADMIN'] as const;

/** Pantalla de relleno de los cascarones: lo que se audita es el marco, no lo que va dentro. */
const INSIDE = 'Contenido de prueba';

/** El contenido del icono del fixture de la guarda de abajo: decorativo, nunca texto de usuario. */
const DECORATIVE_GLYPH = 'delete';

const product = adminProduct({ id: 1, sku: 'SOFA-CUERO-01', name: 'Sofá de cuero' });
const order = orderOf([sofaLine, lampLine], { id: 1001, placedAt: '2026-08-01T18:30:00Z' });

type Audited = {
  /** El nombre que sale en el informe cuando falla. */
  name: string;
  /** El patrón de ruta, porque varias pantallas leen su id del path y no de una prop. */
  path: string;
  route: string;
  element: ReactElement;
  /** Los dobles que esa pantalla necesita para llegar a pintar algo. */
  handlers?: () => RequestHandler[];
};

/**
 * Las pantallas de los tres recorridos de `docs/03-analisis-y-patrones.md` §3 — cliente, empleado y
 * administrador— cada una en su estado de éxito, que es el que tiene contenido que auditar.
 */
const SCREENS: Audited[] = [
  // ── Recorrido de cliente ────────────────────────────────────────────────────────────────────
  { name: 'portada', path: '/', route: '/', element: <HomePage /> },
  { name: 'sobre Ukun', path: '/about', route: '/about', element: <AboutPage /> },
  { name: 'catálogo', path: '/products', route: '/products', element: <CatalogPage /> },
  {
    name: 'ficha de producto',
    path: '/products/:id',
    route: '/products/1',
    element: <ProductDetailPage />,
  },
  { name: 'tiendas', path: '/stores', route: '/stores', element: <StoresPage /> },
  { name: 'alta de cliente', path: '/signup', route: '/signup', element: <SignUpPage /> },
  {
    name: 'carrito',
    path: '/cart',
    route: '/cart',
    element: <CartPage />,
    handlers: () => [signedIn(), ...cartState([lineOf(sofa, 2)])],
  },
  {
    name: 'checkout',
    path: '/checkout',
    route: '/checkout',
    element: <CheckoutPage />,
    handlers: () => [signedIn(), ...checkoutFlow().handlers],
  },
  {
    name: 'historial de pedidos',
    path: '/orders',
    route: '/orders',
    element: <OrdersPage />,
    handlers: () => [signedIn(), ...orderHistory([order])],
  },
  {
    name: 'detalle de pedido',
    path: '/orders/:id',
    route: '/orders/1001',
    element: <OrderDetailPage />,
    handlers: () => [signedIn(), ...orderHistory([order])],
  },
  {
    name: 'perfil',
    path: '/profile',
    route: '/profile',
    element: <ProfilePage />,
    handlers: () => [signedIn()],
  },

  // ── Recorrido de empleado ───────────────────────────────────────────────────────────────────
  {
    name: 'stock de la tienda',
    path: '/backoffice/stock',
    route: '/backoffice/stock',
    element: <StockPage />,
    handlers: () => [
      signedIn([...EMPLOYEE]),
      ...stockState({ lines: [{ lineId: 10, productId: 1, quantity: 4, version: 0 }] }).handlers,
    ],
  },
  {
    name: 'alta de línea de stock',
    path: '/backoffice/stock/new',
    route: '/backoffice/stock/new',
    element: <NewStockLinePage />,
    handlers: () => [signedIn([...EMPLOYEE]), ...stockState().handlers],
  },

  // ── Recorrido de administrador ──────────────────────────────────────────────────────────────
  {
    name: 'portada del back-office',
    path: '/backoffice',
    route: '/backoffice',
    element: <BackofficeHomePage />,
    handlers: () => [signedIn([...ADMIN])],
  },
  {
    name: 'catálogo del back-office',
    path: '/admin/products',
    route: '/admin/products',
    element: <AdminProductsPage />,
    handlers: () => [signedIn([...ADMIN]), ...adminProductState({ products: [product] }).handlers],
  },
  {
    name: 'ficha de producto del back-office',
    path: '/admin/products/:id',
    route: '/admin/products/1',
    element: <AdminProductPage />,
    handlers: () => [signedIn([...ADMIN]), ...adminProductState({ products: [product] }).handlers],
  },
  {
    name: 'alta de producto',
    path: '/admin/products/new',
    route: '/admin/products/new',
    element: <NewProductPage />,
    handlers: () => [signedIn([...ADMIN]), ...adminProductState({ products: [product] }).handlers],
  },
  {
    name: 'tipos de producto',
    path: '/admin/product-types',
    route: '/admin/product-types',
    element: <ProductTypesPage />,
    handlers: () => [
      signedIn([...ADMIN]),
      ...productTypeState({ types: seededTypes }).handlers,
    ],
  },
  {
    name: 'empleados',
    path: '/admin/employees',
    route: '/admin/employees',
    element: <EmployeesPage />,
    handlers: () => [signedIn([...ADMIN]), ...employeeState({ employees: staff }).handlers],
  },
  {
    name: 'alta de empleado',
    path: '/admin/employees/new',
    route: '/admin/employees/new',
    element: <NewEmployeePage />,
    handlers: () => [signedIn([...ADMIN]), ...employeeState({ employees: staff }).handlers],
  },
];

/**
 * La mitad de la auditoría de accesibilidad de v1.0 (issue #54) que corre **sobre este código y sin
 * stack**: axe sobre el árbol que cada pantalla monta de verdad, con MSW en la frontera HTTP.
 *
 * Está en un solo fichero, y no repartida por las features, a propósito: lo que se audita aquí no es
 * el comportamiento de una pantalla —eso ya lo cubre su propio test— sino una propiedad que todas
 * comparten y que hay que poder comprobar de una pasada. Es el mismo argumento por el que la paridad
 * de traducciones vive en `i18n.test.ts` y no en cada JSON.
 *
 * Lo que jsdom no puede juzgar (contraste, foco visible, orden de tabulación) no se afirma aquí: eso
 * lo comprueba `@axe-core/playwright` dentro de los tres recorridos, con un navegador y el CSS
 * aplicado. Ver `src/test/a11y.ts` para la lista exacta de reglas apagadas y por qué.
 */
describe('accesibilidad de las pantallas de los tres recorridos', () => {
  it('detecta un incumplimiento conocido, para que el verde de abajo signifique algo', async () => {
    // La guarda contra sí misma, igual que la del registro de errores en `i18n.test.ts`: una regla
    // mal configurada, un axe que no llega a ejecutarse o un contenedor vacío darían diecinueve
    // verdes indistinguibles de los de verdad. Un botón cuyo único contenido es un icono decorativo
    // no tiene nombre accesible, y eso axe **tiene** que verlo.
    const { container } = renderWithProviders(
      <button type="button">
        {/* El nombre del glifo va en una constante y no escrito aquí: es el ligamento de un icono, no
            texto de pantalla, y la regla de i18n —que no puede saberlo— lo leería como una cadena de
            usuario sin traducir. */}
        <span aria-hidden="true">{DECORATIVE_GLYPH}</span>
      </button>,
    );

    await expect(expectNoA11yViolations(container)).rejects.toThrow(/button-name/u);
  });

  it.each(SCREENS)('$name no tiene incumplimientos que axe pueda detectar', async (audited) => {
    if (audited.handlers !== undefined) {
      server.use(...audited.handlers());
    }

    const { container } = renderWithProviders(
      <Routes>
        <Route path={audited.path} element={audited.element} />
      </Routes>,
      { route: audited.route },
    );

    // Nunca se audita el estado de carga: el `<h1>` es lo primero que toda pantalla pinta cuando ya
    // tiene algo que enseñar, así que esperarlo es esperar a que haya contenido del que hablar.
    await screen.findByRole('heading', { level: 1 });

    await expectNoA11yViolations(container);
  });
});


/**
 * Los landmarks de los tres cascarones.
 *
 * No se comprueban con axe y es a propósito: su regla `region` solo mira contenido **visible**, y en
 * jsdom nada tiene tamaño, así que pasa igual con los landmarks puestos que sin ellos — lo verifiqué
 * quitándolos. Un test que no distingue el arreglo del defecto es peor que ninguno.
 *
 * Preguntarle al árbol de accesibilidad por el landmark sí distingue, porque es estructura. Lo que
 * fija es lo que esta slice arregló: los tres cascarones pintaban su barra dentro de un `<nav>` —lo
 * que `Navbar` renderiza— pero ninguno tenía `<header>`, así que ninguna de las 21 pantallas tenía
 * región de cabecera; y el back-office dejaba además su barra superior fuera de todo landmark.
 */
describe('los tres cascarones tienen sus landmarks', () => {
  function renderShell(element: ReactElement, route: string) {
    return renderWithProviders(
      <Routes>
        <Route element={element}>
          <Route path={route} element={<h1>{INSIDE}</h1>} />
        </Route>
      </Routes>,
      { route },
    );
  }

  it.each([
    { name: 'tienda', route: '/', element: <AppLayout />, handlers: () => [signedIn()] },
    { name: 'identidad', route: '/signin', element: <AuthLayout />, handlers: () => [] },
    {
      name: 'back-office',
      route: '/backoffice/stock',
      element: <BackofficeLayout />,
      handlers: () => [signedIn([...ADMIN]), ...stockState().handlers],
    },
  ])('el cascarón de $name tiene cabecera y contenido', async ({ route, element, handlers }) => {
    server.use(...handlers());
    renderShell(element, route);

    await screen.findByRole('heading', { level: 1, name: INSIDE });

    expect(screen.getByRole('banner')).toBeDefined();
    expect(screen.getByRole('main')).toBeDefined();
  });

  it('el back-office mete su barra superior en la cabecera', async () => {
    server.use(signedIn([...ADMIN]), ...stockState().handlers);
    renderShell(<BackofficeLayout />, '/backoffice/stock');

    await screen.findByRole('heading', { level: 1, name: INSIDE });

    // El idioma, la tienda y la cuenta colgaban de un `<div>` suelto entre el rail y el contenido:
    // ni navegación ni contenido principal, o sea nada para quien se mueve por regiones.
    const banner = screen.getByRole('banner');
    expect(within(banner).getByRole('link', { name: /@/u })).toBeDefined();
  });
});
