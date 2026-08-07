# Frontend: React, UX/UI y buenas prácticas

> **Fuente de verdad de:** tema y paleta, estructura de `apps/ukun-web`, datos de servidor, formularios, iconografía, i18n en pantalla y accesibilidad.
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

## Datos del servidor: TanStack Query, no `useEffect` + `fetch`

Todo dato que viene del API pasa por un hook de TanStack Query (`useQuery`/`useMutation`), nunca por un `fetch` suelto en un `useEffect`. Esto da de fábrica: caché, revalidación, estados `isLoading`/`isError` consistentes, y reintentos — que es exactamente lo que hoy no existe en ningún sitio (el JS actual, `scripts.js`, no hace ni una llamada AJAX; toda la interacción es navegación de servidor).

## Formularios: react-hook-form + zod

Cada formulario define su esquema `zod` una vez; ese esquema valida en el cliente **y** sus tipos se derivan para el payload que se envía. Los errores de validación del servidor (`code: VALIDATION_FAILED` con extensión `errors: [{field, message}]`, ver `docs/12-error-contract.md`) se mapean a los mismos campos del formulario — un solo sitio donde se pinta un error de campo, venga de donde venga.

## Tipos y mocks: generados, no escritos a mano

`types/api.d.ts` se genera con `openapi-typescript` a partir de `docs/api/openapi/ukun-api.yaml` (ver el `local_resource` del Tiltfile en `06-entorno-local-tilt.md`). Los handlers de MSW en `mocks/` responden con esos mismos tipos. Si el backend cambia un contrato, el tipo cambia solo y TypeScript señala cada punto del frontend que hay que actualizar — así es como el check de deriva de OpenAPI (`04-roadmap.md`) se convierte en errores de compilación, no en sorpresas en producción.

## Accesibilidad

No es una pasada final: react-bootstrap ya resuelve buena parte (roles ARIA en modales, dropdowns, tabs), pero cada componente base de `shared/ui/` se revisa explícitamente por: foco visible, orden de tabulación, labels asociados a inputs (el signup actual tiene un formulario largo sin agrupar campos relacionados — se corrige agrupando con `fieldset`/`legend`), y contraste de color suficiente en la paleta elegida. La pasada de a11y de v1.0 (`04-roadmap.md`) es una auditoría con axe/Lighthouse sobre lo ya construido, no el único punto donde se piensa en esto.

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
