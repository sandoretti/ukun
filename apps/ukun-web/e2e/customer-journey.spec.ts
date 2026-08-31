import { expect, test } from '@playwright/test';

import {
  expectNamedTabStops,
  expectNoA11yViolations,
  expectSkipLinkWorks,
  expectVisibleFocusRing,
  focusByKeyboard,
} from './fixtures/a11y';
import { newCustomer, signUp } from './fixtures/customer';
import { signIn, useSpanish } from './fixtures/session';

/**
 * El recorrido de cliente completo, de punta a punta y contra el sistema de verdad: alta → catálogo
 * → carrito → checkout → historial.
 *
 * **Es la validación de paridad con `ukun-clientes`** (`docs/04-roadmap.md`, SINC-4): no hay tests de
 * caracterización sobre el legacy porque no está en marcha y se borra, así que lo que declara que el
 * recorrido funciona es este verde y no una comparación con el código viejo.
 *
 * Un solo `test` y no seis, a propósito. Cada paso depende del anterior —no hay carrito sin sesión ni
 * pedido sin carrito—, y partirlo obligaría a rehacer el alta y la compra en cada uno o a compartir
 * estado entre tests, que es peor. Lo que este smoke comprueba es que los cinco tramos encajan; los
 * detalles de cada pantalla los cubre Vitest, que no paga un navegador por aserción
 * (`docs/13-testing.md` §2).
 *
 * Lo que **no** se afirma es ninguna cantidad absoluta de stock: el propio recorrido consume unidades
 * en cada ejecución, así que una aserción sobre «quedan 3» sería verde hoy y roja el martes.
 */
test('un cliente nuevo se da de alta, compra y encuentra su pedido en el historial', async ({ page }) => {
  const customer = newCustomer();
  await useSpanish(page);

  await test.step('se da de alta y entra con la contraseña que acaba de elegir', async () => {
    await signUp(page, customer);

    // El alta sale hacia `ukun-auth`, que vive en otro puerto y sirve su propia página de entrada.
    await signIn(page, customer);

    // De vuelta en el SPA con sesión: el correo es el nombre accesible del menú de cuenta.
    await expect(page.getByRole('button', { name: customer.email })).toBeVisible();
  });

  const productName = await test.step('elige del catálogo un producto disponible', async () => {
    await page.goto('/products');

    // El primero que la propia pantalla pinta como disponible, nunca un SKU escrito aquí: el stock
    // de los datos demo cambia con cada compra, y este test es una de las que lo cambian.
    const card = page.getByRole('link').filter({ hasText: 'En stock' }).first();
    await expect(card).toBeVisible();
    const name = (await card.getByRole('heading').innerText()).trim();

    // El catálogo es la pantalla más cargada del recorrido —barra, filtros, rejilla, paginación— y
    // la primera donde se audita el conjunto ya pintado, con el CSS puesto.
    await expectNoA11yViolations(page, 'catálogo');

    // Seis paradas de barra por delante de la rejilla es lo que ahorra el salto al contenido, y solo
    // las ahorra si además lleva el foco (por eso el `<main>` es `tabIndex={-1}`).
    await expectSkipLinkWorks(page, 'la tienda');

    await card.click();
    await expect(page.getByRole('heading', { level: 1, name })).toBeVisible();

    return name;
  });

  await test.step('lo añade al carrito', async () => {
    await page.getByRole('button', { name: 'Añadir al carrito' }).click();

    // La confirmación se anuncia junto al botón, y es lo que dice que la escritura terminó.
    await expect(page.getByText('Ya tienes 1 unidad en el carrito.')).toBeVisible();
  });

  const total = await test.step('revisa el carrito y se lleva su total', async () => {
    await page.getByRole('link', { name: 'Ver el carrito' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Tu carrito' })).toBeVisible();
    await expect(page.getByRole('link', { name: productName })).toBeVisible();

    // Con sesión y con contenido: es la primera pantalla del recorrido que solo existe habiendo
    // entrado, y ninguna pasada sin navegador puede llegar hasta aquí.
    await expectNoA11yViolations(page, 'carrito');

    const summary = page.getByRole('complementary', { name: 'Resumen del pedido' });
    // El total del resumen: la última pareja de la lista de definiciones, que es la que va rotulada
    // «Total» y en grande.
    const amount = (await summary.locator('dd').last().innerText()).trim();
    expect(amount).not.toBe('');

    return amount;
  });

  const orderId = await test.step('confirma el pedido', async () => {
    await page.getByRole('link', { name: 'Tramitar pedido' }).click();

    // El intento lo emite el servidor (ADR-0016); la pantalla no inventa ninguna clave.
    await expect(page.getByRole('heading', { level: 1, name: 'Confirmar pedido' })).toBeVisible();
    await expectNoA11yViolations(page, 'checkout');

    // El paso que cobra se puede dar sin ratón, y el botón enseña que lo tiene. Es donde el anillo
    // del tema importa más: era uno de los que Bootstrap tapaba con `outline: 0`.
    const confirm = page.getByRole('button', { name: 'Confirmar pedido' });
    await focusByKeyboard(page, confirm);
    await expectVisibleFocusRing(page, 'confirmar pedido');
    await confirm.click();

    // Aterriza en el detalle del pedido recién hecho, que es la misma pantalla que el historial
    // enlaza: una pantalla con dos entradas, no dos.
    await expect(page.getByRole('alert')).toContainText('Pedido confirmado');
    const heading = await page.getByRole('heading', { level: 1 }).innerText();

    // El total del pedido es el del carrito. Es la comprobación de que lo que se cobra es lo que se
    // enseñó, y de paso que el importe lo pone el servidor y no una multiplicación del navegador.
    await expect(page.getByRole('cell', { name: total }).last()).toBeVisible();

    const id = /(\d+)/u.exec(heading)?.[1];
    expect(id).toBeDefined();
    return id as string;
  });

  await test.step('lo encuentra en su historial', async () => {
    await page.getByRole('link', { name: 'Volver a mis pedidos' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Mis pedidos' })).toBeVisible();
    // El número del pedido **es** el enlace: diez enlaces «Ver el pedido» serían diez enlaces
    // idénticos para quien navega por lista de enlaces.
    await expect(page.getByRole('link', { name: `Pedido n.º ${orderId}` })).toBeVisible();

    await expectNoA11yViolations(page, 'historial de pedidos');

    // La barra completa, con sesión: ninguna de sus paradas es anónima — un control sin nombre en la
    // cabecera es una parada a la que se llega sin saber qué hace.
    await expectNamedTabStops(page, 4);
  });
});
