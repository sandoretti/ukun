# Frontend: React, UX/UI y buenas prácticas

> **Fuente de verdad de:** tema y paleta, estructura de `apps/ukun-web`, diseño de los componentes base, datos de servidor y query keys, formularios, iconografía, i18n en pantalla y accesibilidad.
> **Léelo si:** vas a tocar cualquier cosa de `apps/ukun-web`.
> **No decide:** el contrato que consume (`docs/api/openapi/`), ni los `code`s de error (`12-error-contract.md`), ni cómo se escribe un test (`13-testing.md`).
> **Idioma:** identificadores y props en inglés (ADR-0018); el texto de pantalla siempre vía `t()` (ADR-0019).

Decisión base (ver `adr/0002-frontend-react-bootstrap.md`): **react-bootstrap**, continuidad con el Bootstrap 5.3 ya usado hoy vía CDN en las 18 plantillas Thymeleaf. Migración mecánica del markup, no un rediseño desde cero — pero eso no significa copiar la deuda visual actual tal cual.

## Qué se hereda y qué se corrige del diseño actual

El CSS actual (`ukun-clientes/static/css/styles.css` y `ukun-empleados/static/css/styles.css`) son dos ficheros distintos con **dos paletas verdes distintas** (`#365a08`/`#e5f6db` en uno, `#5d9d0b`/`#456e0f`/`#E9F5D8` en otro), colores repetidos literalmente en vez de variables, y un color de marca puesto inline (`style="background-color: #5d9d0b"` en el layout de empleados). Se unifica en un único tema.

- Un solo fichero de tema (`shared/theme/bootstrap-overrides.scss`, con `sass` como dependencia npm) sobre las variables de Bootstrap 5.3: `--bs-primary`, `--bs-body-font-family`, etc. — aplicado a toda la app, ya no hay "app de clientes" y "app de empleados" con temas distintos, hay una app con una sección de back-office.
- **Los dos `styles.css` legacy (`ukun-clientes/static/css/styles.css`, `ukun-empleados/static/css/styles.css`, 183 líneas en total) se descartan, no se traducen.** Son exactamente el problema que se está corrigiendo: dos paletas, colores repetidos literalmente en vez de variables, un color de marca puesto inline (`style="background-color: #5d9d0b"`) en el layout de empleados. El tema nuevo se construye desde cero como overrides sobre las variables de Bootstrap, con la paleta fijada abajo.
- Reutilizar la tipografía ya existente (`AvertaStandard-Regular.otf`, hoy duplicada en ambos `static/`) — se sirve una sola vez.

### Paleta

Los dos verdes actuales (`#365a08`/`#e5f6db` en un módulo, `#5d9d0b`/`#456e0f`/`#E9F5D8` en el otro) se unifican cambiando el **papel** de cada uno, no inventando una paleta nueva — ninguno de los dos verdes de marca (`#5d9d0b`: 3.34:1, `#365a08`: 8.01:1 solo en su rol de hover) cumplía por sí solo un contraste AA (4.5:1) como color de texto/botón sobre blanco:

| Token | Color | Contraste vs blanco | Uso |
|---|---|:-:|---|
| `--bs-primary` | `#456e0f` | 6.02:1 (AA ✓) | texto de enlace, fondo de botón primario (con texto blanco) |
| `--bs-primary-hover` | `#365a08` | 8.01:1 (AAA ✓) | estado hover/active del botón primario |
| `--bs-accent` | `#5d9d0b` | 3.34:1 | **solo** decorativo: bordes, iconos grandes, fondos de badge — insuficiente para texto normal |
| `--bs-primary-bg` | `#E9F5D8` | — | fondo suave (tarjetas destacadas, alertas de éxito) |

Regla dura: **ningún token con ratio de contraste por debajo de 4.5:1 se asigna a texto normal ni a un botón con texto encima.** `--bs-accent` es el único que puede bajar de ese umbral, y solo para elementos gráficos grandes.

## Cómo se acota "buen UX/UI" para que no devore el calendario

Es el requisito con más riesgo de no tener nunca un "terminado". Se acota así:

- **Entregable concreto en v0.1**, antes de construir ninguna pantalla: paleta (ya fijada arriba), tipografía, escala de espaciado, y 6-8 componentes base envueltos sobre react-bootstrap (`Button`, `Card`, `Input`/`FormGroup`, `Table`, `Alert`/`Toast`, `Badge`, `Icono`, estado vacío, estado de carga).
- **Prohibido rediseñar una pantalla ya entregada** antes de v1.0 salvo bug de usabilidad real. El pulido general (v1.0, pista P3 en `04-roadmap.md`) es el momento de revisar el conjunto, no cada slice individual.
- Cada pantalla nueva se construye combinando los componentes base — si hace falta un componente que no existe, se añade a la base, no se resuelve ad-hoc dentro de la pantalla.

## Estructura del proyecto (`apps/ukun-web`)

```
apps/ukun-web/src/
├── app/            # router, providers (QueryClient, tema, i18n), layout raíz
├── features/       # una carpeta por dominio: catalog, cart, orders,
│                   # profile, auth, backoffice, inventory — cada una con sus
│                   # propias pantallas, hooks y componentes locales
├── shared/
│   ├── ui/         # componentes base envueltos sobre react-bootstrap
│   ├── api/        # cliente HTTP (axios, ver ADR-0010) + hooks de TanStack Query
│   └── hooks/      # hooks transversales (useAuth, useDebounce, ...)
├── i18n/           # namespaces de react-i18next: common, catalog, cart,
│                   # checkout, profile, backoffice, errors — ver ADR-0019
├── mocks/          # handlers de MSW, generados/alineados con el OpenAPI
└── types/api.d.ts  # generado por openapi-typescript — NUNCA editar a mano
```

`features/backoffice` cubre las pantallas de empleado y de admin: stock (RF-09), CRUD de productos con su imagen (RF-10), CRUD de tipos de producto (RF-13) y empleados (RF-11). Todo lo que sea solo de admin habla contra `/api/admin/**` (ADR-0020) y cuelga de la rama `/admin/*` del router, así que la decisión de pintar o no la entrada al back-office se toma en un sitio, con los roles que devuelve `GET /api/me` — no hay una lista de rutas privilegiadas repartida por los componentes.

Nombres de carpeta y de módulo en inglés (ADR-0018), alineados con los módulos de Spring Modulith del backend (ADR-0017) donde aplica — `catalog`, `cart`, `orders`, `inventory` son el mismo concepto a ambos lados de la API. Las carpetas de `features/` no se importan entre sí directamente — si `orders` necesita algo de `cart`, ese algo sube a `shared/`. Es la misma regla que ArchUnit/Spring Modulith hace cumplir en el backend (`03-analisis-y-patrones.md`, ADR-0017), aplicada al frontend.

## Cómo se diseña un componente base de `shared/ui/`

Los 6-8 componentes base de arriba son la pieza que más se va a reutilizar y la que más caro sale rehacer, así que su API se decide con tres invariantes:

- **Variantes explícitas, no props booleanas acumuladas.** Un `<Button danger primary small outline>` con cinco booleanos permite dieciséis combinaciones sin sentido y obliga a leer el cuerpo del componente para saber cuáles son legales. Se sustituye por variantes con nombre (`variant="danger"`) o, cuando el comportamiento difiere de verdad y no solo el aspecto, por componentes distintos. Cada booleano nuevo que se le añade a un componente base es una señal de que hacía falta componer, no parametrizar.
- **`children` antes que props `renderX`.** Un componente que recibe `renderHeader`, `renderFooter` y `renderActions` está reimplementando la composición que JSX ya da gratis. Para lo compuesto de verdad (una tarjeta con cabecera, cuerpo y pie), compound components con contexto interno — que es como react-bootstrap expone `Card.Header`, y conviene no pelearse con esa forma.
- **`ref` es una prop normal.** React 19 la pasa directamente; en código nuestro no se escribe `forwardRef`. react-bootstrap 2.x sí lo usa por dentro, y eso ni se toca ni se envuelve para "arreglarlo".

Y una frontera que no se cruza: **el estado de servidor nunca sube a un contexto**. Para eso está TanStack Query (sección siguiente). El contexto queda para estado de UI —tema, idioma, sesión, apertura de un modal—, que es poco y cambia despacio. Un provider que cachea respuestas del API reimplementa peor algo que ya está resuelto.

## Datos del servidor: TanStack Query, no `useEffect` + `fetch`

Todo dato que viene del API pasa por un hook de TanStack Query (`useQuery`/`useMutation`), nunca por un `fetch` suelto en un `useEffect`. Esto da de fábrica: caché, revalidación, estados `isLoading`/`isError` consistentes, y reintentos — que es exactamente lo que hoy no existe en ningún sitio (el JS actual, `scripts.js`, no hace ni una llamada AJAX; toda la interacción es navegación de servidor).

**Las query keys salen de una factoría por feature, nunca de un array literal escrito dentro de un componente.** Vive en `features/<x>/api/`, y su jerarquía va de entidad a lista/detalle a filtros:

```ts
export const productKeys = {
  all: ['products'] as const,
  lists: () => [...productKeys.all, 'list'] as const,
  list: (filters: ProductFilters) => [...productKeys.lists(), filters] as const,
  detail: (sku: string) => [...productKeys.all, 'detail', sku] as const,
};
```

La jerarquía es lo que hace que invalidar sea dirigido: tras editar un producto se invalida `productKeys.detail(sku)` y, si hace falta, `productKeys.lists()` — no un `invalidateQueries()` sin argumentos que tire la caché entera. Escribir la misma key a mano en dos ficheros es el bug de caché clásico, y aparece en cuanto conviven varias features escribiendo sobre los mismos datos (v0.6: carrito y pedidos sobre el mismo producto). Se fija ahora, con `shared/api/` todavía vacío, porque después cuesta mucho más.

En listados paginados, `placeholderData` con la página anterior mientras carga la siguiente — la alternativa es que la tabla desaparezca y reaparezca en cada cambio de página.

## El estado de una pantalla de listado vive en la URL

Filtros, orden y página van en query params, no en un `useState` local. Es lo que hace que un resultado de catálogo se pueda enlazar y compartir, que el botón atrás del navegador haga lo que el usuario espera, y que recargar no devuelva al estado inicial. Aplica desde la primera pantalla de listado (catálogo, v0.3) y a todas las que vengan después: stock y pedidos del back-office tienen el mismo problema.

## Formularios: react-hook-form + zod

Cada formulario define su esquema `zod` una vez; ese esquema valida en el cliente **y** sus tipos se derivan para el payload que se envía. Los errores de validación del servidor (`code: VALIDATION_FAILED` con extensión `errors: [{field, message}]`, ver `docs/12-error-contract.md`) se mapean a los mismos campos del formulario — un solo sitio donde se pinta un error de campo, venga de donde venga.

## Tipos y mocks: generados, no escritos a mano

`types/api.d.ts` se genera con `openapi-typescript` a partir de `docs/api/openapi/ukun-api.yaml` (ver el `local_resource` del Tiltfile en `06-entorno-local-tilt.md`). Los handlers de MSW en `mocks/` responden con esos mismos tipos. Si el backend cambia un contrato, el tipo cambia solo y TypeScript señala cada punto del frontend que hay que actualizar — así es como el check de deriva de OpenAPI (`04-roadmap.md`) se convierte en errores de compilación, no en sorpresas en producción.

## Accesibilidad

No es una pasada final: react-bootstrap ya resuelve buena parte (roles ARIA en modales, dropdowns, tabs), pero cada componente base de `shared/ui/` se revisa explícitamente por: foco visible, orden de tabulación, labels asociados a inputs (el signup actual tiene un formulario largo sin agrupar campos relacionados — se corrige agrupando con `fieldset`/`legend`), y contraste de color suficiente en la paleta elegida. La pasada de a11y de v1.0 (`04-roadmap.md`) es una auditoría con axe/Lighthouse sobre lo ya construido, no el único punto donde se piensa en esto.

La lista concreta que se comprueba en cada componente, para que "revisar accesibilidad" tenga un contenido verificable y no sea una intención:

- **Semántica antes que ARIA.** `<button>` para acciones, `<a>`/`<Link>` para navegación; nunca un `<div onClick>`. Un rol ARIA es lo que se pone cuando no existe el elemento adecuado, no lo primero que se prueba.
- **Foco.** `:focus-visible` en vez de `:focus`, para no pintar el anillo al hacer clic con el ratón. Nunca `outline: none` sin un sustituto visible en su lugar.
- **Botones de solo icono llevan `aria-label`**; los iconos decorativos, `aria-hidden="true"` (lo que ya hace `<Icon>`).
- **Lo que cambia solo se anuncia**: toasts y validación asíncrona en una región `aria-live="polite"`. Si el usuario no está mirando ese punto de la pantalla, el cambio no existe.
- **Formularios**: `autocomplete` y `name` con sentido, el `type` correcto (`email`, `tel`, `url`), nunca bloquear el pegado, label que sea zona clicable del control, y al enviar con errores el foco va al primero, con el mensaje inline junto a su campo.
- **Movimiento**: se respeta `prefers-reduced-motion`, y se anima `transform`/`opacity`, nunca `transition: all`.
- **Contenido largo**: todo contenedor de texto decide qué hace cuando el contenido no cabe (truncar, recortar a N líneas, partir palabra). Un nombre de producto de 120 caracteres no debe romper la tarjeta.
- **Números**: cifras tabulares en columnas de precio y cantidad, para que comparen alineadas.

Esa lista es también el guión de la skill externa `web-design-guidelines` (ver `.claude/skills/README.md`), que sirve para revisar una pantalla ya escrita — con la salvedad de que sus ejemplos vienen en clases de Tailwind y aquí se traducen al tema.

## Iconografía: Material Symbols autohospedado

Hoy hay 7 usos sueltos de Bootstrap Icons repartidos en las plantillas, sin ningún criterio de cuándo usar uno u otro. Se sustituyen por **Material Symbols** (`material-symbols` como dependencia npm, nunca CDN — misma regla que Bootstrap) envuelto en un único componente:

```tsx
<Icon name="delete" size="md" />
```

`size`, peso óptico y color salen de props acotadas que leen del tema (nunca un `style` inline ni un tamaño en píxeles suelto por pantalla) — es la misma disciplina que ya rige el resto de `shared/ui/`, y los mismos nombres de prop en inglés que el resto del código (ADR-0018). Un mapa fijo de icono por acción evita que aparezcan variantes distintas del mismo concepto en pantallas distintas:

| Acción | Icono | Acción | Icono |
|---|---|---|---|
| Eliminar | `delete` | Editar | `edit` |
| Añadir al carrito | `add_shopping_cart` | Buscar | `search` |
| Cerrar sesión | `logout` | Tienda | `storefront` |
| Envío | `local_shipping` | Favorito | `favorite` |
| Éxito / confirmado | `check_circle` | Error / rechazado | `error` |
| Advertencia | `warning` | Stock | `inventory_2` |

Ampliar el mapa es añadir una fila aquí, no elegir un icono ad-hoc dentro de una pantalla.

## Estados que toda pantalla de datos debe cubrir

Carga · vacío (p. ej. "tu carrito está vacío", que hoy sí existe en `carrito.html` pero no en todas las pantallas) · error (con el `code` del `ProblemDetail` — registro completo en `docs/12-error-contract.md` — resuelto vía `t('errors:CODE', {...})`, nunca mostrando `detail` crudo, ver ADR-0019) · éxito. Se implementan una vez en los componentes base y se reutilizan — no se improvisa un `{loading && <p>Cargando...</p>}` por pantalla.

## i18n: react-i18next (ver ADR-0019)

Con el backend emitiendo solo `code`s (`docs/12-error-contract.md`) y ningún texto de usuario, **todo** el texto de la aplicación vive aquí, en los dos idiomas soportados (`es`/`en`).

- **Namespaces por feature**: `common`, `catalog`, `cart`, `checkout`, `profile`, `backoffice`, `errors` — un fichero JSON por namespace y por idioma en `src/i18n/{es,en}/`, cargados perezosamente por ruta.
- **`errors.json` indexado por `code`**, con las extensiones del `ProblemDetail` como parámetros de interpolación: `t('errors:INSUFFICIENT_STOCK', { sku, available, requested })`. Todo `code` del registro tiene entrada en los dos idiomas — verificado por el test de paridad de traducciones (`docs/13-testing.md`).
- **Selector de idioma en la barra de navegación**, siempre visible. Idioma activo: `customer.locale` si hay sesión (viaja entre dispositivos), `localStorage` ?? `navigator.language` ?? `es` si es anónimo.
- **`<html lang>`** se actualiza al cambiar de idioma — requisito de accesibilidad, no cosmética; entra en la revisión de a11y de cada componente base junto al resto de criterios de la sección de arriba.
- **Formatos de número, moneda y fecha siempre con `Intl`** (`Intl.NumberFormat`, `Intl.DateTimeFormat`), nunca a mano y nunca en el backend — el backend envía `BigDecimal` y `TIMESTAMPTZ` sin formatear.
- **Regla de ESLint** (`i18next/no-literal-string` o equivalente) activa sobre `src/`: un string literal en JSX que debería ser una clave de traducción rompe el build — mismo criterio de "se hace cumplir, no se confía" que ArchUnit/Modulith en el backend.

Detalle completo (librería, estructura de ficheros, alternativas descartadas): ADR-0019.

## Testing de componentes

**Vitest + React Testing Library** para `shared/ui/` y los hooks/formularios de cada feature — consultas por rol/label (nunca por selector CSS o `data-testid` salvo que no haya alternativa accesible), coherente con la sección de accesibilidad de arriba: un test que solo pasa buscando por `data-testid` no verifica que un lector de pantalla pueda usar el componente. Playwright (`03-analisis-y-patrones.md`) cubre los tres recorridos end-to-end completos; Vitest cubre todo lo demás sin pagar el coste de un navegador real por aserción. Convenciones completas de cómo se escribe cada test: `docs/13-testing.md` y skill `testing`.

## Qué se descarta explícitamente

- **CSS a mano por pantalla** (`style={{...}}` inline, salvo casos puntuales de valor dinámico): el tema centralizado es el único sitio donde se define color/tipografía/espaciado.
- **Bootstrap por CDN**: se instala como dependencia npm (`bootstrap` + `react-bootstrap`), versionado como el resto del stack — el CDN de hoy (`cdn.jsdelivr.net`, con y sin `integrity` según el fichero) desaparece.
- **jQuery**: el layout de empleados hoy carga jQuery + Popper + Bootstrap 4.5 JS en un fragmento `footer` que ningún template usa realmente (código muerto confirmado en la exploración). No se traslada nada de eso.
- **Iconos por CDN o por fuente de icono con licencia distinta** (Font Awesome, Bootstrap Icons): un único sistema de iconos, autohospedado, coherente con la regla de Bootstrap.
