import axe, { type AxeResults, type Result } from 'axe-core';

/**
 * Las reglas que no se le pueden preguntar a jsdom, y por qué.
 *
 * jsdom no maqueta: no hay cajas, ni colores compuestos, ni nada apilado sobre nada. Preguntarle por
 * ellas no da un veredicto, da un «no lo sé» que axe devuelve como `incomplete` — y una auditoría
 * que trata un «no lo sé» como un aprobado es peor que no tenerla. Se apagan aquí, se dicen en voz
 * alta, y se comprueban donde sí hay respuesta: `@axe-core/playwright` sobre los tres recorridos, en
 * un navegador de verdad con el CSS aplicado.
 */
const NEEDS_A_REAL_BROWSER = [
  // Contraste: hace falta el color calculado de dos cajas superpuestas.
  'color-contrast',
  // «Todo el contenido está dentro de un landmark». Aquí se monta una pantalla suelta, sin el
  // cascarón que aporta `<header>`, `<nav>` y `<main>`, así que la pregunta no aplica — y aunque se
  // montara: la regla solo mira contenido *visible*, y en jsdom nada mide nada, de modo que pasa
  // igual con los landmarks puestos que sin ellos. Se comprueba en Playwright, que es donde encontró
  // los dos que esta slice arregló, y con aserciones de landmark en los tests de los cascarones.
  'region',
];

/**
 * Falla si axe encuentra algo en lo que hay montado, con el informe legible en el mensaje.
 *
 * Es la mitad de la auditoría de accesibilidad que corre sin stack y sobre este código, en cada
 * `npm test` (issue #54): coge lo que solo se ve con el árbol de accesibilidad ya construido —un
 * control sin nombre, un `aria-labelledby` que apunta a nada, una tabla mal encabezada, un salto de
 * nivel en los encabezados—, que es justo lo que el lint estático no alcanza a ver.
 *
 * Nota sobre `incomplete`: no se afirma nada sobre ello. Son los casos en los que axe necesita ojos
 * o un navegador para decidir, y convertirlos en fallo llenaría la suite de ruido que nadie puede
 * resolver desde jsdom.
 */
export async function expectNoA11yViolations(container: HTMLElement): Promise<void> {
  const results: AxeResults = await axe.run(container, {
    rules: Object.fromEntries(NEEDS_A_REAL_BROWSER.map((rule) => [rule, { enabled: false }])),
  });

  const report = describeViolations(results.violations);
  if (report.length > 0) {
    // Un `expect(...).toEqual([])` sobre esto también fallaría, pero Vitest recorta el diff y lo que
    // recorta es precisamente el informe: el mensaje que se lee en CI acaba siendo «expected
    // [ Array(3) ] to deeply equal []», que no dice ni qué regla ni dónde. Lanzarlo a mano es lo
    // único que garantiza que el fallo se pueda arreglar sin volver a ejecutarlo en local.
    throw new Error(
      `axe encontró ${report.length} incumplimiento(s) de accesibilidad:\n\n${report.join('\n\n')}`,
    );
  }
}

/**
 * El informe que se ve cuando falla. `expect` sobre los objetos crudos de axe imprime cientos de
 * líneas de nodo serializado y esconde lo único que hace falta para arreglarlo: qué regla, en qué
 * elemento y qué hacer.
 */
function describeViolations(violations: Result[]): string[] {
  return violations.flatMap((violation) =>
    violation.nodes.map(
      (node) => `[${violation.id}] ${violation.help}\n  en: ${node.target.join(' ')}\n  ${node.failureSummary ?? ''}`,
    ),
  );
}
