import { fileURLToPath } from 'node:url';

import { expect, test, type Page } from '@playwright/test';

import {
  ADMIN,
  DEMO_PASSWORD,
  STORE,
  commitFilter,
  newEmployeeEmail,
  newProductTypeName,
  newSku,
  openSection,
  rail,
  signInAsStaff,
} from './fixtures/staff';

/** La foto: un PNG de verdad, porque el servidor valida el contenido y no la extensión (`docs/08` §9). */
const PHOTO = fileURLToPath(new URL('./fixtures/product-photo.png', import.meta.url));

/** Los cinco campos del alta de un producto, que este recorrido rellena dos veces: una para crearlo
 * y otra para que el SKU repetido lo rechace. El precio va con coma a propósito —es como lo escribe
 * media de la gente que abre esta pantalla, y el esquema la acepta— y el dinero viaja como cadena de
 * punta a punta, nunca como `number`. */
async function fillProductForm(
  page: Page,
  product: { sku: string; name: string; price: string; type: string },
): Promise<void> {
  // El formulario tiene que estar en pantalla **antes** de escribir en él. El listado del que se
  // llega rotula su filtro «SKU» exactamente igual que este campo, así que rellenar mientras el SPA
  // todavía no ha desmontado el listado escribe el SKU en el filtro: el alta se envía sin SKU, la
  // pantalla responde con la validación del campo y el `409` que este recorrido busca no llega a
  // pedirse nunca. Esperar aquí, y no en cada llamada, es lo que hace que no vuelva a pasar.
  await expect(page.getByRole('heading', { level: 1, name: 'Nuevo producto' })).toBeVisible();

  await page.getByLabel('SKU', { exact: true }).fill(product.sku);
  await page.getByLabel('Nombre', { exact: true }).fill(product.name);
  await page.getByLabel('Descripción').fill('Alta del recorrido de back-office.');
  await page.getByLabel('Precio').fill(product.price);
  await page.getByLabel('Tipo').selectOption({ label: product.type });
}

/**
 * El recorrido del administrador: crea un tipo, crea un producto de ese tipo, le pone foto, lo edita,
 * lo descataloga y lo reactiva, y da de alta a un empleado.
 *
 * **Es la otra mitad de la paridad con `ukun-empleados`** (`docs/04-roadmap.md`, SINC-5). La gestión
 * de empleados (RF-11) vive aquí y no en un cuarto fichero porque el administrador es el único rol
 * que puede hacerla, y `docs/03-analisis-y-patrones.md` §3 fija tres recorridos y no más.
 *
 * El orden de las pantallas es el de uso y no el de importancia: los tipos van antes que los
 * productos porque un producto no se crea sin un tipo, que es la primera pantalla que hace falta en
 * un catálogo recién instalado (`sections.ts`, `docs/04-roadmap.md` v0.7).
 *
 * **Nada de lo que este recorrido deja se limpia al terminar, y es a propósito.** Descatalogar es
 * reversible y se revierte; borrar no lo es, y el borrado físico de un producto solo procede si nadie
 * lo ha referenciado nunca —una aserción sobre eso sería frágil—. El tipo, en cuanto tiene un
 * producto colgando, ya no se puede borrar (RF-13), y este recorrido lo comprueba en vez de pelearse
 * con ello. Lo que hace que dos pasadas seguidas no choquen es que el SKU, el nombre del tipo y el
 * correo llevan marca de tiempo, no que la base se quede limpia.
 */
test('un administrador da de alta un tipo, un producto con foto y un empleado', async ({ page }) => {
  const typeName = newProductTypeName();
  const sku = newSku();
  const productName = `Producto E2E ${sku}`;
  const renamedProduct = `${productName} (revisado)`;

  await test.step('entra y abre el back-office, que le ofrece las cuatro secciones', async () => {
    await signInAsStaff(page, ADMIN);

    // El orden del rail es el de `BACKOFFICE_SECTIONS`, y es el de uso: sin tipos no hay productos.
    for (const section of ['Stock', 'Tipos de producto', 'Productos', 'Empleados']) {
      await expect(rail(page).getByRole('link', { name: section, exact: true })).toBeVisible();
    }

    await expect(page.getByText(`Tu tienda: ${STORE}`)).toBeVisible();
  });

  await test.step('crea un tipo de producto, y repetir el nombre lo rechaza', async () => {
    await openSection(page, 'Tipos de producto');
    await expect(page.getByRole('heading', { level: 1, name: 'Tipos de producto' })).toBeVisible();

    // Un botón y no un enlace: lo que abre es un diálogo, no una ruta. Un tipo tiene un solo campo
    // editable, y el back-office reserva las pantallas enteras para formularios con grupos.
    await page.getByRole('button', { name: 'Nuevo tipo' }).click();
    const create = page.getByRole('dialog', { name: 'Nuevo tipo de producto' });
    await create.getByLabel('Nombre').fill(typeName);
    await create.getByRole('button', { name: 'Crear tipo' }).click();

    await expect(create).toHaveCount(0);
    // Nace sin productos, que es lo que hace que de momento sí se pueda borrar.
    await expect(page.getByRole('row', { name: new RegExp(typeName, 'u') })).toContainText(
      'Sin productos',
    );

    // El nombre es único (RF-13). El `409 DUPLICATE_VALUE` se lee por el campo que choca y se pinta
    // en ese campo, no como un fallo de la pantalla: el diálogo sigue abierto con lo tecleado dentro.
    await page.getByRole('button', { name: 'Nuevo tipo' }).click();
    const again = page.getByRole('dialog', { name: 'Nuevo tipo de producto' });
    await again.getByLabel('Nombre').fill(typeName);
    await again.getByRole('button', { name: 'Crear tipo' }).click();

    await expect(again.getByText('Ya hay otro tipo con ese nombre.')).toBeVisible();
    await again.getByRole('button', { name: 'Cancelar' }).click();
  });

  await test.step('crea un producto de ese tipo', async () => {
    await openSection(page, 'Productos');
    await page.getByRole('link', { name: 'Nuevo producto' }).click();

    await fillProductForm(page, { sku, name: productName, price: '199,95', type: typeName });
    await page.getByRole('button', { name: 'Crear producto' }).click();

    // Aterriza en la ficha del producto recién creado, cuyo `h1` es su nombre. Nace sin foto: la
    // foto es un sub-recurso aparte y no un campo del alta (RF-10).
    await expect(page.getByRole('heading', { level: 1, name: productName })).toBeVisible();
    await expect(page).toHaveURL(/\/admin\/products\/\d+$/u);
  });

  await test.step('repetir el SKU lo rechaza y enseña de quién es', async () => {
    await page.getByRole('link', { name: 'Volver al catálogo' }).click();
    await page.getByRole('link', { name: 'Nuevo producto' }).click();

    await fillProductForm(page, {
      sku,
      name: 'Otro producto con el mismo SKU',
      price: '10,00',
      type: typeName,
    });
    await page.getByRole('button', { name: 'Crear producto' }).click();

    // Un SKU es de un producto para siempre (ADR-0015), así que la salida no es reintentar sino ver
    // quién lo tiene — y el enlace pide `status=ALL` porque ese dueño podría estar descatalogado.
    // Se dice dos veces y en dos sitios distintos a propósito: en el campo que hay que corregir, y
    // en el aviso que explica por qué y adónde ir.
    await expect(page.getByText('Ese SKU ya es de otro producto.')).toBeVisible();
    await expect(
      page.getByRole('heading', { name: 'Ese SKU ya es de otro producto' }),
    ).toBeVisible();
    await page.getByRole('link', { name: 'Ver qué producto lo tiene' }).click();

    const row = page.getByRole('row', { name: new RegExp(sku, 'u') });
    await expect(row).toContainText(productName);
    // El precio se escribió con coma y vuelve del servidor como el mismo importe: la conversión no se
    // ha perdido por el camino.
    await expect(row).toContainText('199,95');

    await page.getByRole('link', { name: productName }).click();
    await expect(page.getByRole('heading', { level: 1, name: productName })).toBeVisible();
  });

  await test.step('le sube la foto, que es una operación aparte', async () => {
    await page.getByLabel('Elegir una foto').setInputFiles(PHOTO);
    // Lo elegido se dibuja antes de enviarse: «vista previa» significa que todavía no ha pasado nada.
    await expect(page.getByAltText('Vista previa de la foto elegida')).toBeVisible();

    await page.getByRole('button', { name: 'Subir foto' }).click();

    // «Quitar foto» solo se pinta con `imageUrl` no nulo, así que su aparición es la confirmación de
    // que el servidor aceptó el fichero por su contenido —no por su extensión— y lo guardó.
    await expect(page.getByRole('button', { name: 'Quitar foto' })).toBeVisible();
    await expect(page.getByAltText(`Foto de ${productName}`)).toBeVisible();
  });

  await test.step('lo edita', async () => {
    // El SKU no está entre los campos: se fijó al darlo de alta y cambiarlo sería dar de alta otro
    // producto. Está en la ficha como valor, con su explicación al lado.
    await expect(page.getByRole('group', { name: 'SKU' })).toHaveText(sku);

    await page.getByLabel('Nombre', { exact: true }).fill(renamedProduct);
    await page.getByRole('button', { name: 'Guardar cambios' }).click();

    await expect(page.getByText('Cambios guardados.')).toBeVisible();
    await expect(page.getByRole('heading', { level: 1, name: renamedProduct })).toBeVisible();
  });

  await test.step('lo descataloga: sale del catálogo y deja de poder reponerse', async () => {
    // Sin diálogo de confirmación: la operación es idempotente y reversible, y la pantalla dice al
    // lado que descatalogar no borra nada (ADR-0013).
    await page.getByRole('button', { name: 'Descatalogar' }).click();
    await expect(page.getByRole('button', { name: 'Reactivar' })).toBeVisible();
    await expect(page.getByText('Descatalogado', { exact: true })).toBeVisible();

    // Un producto fuera del catálogo no se puede reponer: `GET /api/products/by-sku` solo resuelve
    // activos, que es ADR-0013 cruzado con ADR-0015 y el tramo que ninguna pantalla prueba sola.
    await openSection(page, 'Stock');
    await page.getByRole('link', { name: 'Dar de alta por SKU' }).click();
    await page.getByLabel('SKU del producto').fill(sku);
    await page.getByRole('button', { name: 'Buscar' }).click();
    await expect(
      page.getByText('No hay ningún producto activo con ese SKU. Puede que no exista o que esté descatalogado.'),
    ).toBeVisible();

    // Y tampoco está en el catálogo activo, que es la vista con la que abre el listado.
    await openSection(page, 'Productos');
    await commitFilter(page, 'SKU', sku);
    await expect(page.getByText('Ningún producto coincide con la búsqueda.')).toBeVisible();

    await page.getByLabel('Estado').selectOption({ label: 'Descatalogados' });
    await expect(page.getByRole('link', { name: renamedProduct })).toBeVisible();
  });

  await test.step('lo reactiva, y el tipo ya no se puede borrar', async () => {
    await page.getByRole('link', { name: renamedProduct }).click();
    await page.getByRole('button', { name: 'Reactivar' }).click();

    // Vuelve a estar en el catálogo: el badge desaparece y el botón vuelve a ofrecer descatalogar.
    await expect(page.getByRole('button', { name: 'Descatalogar' })).toBeVisible();
    await expect(page.getByText('Descatalogado', { exact: true })).toHaveCount(0);

    // El tipo creado al principio tiene ya un producto, así que su diálogo de borrado abre
    // directamente en la negativa: no se ofrece un botón destructivo que va a fallar seguro (RF-13).
    await openSection(page, 'Tipos de producto');
    await expect(page.getByRole('row', { name: new RegExp(typeName, 'u') })).not.toContainText(
      'Sin productos',
    );

    await page.getByRole('button', { name: `Borrar ${typeName}` }).click();
    const dialog = page.getByRole('dialog', { name: 'Borrar tipo de producto' });
    await expect(dialog.getByText('Este tipo no se puede borrar')).toBeVisible();
    await expect(dialog.getByRole('button', { name: 'Borrar tipo' })).toHaveCount(0);
    await dialog.getByRole('button', { name: 'Entendido' }).click();
  });

  await test.step('da de alta a un empleado en su tienda', async () => {
    const email = newEmployeeEmail();

    await openSection(page, 'Empleados');
    await expect(
      page.getByRole('heading', { level: 1, name: `Empleados de ${STORE}` }),
    ).toBeVisible();

    await page.getByRole('link', { name: 'Dar de alta empleado' }).click();
    await expect(
      page.getByRole('heading', { level: 1, name: 'Dar de alta un empleado' }),
    ).toBeVisible();

    // No hay ningún selector de tienda, y no es un olvido: el nuevo empleado se incorpora a la del
    // administrador que rellena esto, resuelta desde su `account_id` (RF-11, `docs/08` §3).
    await expect(page.getByRole('combobox')).toHaveCount(0);

    await page.getByLabel('Correo').fill(email);
    await page.getByLabel('Contraseña').fill(DEMO_PASSWORD);
    await page.getByLabel('Nombre', { exact: true }).fill('Elena');
    await page.getByLabel('Apellido').fill('Prueba');
    await page.getByLabel('Puesto').fill('Vendedora');
    await page.getByRole('button', { name: 'Dar de alta' }).click();

    // La confirmación es aterrizar en el listado filtrado por su correo, donde su fila es la única.
    await expect(page).toHaveURL(new RegExp(`q=${encodeURIComponent(email)}`, 'u'));
    await expect(page.getByRole('cell', { name: email })).toBeVisible();
  });
});
