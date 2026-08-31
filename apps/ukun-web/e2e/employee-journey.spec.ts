import { expect, test, type Page } from '@playwright/test';

import { expectNoA11yViolations, expectSkipLinkWorks } from './fixtures/a11y';
import { EMPLOYEE, STORE, commitFilter, openSection, rail, signInAsStaff } from './fixtures/staff';

/** Un SKU con la forma que pide el contrato y que no es de nadie: el buscador tiene que decirlo. */
const UNKNOWN_SKU = 'E2E-NO-EXISTE';

/**
 * El SKU y el nombre de la primera fila que la tabla esté pintando.
 *
 * Se lee de la pantalla y no se escribe aquí: `docs/13-testing.md` §3 prohíbe afirmar sobre un SKU
 * concreto del catálogo porque este mismo recorrido lo mueve —da de alta líneas y las borra—, así
 * que el único dato de partida honesto es el que hay delante ahora.
 */
async function firstRow(page: Page): Promise<{ sku: string; name: string }> {
  const row = page.locator('tbody tr').first();
  await expect(row).toBeVisible();

  return {
    sku: (await row.locator('td').nth(0).innerText()).trim(),
    name: (await row.locator('td').nth(1).innerText()).trim(),
  };
}

/**
 * El recorrido del empleado **sin** administración: entra, ve el stock de su tienda, da de alta una
 * línea resolviendo un SKU, le cambia las unidades y la borra.
 *
 * **Es una de las dos mitades de la paridad con `ukun-empleados`** (`docs/04-roadmap.md`, SINC-5),
 * junto con `admin-journey.spec.ts`. Como en el de cliente, es un solo `test` y no ocho: cada paso
 * depende del anterior —no hay línea que editar sin haberla creado— y partirlo obligaría a rehacer
 * el alta en cada uno o a compartir estado entre tests, que es peor.
 *
 * Lo que este recorrido afirma y ningún test de pantalla puede afirmar es lo que solo existe con el
 * sistema entero delante: que la tienda sale del token y no de la URL (RF-09), que el borde cierra
 * `/admin/*` a esta cuenta, que el filtro vive en la URL y sobrevive a una recarga de verdad, y que
 * el `409` de una línea repetida es el del registro cerrado y no un mensaje inventado por la
 * pantalla.
 *
 * **Ninguna aserción sobre las cantidades del catálogo** (`docs/13-testing.md` §3): las únicas que
 * se afirman son las que este mismo recorrido escribe en una línea que acaba de crear. Y como al
 * final la borra, la base queda como estaba: dos pasadas seguidas sin `db-reset` dan lo mismo.
 */
test('una empleada gestiona el stock de su tienda y no puede entrar en la administración', async ({
  page,
}) => {
  await test.step('entra y abre el back-office, que le ofrece solo lo suyo', async () => {
    await signInAsStaff(page, EMPLOYEE);

    // El rail es una sola lista leída a través de `hasRole` (`sections.ts`): las tres secciones de
    // administración no están, y no por estar deshabilitadas sino por no dibujarse.
    await expect(rail(page).getByRole('link', { name: 'Stock', exact: true })).toBeVisible();
    await expect(rail(page).getByRole('link', { name: 'Tipos de producto' })).toHaveCount(0);
    await expect(rail(page).getByRole('link', { name: 'Productos', exact: true })).toHaveCount(0);
    await expect(rail(page).getByRole('link', { name: 'Empleados', exact: true })).toHaveCount(0);

    // La barra dice en qué tienda trabajas, que es el dato que el resto de pantallas dan por sabido.
    await expect(page.getByText(`Tu tienda: ${STORE}`)).toBeVisible();
  });

  await test.step('la rama de administración no es para su cuenta', async () => {
    // Aquí sí se escribe la ruta, y es justo lo que se está probando: no hay ningún enlace que
    // lleve, así que la única forma de llamar a esa puerta es a mano. Esconderla es UX; la barrera
    // son el gateway y `ukun-api` (`docs/08-identidad-y-seguridad.md` §4, ADR-0020).
    await page.goto('/admin/products');

    await expect(
      page.getByRole('heading', { level: 1, name: 'Esta página no es para tu cuenta' }),
    ).toBeVisible();
    // Dentro del cascarón y con el rail a la vista: sin navegación, la única salida sería el botón
    // de atrás del navegador.
    await expect(rail(page).getByRole('link', { name: 'Stock', exact: true })).toBeVisible();

    // Y `/admin` a secas ya no es un índice de nada: lleva al del back-office.
    await page.goto('/admin');
    await expect(page).toHaveURL(/\/backoffice$/u);
  });

  const stocked = await test.step('ve el stock de su tienda', async () => {
    await openSection(page, 'Stock');

    // La tienda está en el título y en ninguna parte de la URL: la resuelve el servidor desde
    // `account_id`, y este recorrido no intenta forzar otra —eso es un IT de backend—.
    await expect(page.getByRole('heading', { level: 1, name: `Stock de ${STORE}` })).toBeVisible();

    // El back-office tiene cascarón propio (rail, topbar, tabla), así que se audita aparte del de la
    // tienda: no comparten ni landmarks ni tema.
    await expectNoA11yViolations(page, 'stock de la tienda');

    // El rail son cuatro a seis paradas antes del contenido en **todas** las pantallas del
    // back-office; su salto al contenido es el que más ahorra de los tres cascarones.
    await expectSkipLinkWorks(page, 'el back-office');
    await expect(page).toHaveURL(/\/backoffice\/stock$/u);
    await expect(page.getByRole('navigation', { name: 'Paginación del stock' })).toBeVisible();

    return firstRow(page);
  });

  await test.step('filtra por un SKU y el filtro sobrevive a la recarga', async () => {
    await commitFilter(page, 'SKU', stocked.sku);

    await expect(page).toHaveURL(new RegExp(`sku=${stocked.sku}`, 'u'));
    await expect(page.getByText('1 producto', { exact: true })).toBeVisible();
    await expect(page.getByRole('group', { name: 'Filtros activos' })).toContainText(
      `SKU: ${stocked.sku}`,
    );

    // Con un solo filtro no se ofrece limpiarlos todos: sería un segundo botón haciendo exactamente
    // lo que hace el chip que ya está al lado.
    await expect(page.getByRole('button', { name: 'Limpiar todo' })).toHaveCount(0);

    // La recarga es la prueba de que lo que sostiene la respuesta es la URL y no un `useState`, y de
    // que la tabla filtrada es enlazable —que es como el alta apunta a la línea que acaba de crear—.
    await page.reload();
    await expect(page.getByText('1 producto', { exact: true })).toBeVisible();
    await expect(page.getByRole('group', { name: 'Filtros activos' })).toContainText(
      `SKU: ${stocked.sku}`,
    );

    await commitFilter(page, 'Nombre del producto', stocked.name);
    await expect(page.getByRole('group', { name: 'Filtros activos' })).toContainText(
      `Nombre: ${stocked.name}`,
    );

    // Y con dos, sí: cada chip se quita por su cuenta, y además se pueden quitar de golpe.
    await page.getByRole('button', { name: `Quitar el filtro SKU: ${stocked.sku}` }).click();
    await expect(page).not.toHaveURL(/sku=/u);
    await expect(page.getByRole('button', { name: 'Limpiar todo' })).toHaveCount(0);

    await commitFilter(page, 'SKU', stocked.sku);
    await page.getByRole('button', { name: 'Limpiar todo' }).click();

    await expect(page.getByRole('group', { name: 'Filtros activos' })).toHaveCount(0);
    await expect(page).toHaveURL(/\/backoffice\/stock$/u);
  });

  const candidate = await test.step('elige un producto que su tienda no gestiona', async () => {
    // Los tres estados que la pantalla distingue son tres cosas distintas, y «sin dar de alta» no es
    // «cero unidades»: no hay línea, así que lo único que cabe hacer con él es darlo de alta (RF-09).
    await page.getByLabel('Qué se lista').selectOption({ label: 'Sin dar de alta' });
    await expect(page.getByRole('cell', { name: 'Sin dar de alta' }).first()).toBeVisible();

    return firstRow(page);
  });

  await test.step('lo da de alta resolviendo su SKU', async () => {
    // Por la cabecera y no por el enlace de la fila: ese llega con `?sku=` y resuelve solo, saltando
    // el buscador, que es precisamente el paso que este tramo tiene que ejercitar (ADR-0015).
    await page.getByRole('link', { name: 'Dar de alta por SKU' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Dar de alta stock' })).toBeVisible();

    // Un SKU que no resuelve es un mensaje en el campo donde se tecleó, no una pantalla rota.
    await page.getByLabel('SKU del producto').fill(UNKNOWN_SKU);
    await page.getByRole('button', { name: 'Buscar' }).click();
    await expect(
      page.getByText('No hay ningún producto activo con ese SKU. Puede que no exista o que esté descatalogado.'),
    ).toBeVisible();

    await page.getByLabel('SKU del producto').fill(candidate.sku);
    await page.getByRole('button', { name: 'Buscar' }).click();

    // El producto queda resuelto antes de que haya nada que crear: el SKU no es un campo más del
    // formulario, es el paso uno.
    await expect(page.getByRole('group', { name: 'Nombre' })).toHaveText(candidate.name);

    // Un formulario del back-office ya resuelto, con su campo, su etiqueta y su mensaje: es la
    // pantalla donde más cosas hay que nombrar bien.
    await expectNoA11yViolations(page, 'alta de línea de stock');
    await page.getByLabel(`Unidades en ${STORE}`).fill('7');
    await page.getByRole('button', { name: 'Dar de alta' }).click();

    // La confirmación es aterrizar en el listado filtrado por ese SKU, con la línea nueva sola: no
    // hay toast, y no hace falta.
    await expect(page).toHaveURL(new RegExp(`sku=${candidate.sku}`, 'u'));
    await expect(page.getByRole('cell', { name: '7', exact: true })).toBeVisible();
  });

  await test.step('dar de alta la misma línea otra vez choca con la que ya existe', async () => {
    await page.getByRole('link', { name: 'Dar de alta por SKU' }).click();
    await page.getByLabel('SKU del producto').fill(candidate.sku);
    await page.getByRole('button', { name: 'Buscar' }).click();
    await page.getByLabel(`Unidades en ${STORE}`).fill('3');
    await page.getByRole('button', { name: 'Dar de alta' }).click();

    // El `409 REFERENTIAL_INTEGRITY` del registro cerrado, dicho como lo que es: no hay nada mal en
    // lo tecleado, se está en la pantalla equivocada. Por eso la salida es un enlace y no reintentar.
    await expect(page.getByText('Ese producto ya tiene línea en esta tienda')).toBeVisible();
    await page.getByRole('link', { name: 'Ir a la línea existente' }).click();

    // Y la unidad sigue siendo la que se creó: el intento repetido no ha escrito nada.
    await expect(page.getByRole('cell', { name: '7', exact: true })).toBeVisible();
  });

  await test.step('le cambia las unidades', async () => {
    // El nombre accesible de cada acción lleva dentro el producto: veinte filas con veinte controles
    // llamados igual son veinte que nadie puede distinguir por teclado.
    await page.getByRole('button', { name: `Cambiar las unidades de ${candidate.name}` }).click();

    const dialog = page.getByRole('dialog', { name: 'Cambiar unidades' });
    await dialog.getByLabel('Unidades').fill('4');
    await dialog.getByRole('button', { name: 'Guardar' }).click();

    await expect(dialog).toHaveCount(0);
    await expect(page.getByRole('cell', { name: '4', exact: true })).toBeVisible();
  });

  await test.step('la quita del stock y el producto vuelve a estar sin dar de alta', async () => {
    await page.getByRole('button', { name: `Quitar ${candidate.name} del stock` }).click();

    // El botón de confirmar repite el título del diálogo, así que se acota dentro de él.
    const dialog = page.getByRole('dialog', { name: 'Quitar del stock' });
    await dialog.getByRole('button', { name: 'Quitar del stock' }).click();
    await expect(dialog).toHaveCount(0);

    // Borrar la línea no es poner las unidades a cero: el producto deja de estar gestionado aquí y
    // vuelve al saco de los que no lo están. Es también lo que deja la base como estaba.
    await expect(
      page.getByRole('button', { name: `Cambiar las unidades de ${candidate.name}` }),
    ).toHaveCount(0);

    await page.getByLabel('Qué se lista').selectOption({ label: 'Sin dar de alta' });
    await expect(page.getByRole('link', { name: `Dar de alta ${candidate.name}` })).toBeVisible();
  });
});
