import { defineConfig, devices } from '@playwright/test';

/**
 * El smoke del recorrido de cliente (`docs/03-analisis-y-patrones.md` §3): navegador de verdad
 * contra el stack que levanta `tilt up`, no contra dobles.
 *
 * **Contra el gateway y no contra el dev server de Vite.** El puerto 8080 es el único origen donde
 * conviven sesión, CSRF y SPA (ADR-0003): el 5173 lleva `/api` por proxy directamente a `ukun-api`,
 * saltándose lo que este recorrido tiene que ejercitar. `E2E_BASE_URL` existe porque un worktree
 * desplaza los puertos (+100, `docs/06-entorno-local-tilt.md`).
 *
 * Detalle completo de qué corre en cada fase: `docs/13-testing.md` §3.
 */

export const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:8080';

export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/globalSetup.ts',
  // El recorrido escribe en la base —crea una cuenta y un pedido, y consume stock—, así que no hay
  // paralelismo que ganar: dos ejecuciones a la vez competirían por las mismas unidades.
  fullyParallel: false,
  workers: 1,
  // Sin reintentos: este smoke es la declaración de paridad (SINC-4). Uno que pasa a la segunda no
  // dice que el recorrido funcione, dice que a veces funciona.
  retries: 0,
  // Un recorrido completo con login incluido no cabe en los 30 s por defecto.
  timeout: 120_000,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: BASE_URL,
    // El idioma se fija aquí y en `localStorage` (ver `fixtures/customer.ts`): el detector de
    // `i18n/config.ts` y el `locale` del perfil pueden cambiarlo a mitad del recorrido, y las
    // aserciones van por nombre accesible.
    locale: 'es-ES',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  // Un solo navegador. Lo que este recorrido comprueba es que el sistema entero responde, no que
  // cada motor pinte igual; la compatibilidad de render no se paga con tres veces el tiempo de CI.
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  // Sin `webServer`: el stack lo levanta Tilt y este proceso no lo arranca ni lo para. `globalSetup`
  // comprueba que está y falla diciéndolo.
});
