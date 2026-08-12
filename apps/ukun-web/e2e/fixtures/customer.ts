import type { Page } from '@playwright/test';

/**
 * El cliente que este recorrido crea, y las dos operaciones que lo meten en el sistema.
 *
 * **Cada ejecución da de alta a una persona nueva.** El alta escribe en la base y el smoke se lanza
 * muchas veces: reutilizar un correo daría un `409 EMAIL_ALREADY_EXISTS` en la segunda pasada, y
 * borrar la cuenta al terminar exigiría un endpoint que no existe (ni debe: la baja no es RF de este
 * release). La base se limpia con el botón `db-reset` de Tilt, que es manual a propósito.
 */

export type Customer = {
  email: string;
  password: string;
  name: string;
  lastName: string;
};

/** Número que pasa Luhn con IIN de Visa: la marca se lee de él, nadie la elige. */
export const TEST_CARD_NUMBER = '4242424242424242';

export function newCustomer(): Customer {
  return {
    // `example.com` está reservado por la RFC 2606 justamente para esto: no existe y nadie lo recibe.
    email: `e2e-${Date.now()}@example.com`,
    // Doce caracteres es el mínimo del contrato, y una frase es lo que `docs/08` §8 recomienda.
    password: 'recorrido de cliente 2026',
    name: 'Elena',
    lastName: 'Prueba',
  };
}

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
 * Rellena y envía el alta (RF-06). Al terminar, el navegador ya está saliendo hacia `ukun-auth`: el
 * contrato dice que el paso siguiente del 201 es la identificación.
 */
export async function signUp(page: Page, customer: Customer): Promise<void> {
  await page.goto('/signup');

  await page.getByLabel('Correo').fill(customer.email);
  await page.getByLabel('Contraseña').fill(customer.password);
  await page.getByLabel('Nombre').fill(customer.name);
  await page.getByLabel('Apellidos').fill(customer.lastName);
  await page.getByLabel('Calle y número').fill('Calle Mayor, 1');
  await page.getByLabel('Ciudad').fill('Alcalá de Henares');
  await page.getByLabel('Provincia').fill('Madrid');
  await page.getByLabel('Código postal').fill('28801');
  // Ni CVV ni marca: el número no viaja y la marca se deduce de sus primeras cifras.
  await page.getByLabel('Número de la tarjeta').fill(TEST_CARD_NUMBER);

  await page.getByRole('button', { name: 'Crear cuenta' }).click();
}

/**
 * Teclea las credenciales en la página de `ukun-auth`, que vive en otro origen (el `iss` es
 * `http://localhost:9000`, `docs/06`): el SPA nunca ve la contraseña ni el token (ADR-0003).
 */
export async function signIn(page: Page, customer: Customer): Promise<void> {
  // Los `name` del formulario son los de Spring Security, no etiquetas traducidas.
  await page.locator('input[name="username"]').fill(customer.email);
  await page.locator('input[name="password"]').fill(customer.password);
  await page.locator('form').getByRole('button').click();
}
