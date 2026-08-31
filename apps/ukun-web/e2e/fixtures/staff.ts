import { expect, type Page } from '@playwright/test';

import { signIn, useSpanish, type Credentials } from './session';

/**
 * Lo que comparten los dos recorridos de back-office: con qué cuenta se entra, cómo se entra, y los
 * valores que tienen que ser distintos en cada ejecución.
 *
 * **Ninguna de estas dos cuentas se crea aquí**, a diferencia del cliente. Son cuentas de plantilla
 * del `db/changelog/demo`, así que lo que hace falta de ellas es su correo y su contraseña; el alta
 * de una persona nueva sí ocurre dentro del recorrido de admin, y ahí sí lleva marca de tiempo.
 */

/**
 * La contraseña de las catorce cuentas demo (`docs/06-entorno-local-tilt.md`, el único sitio donde
 * está en claro; en `ukun_auth` solo existe su hash de BCrypt).
 */
export const DEMO_PASSWORD = 'ukun-demo-2026';

/** Empleada **sin** administración: ve el stock de su tienda y nada de `/admin/*`. */
export const EMPLOYEE: Credentials = {
  email: 'laura.martinez@ukun.com',
  password: DEMO_PASSWORD,
};

/** Gerente con administración, y también empleado: es el único que abre las cuatro secciones. */
export const ADMIN: Credentials = {
  email: 'salvador.oton@ukun.com',
  password: DEMO_PASSWORD,
};

/**
 * La tienda de las dos, según `demo/data/employees.csv`. Aparece en el título del stock y en el de
 * los empleados, y **nunca** en una URL: la resuelve el servidor desde `account_id` (RF-09, RF-11).
 */
export const STORE = 'Ukun Online';

/**
 * Los tres valores que el smoke escribe y que son únicos en la base: el SKU de un producto (RF-10,
 * fijado en el alta y para siempre), el nombre de un tipo (RF-13) y el correo de una cuenta (RF-11).
 *
 * **Marca de tiempo, como `newCustomer()`.** Un literal daría `DUPLICATE_VALUE` en la segunda
 * pasada, y el criterio de terminado de esta slice es justamente que dos pasadas seguidas pasen sin
 * `db-reset` en medio.
 */
export function newSku(): string {
  // El patrón del contrato es `^[A-Z0-9-]{3,32}$`: mayúsculas, cifras y guiones, nada más.
  return `E2E-${Date.now()}`;
}

export function newProductTypeName(): string {
  return `Tipo E2E ${Date.now()}`;
}

export function newEmployeeEmail(): string {
  // `example.com` está reservado por la RFC 2606 justamente para esto: no existe y nadie lo recibe.
  return `e2e-staff-${Date.now()}@example.com`;
}

/**
 * Entra con una cuenta de trabajo y deja el navegador en el índice del back-office.
 *
 * Se entra **por donde se entra**: el botón de la barra de la tienda, que no es un enlace de este
 * router sino una salida del SPA hacia `/oauth2/authorization/ukun` — el gateway es quien tiene la
 * sesión y el SPA nunca ve el token (ADR-0003). Después, la página de identificación es la de
 * `ukun-auth`, en otro origen, y de vuelta el correo es el nombre accesible del menú de cuenta.
 *
 * La única entrada del menú al back-office lleva al índice, sea cual sea el rol; qué se puede abrir
 * desde ahí lo decide el rail, y eso es lo que cada recorrido comprueba por su cuenta.
 */
export async function signInAsStaff(page: Page, credentials: Credentials): Promise<void> {
  // `useSpanish` no es un hook de React: es el `addInitScript` que fija el idioma antes de que cargue
  // nada, y se llama aquí —y no en cada recorrido— porque tiene que ir delante del primer `goto` y
  // olvidarlo se paga con aserciones en inglés. La regla solo mira el prefijo del nombre.
  // eslint-disable-next-line react-hooks/rules-of-hooks
  await useSpanish(page);
  await page.goto('/');

  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await signIn(page, credentials);

  await page.getByRole('button', { name: credentials.email }).click();
  await page.getByRole('link', { name: 'Back office' }).click();

  await expect(page.getByRole('heading', { level: 1, name: 'Back office' })).toBeVisible();
}

/** El rail del back-office, que es la navegación de todas sus pantallas. */
export function rail(page: Page) {
  return page.getByRole('navigation', { name: 'Navegación del back office' });
}

/**
 * Abre una sección haciendo clic en el rail y no escribiendo su ruta: que el menú lleve donde dice
 * es parte de lo que este smoke valida, y el rail es además lo único que decide qué ve cada rol
 * (`features/backoffice/sections.ts`).
 */
export async function openSection(page: Page, name: string): Promise<void> {
  await rail(page).getByRole('link', { name, exact: true }).click();
}

/**
 * Escribe un filtro y lo confirma.
 *
 * Las dos barras del back-office son la misma (`FilterToolbar`), y sus campos de texto se confirman
 * al salir del campo o con Enter en vez de filtrar por cada tecla: cruzar un campo es una petición,
 * no cuarenta (`docs/07-frontend-ux-ui.md`). Escribir las dos líneas sueltas cada vez invita a
 * olvidar la segunda, y un filtro sin confirmar no falla: simplemente no filtra.
 */
export async function commitFilter(page: Page, label: string, value: string): Promise<void> {
  const field = page.getByLabel(label, { exact: true });

  await field.fill(value);
  await field.press('Enter');
}
