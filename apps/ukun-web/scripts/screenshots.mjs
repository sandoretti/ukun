// Capturas del front, con sesión de verdad, para revisar una pantalla dentro de la sesión de trabajo
// —incluido desde otro dispositivo con `/rc`, donde no hay un Chrome delante que conducir a mano.
//
// **No es `playwright test`.** No hay nada que aserta aquí: lanza un navegador, entra con una cuenta
// demo, va a las rutas que se le piden y escribe PNG. Vive dentro de `apps/ukun-web` porque Node
// resuelve los módulos desde el directorio del fichero y `@playwright/test` está en este package.
//
//   node scripts/screenshots.mjs [directorio-de-salida]
//
// Contra el **gateway** (8080) y no contra el dev server de Vite: 8080 es el único origen donde
// conviven sesión, CSRF y SPA (ADR-0003), y en dev el gateway sirve el bundle desde el propio Vite,
// así que lo que se ve es esta rama. Las cuentas y la contraseña común están en
// `docs/06-entorno-local-tilt.md`; `E2E_BASE_URL` existe porque un worktree desplaza los puertos.
import { mkdir } from 'node:fs/promises';
import { join, resolve } from 'node:path';

import { chromium } from '@playwright/test';

const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:8080';
const PASSWORD = process.env.UKUN_DEMO_PASSWORD ?? 'ukun-demo-2026';
const OUT_DIR = resolve(process.argv[2] ?? 'screenshots');

const DESKTOP = { width: 1440, height: 900 };
const PHONE = { width: 390, height: 844 };

/**
 * Lo que se captura, por cuenta. `wide` y `narrow` piden que el rail se despliegue o se recoja antes
 * de la foto —la preferencia se recuerda, así que solo hay que pedirlo cuando cambia—; `viewport`
 * cambia el tamaño solo para esa toma.
 */
const RUNS = [
  {
    account: 'joel.sandoval@ukun.com', // EMPLOYEE + ADMIN, gerente de Ukun Alcalá de Henares
    shots: [
      // Las dos primeras con el rail mínimo, que es como se abre: iconos y tooltip.
      { name: '01-admin-home-rail-minimo', path: '/backoffice' },
      { name: '02-admin-stock-rail-minimo', path: '/backoffice/stock' },
      // A partir de aquí ancho. La preferencia se recuerda, así que basta con pedirlo una vez.
      { name: '03-admin-home-rail-ancho', path: '/backoffice', wide: true },
      { name: '04-admin-catalogo', path: '/admin/products' },
      // Con filtros puestos, que es donde se ven los chips que trae el issue #148.
      { name: '05-admin-catalogo-filtrado', path: '/admin/products?q=mesa&status=ALL' },
      { name: '06-admin-empleados', path: '/admin/employees' },
      { name: '07-admin-ficha-producto', path: '/admin/products/1' },
      // En el móvil el rail ancho flota sobre el contenido en vez de quitarle sitio.
      { name: '08-admin-stock-movil-ancho', path: '/backoffice/stock', viewport: PHONE },
      { name: '09-admin-stock-movil', path: '/backoffice/stock', viewport: PHONE, narrow: true },
    ],
  },
  {
    account: 'laura.martinez@ukun.com', // EMPLOYEE sin admin: el rail tiene que ser más corto
    shots: [
      { name: '10-empleado-home', path: '/backoffice', wide: true },
      { name: '11-empleado-stock', path: '/backoffice/stock' },
      // Y el catálogo, que es de admin, se le niega dentro del cascarón y con salida.
      { name: '12-empleado-catalogo-denegado', path: '/admin/products' },
    ],
  },
  {
    account: 'joel.sandoval@email.com', // CUSTOMER: la tienda, sin entrada al back-office
    shots: [{ name: '13-cliente-menu-tienda', path: '/', openSessionMenu: true }],
  },
];

/**
 * Entra con una cuenta. El formulario es el de `ukun-auth`, en otro origen: sus campos se llaman como
 * los de Spring Security y no como una etiqueta traducida, que es lo que cambia entre idiomas.
 */
async function signIn(page, email) {
  await page.goto(`${BASE_URL}/oauth2/authorization/ukun`, { waitUntil: 'domcontentloaded' });
  await page.locator('input[name="username"]').fill(email);
  await page.locator('input[name="password"]').fill(PASSWORD);
  await page.locator('form').getByRole('button').click();
  await page.waitForURL((url) => url.origin === new URL(BASE_URL).origin, { timeout: 30_000 });
}

async function capture(page, shot) {
  await page.setViewportSize(shot.viewport ?? DESKTOP);
  await page.goto(`${BASE_URL}${shot.path}`, { waitUntil: 'networkidle' });

  const toggle = shot.wide ? 'Ampliar el menú' : shot.narrow ? 'Reducir el menú' : undefined;
  if (toggle !== undefined) {
    const button = page.getByRole('button', { name: toggle });
    if (await button.isVisible().catch(() => false)) {
      await button.click();
    }
  }

  if (shot.openSessionMenu) {
    await page.locator('#session-menu').click();
  }

  const file = join(OUT_DIR, `${shot.name}.png`);
  await page.screenshot({ path: file });
  console.log(file);
}

async function main() {
  await mkdir(OUT_DIR, { recursive: true });

  const browser = await chromium.launch();

  for (const run of RUNS) {
    // Un contexto por cuenta: la cookie de sesión es del origen, así que reutilizar el contexto
    // dejaría a la segunda cuenta dentro de la sesión de la primera.
    const context = await browser.newContext({
      viewport: DESKTOP,
      locale: 'es-ES',
      // El detector de i18next mira `localStorage` antes que el navegador, y todas las capturas van
      // en español.
      storageState: { cookies: [], origins: [] },
    });
    await context.addInitScript(() => window.localStorage.setItem('i18nextLng', 'es'));

    const page = await context.newPage();
    await signIn(page, run.account);

    for (const shot of run.shots) {
      await capture(page, shot);
    }

    await context.close();
  }

  await browser.close();
}

await main();
