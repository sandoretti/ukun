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
 * **Por qué el primer tabulador solo se puede pedir sobre un documento recién cargado.**
 *
 * Chromium no tabula «desde el principio»: tabula desde su *sequential focus navigation starting
 * point*, que es donde estuvo el último foco. Un `blur()` no lo reinicia — lo deja donde estaba—, así
 * que pedir el primer tabulador a mitad de recorrido devuelve la parada siguiente a lo último que se
 * pulsó, no la primera de la página. Esto costó un CI en rojo, y con una ironía: lo que lo destapa es
 * `useRouteFocus`, que al navegar deja el foco en el `<main>`, o sea **pasado** el salto al contenido.
 *
 * Y está bien que sea así: quien acaba de aterrizar en el contenido no necesita un enlace para
 * saltárselo. Lo que el salto al contenido promete es otra cosa —que al **cargar** una ruta sea la
 * primera parada—, y es exactamente lo que `useRouteFocus` respeta al no robar el foco en el primer
 * render. Por eso estas comprobaciones van detrás de un `goto` o un `reload` y no de un helper: la
 * carga limpia no es un detalle de montaje, es la condición que se está afirmando.
 */

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
 * Tabula `limit` veces y exige que **cada parada tenga nombre accesible**.
 *
 * Una parada anónima es un control al que se llega y del que no se sabe qué hace: para quien navega
 * con teclado y lector, un «enlace» a secas en mitad de la cabecera.
 *
 * El nombre lo calcula Playwright y no este fichero, y esa es la parte que importa. La primera
 * versión lo aproximaba con `aria-label ?? textContent` y daba por anónimo el enlace de la marca,
 * que se llama por el `alt` de su imagen — una aproximación que no falla del lado seguro: además de
 * inventarse fallos, habría aprobado un `aria-labelledby` roto. `toHaveAccessibleName` aplica el
 * algoritmo de verdad, con sus `alt`, sus `aria-labelledby` y su texto visually-hidden.
 *
 * Cuenta desde el principio de la página, así que quien lo llame tiene que venir de una carga limpia
 * — la razón está arriba, en el bloque sobre el punto de partida de la navegación secuencial.
 */
export async function expectNamedTabStops(page: Page, limit: number): Promise<void> {
  for (let stop = 1; stop <= limit; stop += 1) {
    await page.keyboard.press('Tab');

    await expect(page.locator(':focus'), `la parada ${stop} del tabulador no tiene nombre`)
      .not.toHaveAccessibleName('');
  }
}
