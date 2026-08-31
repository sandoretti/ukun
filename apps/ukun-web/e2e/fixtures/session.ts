import type { Page } from '@playwright/test';

/**
 * Lo que los tres recorridos hacen igual antes de tener nada que probar: fijar el idioma y meter unas
 * credenciales en la página de `ukun-auth`.
 *
 * Vive aparte porque lo usan **los tres**. Estaba en `fixtures/customer.ts`, que es donde nació con
 * el recorrido de v0.6, y dejarlo ahí obligaba a que el back-office importase del cliente: dos cosas
 * que no tienen nada que ver colgando de un fichero que dice llamarse otra cosa. Cuando dos lo
 * necesitan se sube, no se cruza.
 */

/**
 * Lo que hace falta para identificarse, y nada más.
 *
 * Es un tipo propio y no `Customer` porque las cuentas de trabajo del back-office **no se crean**:
 * son de plantilla, vienen del `db/changelog/demo` y de ellas solo se sabe el correo y la contraseña
 * (`fixtures/staff.ts`). El cliente, que sí se da de alta, aporta además su perfil.
 */
export type Credentials = {
  email: string;
  password: string;
};

/**
 * Fija el idioma antes de que cargue nada de la aplicación.
 *
 * El detector de `i18n/config.ts` mira `localStorage` primero, así que escribirlo aquí gana al
 * `Accept-Language` del navegador; y `useLocaleSync` lo vuelve a leer del perfil en cuanto hay
 * sesión, que es la razón de escribirlo con la clave que i18next usa y no confiar en el `locale` de
 * la configuración de Playwright a secas. Todas las aserciones van por nombre accesible en español.
 */
export async function useSpanish(page: Page): Promise<void> {
  await page.addInitScript(() => {
    window.localStorage.setItem('i18nextLng', 'es');
  });
}

/**
 * Teclea las credenciales en la página de `ukun-auth`, que vive en otro origen (el `iss` es
 * `http://localhost:9000`, `docs/06`): el SPA nunca ve la contraseña ni el token (ADR-0003).
 */
export async function signIn(page: Page, credentials: Credentials): Promise<void> {
  // Los `name` del formulario son los de Spring Security, no etiquetas traducidas.
  await page.locator('input[name="username"]').fill(credentials.email);
  await page.locator('input[name="password"]').fill(credentials.password);
  await page.locator('form').getByRole('button').click();
}
