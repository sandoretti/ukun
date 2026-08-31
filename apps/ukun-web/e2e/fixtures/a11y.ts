import AxeBuilder from '@axe-core/playwright';
import { expect, type Locator, type Page } from '@playwright/test';

/**
 * La otra mitad de la auditoría de accesibilidad de v1.0 (issue #54): axe con un navegador de verdad
 * y el CSS aplicado.
 *
 * Lo que aporta sobre la pasada de Vitest (`src/test/a11y.test.tsx`) es exactamente lo que jsdom no
 * puede responder, porque no maqueta: **contraste de color**, **anillo de foco** y **orden de
 * tabulación**. Y no es teoría — los dos defectos más caros de esta slice solo se ven aquí: el token
 * decorativo usado como color de texto, y el `outline: 0` con que Bootstrap tapaba el anillo del
 * tema. Ninguno de los dos aparece en un árbol sin estilos.
 *
 * Va dentro de los tres recorridos que ya existen y no en un cuarto: `docs/03-analisis-y-patrones.md`
 * §3 fija tres, y lo que crece son sus `test.step`. Aprovecha además que el recorrido ya ha llegado
 * hasta ahí con sesión, que es la única forma de auditar las pantallas que la piden.
 */
export async function expectNoA11yViolations(page: Page, screen: string): Promise<void> {
  const { violations } = await new AxeBuilder({ page }).analyze();

  const report = violations.flatMap((violation) =>
    violation.nodes.map((node) => `[${violation.id}] ${violation.help} — ${node.target.join(' ')}`),
  );

  expect(report, `axe en «${screen}»`).toEqual([]);
}

/**
 * Que el elemento que tiene el foco lo esté enseñando.
 *
 * Se comprueba el contorno calculado y no una captura: el defecto que esto vigila no era la ausencia
 * de una regla —el tema define `:focus-visible { outline: 2px solid }` desde v0.1— sino que las
 * reglas de Bootstrap la ganaban por especificidad y ponían `outline: 0` en botones, campos, selects,
 * casillas y la X de los diálogos. Un anillo que existe en la hoja y no llega a pintarse es
 * indistinguible de uno que no existe, y solo el navegador sabe cuál de los dos hay.
 */
export async function expectVisibleFocusRing(page: Page, what: string): Promise<void> {
  const focus = await page.evaluate(() => {
    const element = document.activeElement;
    if (element === null || element === document.body) {
      return null;
    }

    const style = getComputedStyle(element);
    return {
      tag: element.tagName.toLowerCase(),
      // `:focus-visible` es la condición que el tema pide: con el ratón no se pinta anillo.
      isFocusVisible: element.matches(':focus-visible'),
      outlineStyle: style.outlineStyle,
      outlineWidth: Number.parseFloat(style.outlineWidth),
    };
  });

  expect(focus, `no hay nada enfocado en «${what}»`).not.toBeNull();
  expect(focus?.isFocusVisible, `«${what}»: el foco de teclado no activa :focus-visible`).toBe(true);
  expect(focus?.outlineStyle, `«${what}»: el elemento enfocado (${focus?.tag}) no pinta contorno`).not.toBe(
    'none',
  );
  expect(
    focus?.outlineWidth ?? 0,
    `«${what}»: el contorno del foco es demasiado fino para verse`,
  ).toBeGreaterThanOrEqual(2);
}

/**
 * Deja el foco al principio del documento y da el primer tabulador.
 *
 * Hace falta porque un recorrido llega a este punto con el foco en mitad de la pantalla —acaba de
 * pulsar algo— y desde ahí el tabulador no vuelve al principio. Y hace falta que sea `blur` y no un
 * clic en el fondo: un clic pone al navegador en modalidad ratón, y entonces `:focus-visible` deja de
 * activarse, que es justo lo que se quiere comprobar.
 */
export async function tabFromStart(page: Page): Promise<void> {
  await page.evaluate(() => {
    const active = document.activeElement;
    if (active instanceof HTMLElement) {
      active.blur();
    }
  });

  await page.keyboard.press('Tab');
}

/**
 * Lleva el foco a un control **como lo haría el teclado**, y no con `focus()` a secas.
 *
 * La diferencia no es cosmética. Chromium decide `:focus-visible` por la última modalidad de entrada:
 * si lo último fue un clic, un `focus()` programático **no** activa el pseudo-selector y el anillo no
 * se pinta — correctamente, porque esa es toda la razón de usar `:focus-visible` en vez de `:focus`.
 * Comprobar el anillo tras un `focus()` mide entonces la modalidad, no el estilo.
 *
 * Salir y volver con el tabulador es lo que devuelve la modalidad de teclado dejando el foco donde
 * estaba, sin depender de cuántas paradas haya hasta el control.
 */
export async function focusByKeyboard(page: Page, locator: Locator): Promise<void> {
  await locator.focus();
  await page.keyboard.press('Shift+Tab');
  await page.keyboard.press('Tab');
}

/**
 * Recorre la pantalla con el tabulador y devuelve el nombre accesible de cada parada.
 *
 * Sirve para afirmar dos cosas que solo existen en un navegador: que **se llega** a un control sin
 * ratón, y en qué orden. Se para en `limit` porque lo que se comprueba es la cabeza del recorrido
 * —el salto al contenido y lo que viene tras él—, no el inventario entero de la página.
 */
export async function tabThrough(page: Page, limit: number): Promise<string[]> {
  const stops: string[] = [];

  for (let step = 0; step < limit; step += 1) {
    await page.keyboard.press('Tab');
    stops.push(
      await page.evaluate(() => {
        const element = document.activeElement;
        if (element === null || element === document.body) {
          return '';
        }
        return (element.getAttribute('aria-label') ?? element.textContent ?? '').trim();
      }),
    );
  }

  return stops;
}
