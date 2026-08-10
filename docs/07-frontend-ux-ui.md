# Frontend: React, UX/UI y buenas prácticas

> **Fuente de verdad de:** tema y paleta, estructura de `apps/ukun-web`, diseño de los componentes base, datos de servidor y query keys, formularios, iconografía, i18n en pantalla y accesibilidad.
> **Léelo si:** vas a tocar cualquier cosa de `apps/ukun-web`.
> **No decide:** el contrato que consume (`docs/api/openapi/`), ni los `code`s de error (`12-error-contract.md`), ni cómo se escribe un test (`13-testing.md`).
> **Idioma:** identificadores y props en inglés (ADR-0018); el texto de pantalla siempre vía `t()` (ADR-0019).

Decisión base (ver `adr/0002-frontend-react-bootstrap.md`): **react-bootstrap**, continuidad con el Bootstrap 5.3 ya usado hoy vía CDN en las 18 plantillas Thymeleaf. Migración mecánica del markup, no un rediseño desde cero — pero eso no significa copiar la deuda visual actual tal cual.

## Qué se hereda y qué se corrige del diseño actual

El CSS actual (`ukun-clientes/static/css/styles.css` y `ukun-empleados/static/css/styles.css`) son dos ficheros distintos con **dos paletas verdes distintas** (`#365a08`/`#e5f6db` en uno, `#5d9d0b`/`#456e0f`/`#E9F5D8` en otro), colores repetidos literalmente en vez de variables, y un color de marca puesto inline (`style="background-color: #5d9d0b"` en el layout de empleados). Se unifica en un único tema.

- Un solo tema (`shared/theme/`, con `sass` como dependencia npm) sobre las variables de Bootstrap 5.3: `--bs-primary`, `--bs-body-font-family`, etc. Es un índice (`index.scss`) y parciales por responsabilidad: `_tokens.scss` es la única fuente de la paleta, `_bootstrap.scss` el **único** `@use ... with` que configura Bootstrap, y luego `_fonts`, `_base`, `_utilities` y `components/*`. Aplicado a toda la app, ya no hay "app de clientes" y "app de empleados" con temas distintos, hay una app con una sección de back-office.
- **Los dos `styles.css` legacy (`ukun-clientes/static/css/styles.css`, `ukun-empleados/static/css/styles.css`, 183 líneas en total) se descartan, no se traducen.** Son exactamente el problema que se está corrigiendo: dos paletas, colores repetidos literalmente en vez de variables, un color de marca puesto inline (`style="background-color: #5d9d0b"`) en el layout de empleados. El tema nuevo se construye desde cero como overrides sobre las variables de Bootstrap, con la paleta fijada abajo.
- Reutilizar la tipografía ya existente (`AvertaStandard-Regular.otf`, hoy duplicada en ambos `static/`) — se sirve una sola vez.

### El logotipo

El original de marca es `resources/logo/UkunLogo.png` (1200×400) y **no se importa nunca desde el
frontend**: su tinta ocupa solo `(159,73)→(1041,327)`, así que arrastra un 13 % de aire transparente
a cada lado que descoloca cualquier alineación, y sus dos verdes (`#365a08` y `#5d9d0b`) son
ilegibles sobre el verde de la barra. Lo que se sirve es un derivado recortado y en blanco,
`apps/ukun-web/src/assets/ukun-wordmark-light.png` (882×254), regenerable con:

```python
from PIL import Image
src = Image.open('resources/logo/UkunLogo.png').convert('RGBA').crop((159, 73, 1041, 327))
out = Image.new('RGBA', src.size, (255, 255, 255, 0))
out.putalpha(src.getchannel('A'))   # el antialias vive en el alfa: se conserva
out.save('apps/ukun-web/src/assets/ukun-wordmark-light.png', optimize=True)
```

Ese derivado se usa **solo** en la barra de navegación. En la portada la marca va en tipografía
(`.hero__eyebrow` con la fuente de la casa), no como imagen: así comparte el borde izquierdo del
titular sin descontar aire a mano, escala con el viewport y no cuesta ni una petición ni un salto de
maquetación.

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
├── test/msw/       # dobles HTTP de Vitest — NUNCA los ve el navegador
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

En listados paginados, `placeholderData` con la página anterior mientras carga la siguiente — la alternativa es que la tabla desaparezca y reaparezca en cada cambio de página. Cuando la página se carga por bloques (el catálogo, ver abajo), la consulta es `useInfiniteQuery` y su key sale de la misma factoría (`productKeys.chunks(criterios, página)`, colgando de `lists()` para que invalidar por prefijo la siga alcanzando): el bloque nunca es un `useState` con un array al que se le hace `push`.

## El estado de una pantalla de listado vive en la URL

Filtros, orden y página van en query params, no en un `useState` local. Es lo que hace que un resultado de catálogo se pueda enlazar y compartir, que el botón atrás del navegador haga lo que el usuario espera, y que recargar no devuelva al estado inicial. Aplica desde la primera pantalla de listado (catálogo, v0.3) y a todas las que vengan después: stock y pedidos del back-office tienen el mismo problema.

La página se cuenta **desde 1** en la URL y se omite cuando vale 1 (`/products`, `/products?page=2`), aunque el API cuente desde 0: `?page=1` significando "la segunda" es una trampa para quien edita la barra de direcciones, y la traducción se hace en un solo sitio, la capa `api/` de la feature. Lo que **no** va en la URL es cuántos bloques de la página se han cargado: eso es avance dentro de un mismo resultado, no un resultado distinto, y escribirlo llenaría el historial de entradas que muestran lo mismo.

## Anatomía de una pantalla de listado

El catálogo (v0.3) fija la forma que repiten las demás pantallas de listado —tiendas, stock y pedidos del back-office—, tomada de las tiendas online que ya la tienen resuelta (IKEA, Amazon, PcComponentes):

- **El buscador por nombre vive en la barra de navegación, no entre los filtros.** Es la acción principal de una tienda y está en todas las pantallas. Se implementa como combobox accesible (`shared/ui/SearchBox.tsx`, patrón ARIA *combobox with listbox*: `aria-expanded`, `aria-controls`, `aria-activedescendant`, flechas, Enter, Escape) sobre `GET /api/products/suggest`, con el término escrito con retardo (`shared/hooks/useDebouncedValue.ts`) y `placeholderData` para que la lista no parpadee tecla a tecla. El componente base no pide datos: quien los resuelve es `shared/ui/ProductSearchBox.tsx`. Vive en `shared/` porque lo consumen la barra (`app/`) y la pantalla de inicio — nunca una feature importando otra.
- **Los filtros son un panel lateral**, no una fila de controles encima de la rejilla: `<Offcanvas responsive="lg">` es barra lateral pegajosa desde `lg` y panel deslizante por debajo, con **una sola instancia** del formulario (sin `id`s duplicados ni ramas por breakpoint). Una sección por criterio, separadas por un filete, y **sin scroll propio**: una segunda zona de scroll dentro de la página parte el sidebar en dos.
- **Se aplican al elegirlos**, sin botón "Filtrar". La excepción es un rango: el precio es un control de dos pomos (dos `input[type=range]` superpuestos, sin dependencia nueva y con el teclado nativo intacto) **más sus dos campos numéricos**, porque un slider no sabe decir "exactamente 249,50" — arrastrar es para explorar, escribir es para quien ya sabe lo que quiere. Los dos escriben los mismos dos valores, así que no hay estado que sincronizar, y ninguno filtra mientras se mueve: se aplica al soltar el pomo o al salir del campo, de modo que cruzar la pista es una petición y no cuarenta. El techo del recorrido se lee del propio catálogo (una página de un elemento ordenada a la baja, redondeada hacia arriba), nunca de una constante que caduca el día que alguien da de alta un sofá más caro.
- **Los filtros activos se pintan como chips** encima de la rejilla, cada uno con su botón de quitar y un "Limpiar todo". Es lo que mantiene honesta la pantalla cuando los controles no están a la vista: el nombre se escribe en la barra y el resto se esconde en el offcanvas del móvil.
- **El orden y el recuento van en la barra de resultados**, arriba a la derecha y arriba a la izquierda.
- **La rejilla crece al bajar, pero la página tiene tope**: se piden bloques de 24 productos (`CHUNK_SIZE`, múltiplo de las 2/3/4 columnas, así que cada bloque cierra filas completas) hasta 72 por página (`PAGE_SIZE`), y de ahí en adelante se pasa de página. Es el modelo de chollometro: el scroll infinito es cómodo para explorar, pero sin tope acaba en una lista que no se puede enlazar, con un DOM y una caché que crecen sin final y un pie de página inalcanzable. El bloque siguiente lo dispara un centinela con `IntersectionObserver` **y** un `<button>` "Ver más productos" que sigue ahí: lo automático es la comodidad, nunca la única vía, porque con teclado o con el observer sin disparar no habría ninguna. Bajo la rejilla queda el recuento de lo cargado ("Mostrando 48 de 105 productos", en `aria-live="polite"` — lo que cambia se anuncia) y, centrada debajo, la paginación con "Página X de Y", que no se pinta si solo hay una página. Al cambiar de página se vuelve arriba del documento: tras 72 productos el lector está en el fondo, y aterrizar a mitad de la siguiente es cómo un listado pierde a quien lo estaba leyendo.
- **La tarjeta de producto** es foto (proporción fija, `object-fit: cover`) → tipo como línea superior en versalitas → nombre a dos líneas → precio destacado con cifras tabulares → estado. Ni SKU ni `Badge` de color: son datos de la ficha o ruido de back-office.
- **La disponibilidad se muestra en pequeño**: punto de color + microtexto ("En stock" / "Sin stock online"), y la foto de lo agotado se atenúa. Un producto sin fila de stock es *no disponible*, nunca "desconocido" (RF-01). Que falle la lectura de disponibilidad no es un `Alert` a todo el ancho: es una nota discreta en la barra de resultados, porque el catálogo se sigue viendo.
- **Lo que no se puede filtrar, no se ofrece**: no hay filtro "solo disponibles" (la disponibilidad es otro endpoint, ADR-0017 §3, y filtrar en cliente falsearía el recuento y la paginación) ni recuento por tipo (`ProductType` público no lleva `productCount`; ese es `AdminProductType`).

Las utilidades que esto añade al tema —y que, como el resto, no se escriben inline en una pantalla— son `.product-media` (+ `--dimmed`), `.overline`, `.status-dot` (+ `--in`/`--out`), `.filter-sidebar`, `.filter-section`, `.price-range`, `.search-box`/`.search-suggestions` y `.icon-sm|md|lg`. El único `style` inline de todo esto son los dos porcentajes del tramo seleccionado del slider, que es exactamente el "valor dinámico" que la sección de descartes admite.

## Anatomía de una pantalla de ficha

La ficha de producto (v0.3) fija la forma de las pantallas de **un solo recurso** —tienda, pedido, cliente del back-office—, igual que el catálogo fija la de los listados. Dos columnas desde `lg`: el medio a la izquierda, lo que se lee a la derecha, y debajo el contenido secundario.

- **La foto no se recorta.** El listado usa `object-fit: cover` porque una rejilla necesita todas las celdas iguales; una ficha, no. El hueco es cuadrado y estable —para que la página no salte de un producto al siguiente— pero la imagen va `contain` dentro de él: de las 24 fotos del catálogo demo 19 son cuadradas, y a las verticales (camas, lámparas de pie) el encuadre 4/3 les corta el cabecero. Sin recuadro ni fondo de color: lo que se enseña es el producto, no su marco.
- **Ampliar la foto tiene dos caminos, y no son alternativos.** Una **lupa** que sigue al ratón (lente sobre la foto + panel ampliado al lado, con las posiciones como variables CSS dinámicas) solo se pinta bajo `@media (hover: hover) and (pointer: fine)` y de `xl` hacia arriba, que es donde hay ratón y hay sitio. El camino que **siempre** existe es el **visor** a pantalla completa que abre el clic o el `Enter`: un `Modal` de react-bootstrap —no un overlay a mano— por la trampa de foco, el `Escape` y la devolución del foco, con niveles fijos (×1 · ×1,5 · ×2 · ×3), el nivel anunciado en `aria-live`, y el desplazamiento resuelto por el `overflow` nativo del marco, que es lo que hace que las flechas del teclado muevan la imagen ampliada. Arrastrar es un atajo encima de eso, nunca el único medio.
- **La jerarquía de la columna derecha es tipo → nombre → referencia → precio → disponibilidad → descripción.** El **tipo** es un chip que enlaza al catálogo ya filtrado (`/products?typeId=N`, el mismo parámetro que lee la URL del listado): una ficha sin salida lateral es un callejón. El **SKU** se muestra como referencia discreta bajo el título ("Ref. SOFA-CUERO-01") — RF-01 lo exige, pero es un dato de consulta, no el contenido de la pantalla.
- **La disponibilidad sí es un bloque aquí.** En la rejilla son 24 productos a la vez y por eso va en microtexto; en la ficha es un solo producto y es la respuesta a "¿puedo comprarlo?", así que lleva icono (`check_circle` / `warning`) y **nombra la tienda** ("Disponible en Ukun Online"). Sigue siendo la lectura paralela de siempre: si falla, la nota discreta ocupa su sitio y la ficha no se cae.
- **Al pie, contenido secundario**: los demás productos del mismo tipo, con la misma tarjeta del listado. Es secundario de verdad — si esa lectura falla o el tipo no tiene más productos, **la sección entera desaparece**; un mensaje de error sobre "también te puede interesar" es ruido encima de una ficha que funciona.
- **Nada de reclamos inventados** (envíos, devoluciones, garantías): si no hay dato en el contrato que lo respalde, no se escribe en la pantalla.

Utilidades que esto añade al tema: `.product-photo` (+ `--empty`), `.product-zoom` (`__trigger`, `__badge`, `__lens`, `__panel`), `.availability-block` (+ `--out`), `.product-viewer` (`__frame`, `__image`, `__controls`) y `.product-description`. Los únicos `style` inline son valores dinámicos: la posición de la lente y el factor de zoom.

## Formularios: react-hook-form + zod

Cada formulario define su esquema `zod` una vez; ese esquema valida en el cliente **y** sus tipos se derivan para el payload que se envía. Los errores de validación del servidor (`code: VALIDATION_FAILED` con extensión `errors: [{field, message}]`, ver `docs/12-error-contract.md`) se mapean a los mismos campos del formulario — un solo sitio donde se pinta un error de campo, venga de donde venga.

## Tipos: generados, no escritos a mano

`types/api.d.ts` se genera con `openapi-typescript` a partir de `docs/api/openapi/ukun-api.yaml` (ver el `local_resource` del Tiltfile en `06-entorno-local-tilt.md`). Si el backend cambia un contrato, el tipo cambia solo y TypeScript señala cada punto del frontend que hay que actualizar — así es como el check de deriva de OpenAPI (`04-roadmap.md`) se convierte en errores de compilación, no en sorpresas en producción.

## El navegador habla siempre con el API real

**No hay modo mock en el navegador.** El entorno de desarrollo es `tilt up` con `ukun-api` levantado, y el dev server de Vite le lleva `/api` y `/media` por proxy (`VITE_API_URL` vacía = mismo origen, igual que en producción tras el gateway). No existe interruptor tipo `VITE_ENABLE_MSW`, ni service worker registrado, ni handlers generados del OpenAPI para desarrollar: eran un segundo backend que hay que mantener al día para que mienta más despacio, y ahora que el API existe solo servían para que un fallo real —una consulta mal formada, una cabecera CSRF que falta, una latencia— se descubriese tarde.

Los **únicos** mocks del frontend son dobles HTTP de MSW en `src/test/msw/`, cargados por el setup de Vitest y por nada más. Están tipados con los mismos `types/api.d.ts`, así que un cambio de contrato también rompe su compilación. Ver `13-testing.md` §2 para por qué el doble vive en la frontera HTTP y no en axios.

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
| Filtros | `tune` | Quitar / cerrar | `close` |
| Avanzar / ir a | `arrow_forward` | Ampliar imagen | `zoom_in` |
| Reducir imagen | `zoom_out` | | |

Ampliar el mapa es añadir una fila aquí, no elegir un icono ad-hoc dentro de una pantalla.

## Estados que toda pantalla de datos debe cubrir

Carga · vacío (p. ej. "tu carrito está vacío", que hoy sí existe en `carrito.html` pero no en todas las pantallas) · error (con el `code` del `ProblemDetail` — registro completo en `docs/12-error-contract.md` — resuelto vía `t('errors:CODE', {...})`, nunca mostrando `detail` crudo, ver ADR-0019) · éxito. Se implementan una vez en los componentes base y se reutilizan — no se improvisa un `{loading && <p>Cargando...</p>}` por pantalla.

Los cuatro juntos son `<QueryBoundary query={...} data={...} empty={...}>`, que recibe los datos ya resueltos —una lista aplanada, si la consulta es `useInfiniteQuery`— y entrega el caso de éxito por `children`. Una regla que no es negociable y vive dentro del componente: **el estado de error solo sustituye a la pantalla cuando no hay nada que enseñar**. Si una lectura falla después de que parte de sus datos ya haya llegado (el siguiente bloque del catálogo), lo que hay se queda y el fallo se cuenta junto a lo que intentó cargar más — tirar 48 productos para anunciar el 49 es peor que el propio fallo. `LoadingState`, `EmptyState` y `ErrorState` siguen existiendo sueltos para el caso puntual que no es una lista (el error del filtro de tipos en el sidebar del catálogo).

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
