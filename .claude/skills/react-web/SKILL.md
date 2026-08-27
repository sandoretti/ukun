---
name: react-web
description: Convenciones de este repo para el frontend (apps/ukun-web) — React 19 + Vite 8 + react-bootstrap, estructura por feature, TanStack Query, react-hook-form + zod, tipos generados del OpenAPI, accesibilidad y estados de carga/vacío/error. Usar al implementar o revisar cualquier pantalla o componente.
---

# Convenciones de `apps/ukun-web`

Lee `docs/07-frontend-ux-ui.md` antes de construir una pantalla nueva — este skill resume las reglas operativas, el documento explica el porqué y el criterio de "buen UX/UI" acotado.

## Estructura

```
src/
├── app/            router, providers (QueryClient, tema, i18n), los tres layouts
├── features/       una carpeta por dominio: catalog, cart, orders, profile, auth, backoffice, inventory
├── shared/ui/       componentes base envueltos sobre react-bootstrap
├── shared/api/       cliente HTTP + hooks de TanStack Query
├── i18n/            namespaces de react-i18next: common, catalog, cart, checkout, profile, backoffice, errors
├── test/msw/        dobles HTTP de Vitest — NUNCA los ve el navegador
└── types/api.d.ts   generado — NUNCA editar a mano
```

Nombres de carpeta en inglés (ADR-0018), alineados con los módulos de Spring Modulith del backend donde aplica (`catalog`, `cart`, `orders`, `inventory` — ADR-0017). Las carpetas de `features/` no se importan entre sí — si dos features necesitan lo mismo, sube a `shared/`. `app/` sí puede bajar a una feature: es la capa que las compone.

**Tres layouts, y una pantalla nueva elige uno**: `AppLayout` (la tienda), `AuthLayout` (entrar y darse de alta) y `BackofficeLayout` (rail lateral por rol, sin barra ni pie de tienda). Ninguna pantalla del back-office trae su propio `<Container>` ni sus propias guardas: el ancho y el rol los pone el cascarón, y lo que el rail ofrece sale de la lista única `features/backoffice/sections.ts`. Detalle y porqué en `docs/07-frontend-ux-ui.md` §«El back-office tiene cascarón propio».

## Reglas duras

1. **react-bootstrap, no Bootstrap por CDN ni CSS a mano por pantalla.** `bootstrap` + `react-bootstrap` como dependencias npm. Un único tema centralizado (ver ADR-0002) — nunca un color o espaciado hardcodeado dentro de una pantalla. Paleta fijada: `--bs-primary #456e0f` (6.02:1 AA), `--bs-primary-hover #365a08`, `--bs-accent #5d9d0b` (solo decorativo, 3.34:1 insuficiente para texto), `--bs-primary-bg #E9F5D8`. Ningún token con ratio < 4.5:1 va en texto ni en botón con texto encima.
2. **Todo dato del servidor pasa por TanStack Query** (`useQuery`/`useMutation`) sobre una única instancia de **axios** en `shared/api/http.ts` (ver ADR-0010: interceptores de `XSRF-TOKEN` y de `401`, traducción de `ProblemDetail`). Nunca un `fetch` suelto en `useEffect`, ni una instancia de axios distinta por feature, ni **SWR** — que es lo que proponen las skills externas de React y aquí está descartado por ADR-0010, no por gusto.
3. **Las query keys salen de una factoría por feature**, nunca de un array literal escrito dentro de un componente. Vive en `features/<x>/api/`, con jerarquía entidad → lista/detalle → filtros, de forma que invalidar sea dirigido y por prefijo:
   ```ts
   export const productKeys = {
     all: ['products'] as const,
     lists: () => [...productKeys.all, 'list'] as const,
     list: (filters: ProductFilters) => [...productKeys.lists(), filters] as const,
     detail: (sku: string) => [...productKeys.all, 'detail', sku] as const,
   };
   ```
   Un `invalidateQueries()` sin key, o una key escrita a mano en dos sitios, es el bug de caché que aparece cuando llegan carrito y pedidos. En listados paginados, `placeholderData` para no parpadear entre páginas. Si la página se carga por bloques (el catálogo: bloques de 24 hasta un tope de 72 por página, `docs/07-frontend-ux-ui.md`), es `useInfiniteQuery` con `chunks(criterios, página)` en la misma factoría y bajo `lists()` — nunca un array acumulado en `useState`.
4. **Los componentes de `shared/ui/` se componen, no se parametrizan con booleanos.** Envuelven react-bootstrap con variantes con nombre en vez de props booleanas acumuladas (`variant="danger"`, no `<Button danger primary small outline>`), y con componentes distintos cuando lo que cambia es el comportamiento y no solo el aspecto; `children` antes que props `renderX`; y `ref` como prop normal — React 19, sin `forwardRef` en código nuestro. El estado de servidor nunca sube a un contexto: para eso está la regla 2. El contexto queda para estado de UI (tema, idioma, sesión).
5. **Iconos: un único componente `<Icon name="..."/>`** sobre `material-symbols` (npm, autohospedado — nunca CDN ni mezclar con otro set como Bootstrap Icons). Tamaño, peso óptico y color salen del tema, nunca de un `style` inline. Mapa de icono por acción en `docs/07-frontend-ux-ui.md`.
6. **Todo formulario usa react-hook-form + zod, dentro de `<HookForm>`.** El esquema zod es la única fuente de validación de cliente. El elemento `<form>` lo pone siempre `shared/forms/HookForm`, nunca un `<Form>` de react-bootstrap a pelo: es lo que garantiza el `noValidate` y, sobre todo, que el evento de envío llegue entero a `handleSubmit`, que es quien llama a `preventDefault()`. Si el evento se pierde por el camino, el navegador hace **además** su envío nativo y recarga el SPA con los campos en la query string —la petición sale, pero el formulario se vacía sin enseñar la respuesta—, y **jsdom no puede verlo**: no implementa navegación, así que los tests siguen en verde. Pasó de verdad en las dos pantallas de producto (issue #151).
   Los errores del servidor se mapean a los mismos campos que los de zod, siempre como **clave de traducción** y nunca como frase, con los dos helpers de `shared/forms/applyServerErrors.ts`: `applyServerErrors` reparte un `400 VALIDATION_FAILED` entre sus `errors[]`, y `applyDuplicateValue` coloca un `409 DUPLICATE_VALUE` en el campo que nombra su extensión `field` (`docs/12-error-contract.md`). Ninguna pantalla supone qué campo chocó.
7. **`types/api.d.ts` se genera con `openapi-typescript`** desde `docs/api/openapi/*.yaml` — si el tipo que necesitas no existe, el contrato está desactualizado o incompleto, no se escribe el tipo a mano como parche.
8. **Toda pantalla que muestra datos cubre cuatro estados**: carga, vacío, error (`code` del `ProblemDetail` resuelto vía `t('errors:CODE', {...})`, nunca `detail` crudo — ver ADR-0019), éxito. Se implementan en los componentes base de `shared/ui/`, no ad-hoc por pantalla.
9. **En una pantalla de listado, el estado vive en la URL**: filtros, orden y página como query params, no como `useState` local. Es lo que la hace enlazable, compartible y correcta con el botón atrás del navegador. La página se cuenta desde 1 y se omite cuando vale 1, aunque el API cuente desde 0; el avance dentro de una página (cuántos bloques se han cargado) no se escribe en la URL.
10. **Accesibilidad no es una pasada final**: foco visible con `:focus-visible` (nunca `outline: none` sin sustituto), labels asociados a inputs, agrupación de campos relacionados con `fieldset`/`legend` en formularios largos (el signup legacy no lo hace), `aria-label` en botones de solo icono y `aria-hidden` en los decorativos, `aria-live="polite"` en toasts y validación asíncrona, error inline junto al campo con foco al primero al enviar, contraste suficiente en la paleta del tema, `<html lang>` sincronizado con el idioma activo.
11. **i18n con react-i18next** (ver ADR-0019): todo texto de usuario sale de `t(...)`, nunca hardcodeado — regla de ESLint que rompe el build si aparece un literal en JSX. Namespaces por feature; `errors.json` indexado por el `code` del registro de `docs/12-error-contract.md`, en `es` y `en`. Idioma activo: `customer.locale` si hay sesión, `localStorage`/`navigator.language` si es anónimo.
12. **Todo identificador de código en inglés** (componentes, props, hooks, variables) — ver ADR-0018. El texto en pantalla es lo único bilingüe, y vive en `i18n/`, nunca en el propio componente.
13. **Prohibido rediseñar una pantalla ya entregada antes de v1.0** salvo bug de usabilidad real — el pulido general tiene su propio hueco en el roadmap (`docs/04-roadmap.md`, v1.0).
14. **Componentes y hooks se testean con Vitest + React Testing Library**, consultando por rol/label — nunca solo por `data-testid` salvo que no haya alternativa accesible. Playwright cubre los tres recorridos end-to-end; Vitest cubre todo lo demás, incluido el test de paridad de claves de traducción. Convenciones completas: skill `testing` y `docs/13-testing.md`.

## Se desarrolla contra el API real, siempre

**No hay modo mock en el navegador** y no se vuelve a introducir uno. El entorno es `tilt up` con `ukun-api` levantado; `VITE_API_URL` va vacía (mismo origen) y el proxy del dev server lleva `/api` y `/media` al servicio. Si un endpoint que necesitas todavía no existe, la respuesta es implementarlo o acordar el contrato en el OpenAPI —no montar un service worker que lo finja: los handlers generados del OpenAPI, `VITE_ENABLE_MSW` y `public/mockServiceWorker.js` se retiraron al cerrar v0.3 (`docs/04-roadmap.md`) y volver a añadirlos es reabrir una decisión ya tomada.

Los únicos mocks del frontend son dobles HTTP de MSW en `src/test/msw/`, que solo ve Vitest (skill `testing`, `docs/13-testing.md` §2).

## Skills externas de apoyo

Cuatro instaladas desde skills.sh (ver `.claude/skills/README.md`): `vercel-react-best-practices`, `vercel-composition-patterns`, `web-design-guidelines` y `tanstack-query-best-practices`. **Esta skill y `docs/07-frontend-ux-ui.md` mandan sobre las cuatro.** Están escritas para Next.js; aquí hay un SPA de Vite, y eso invalida bloques enteros suyos: todas las reglas `server-*` (RSC, server actions, `React.cache()`), la categoría SSR de TanStack Query, y `next/dynamic`, que aquí es `React.lazy` sobre las rutas.

Tres choques que no hay que redescubrir:

- `client-swr-dedup` propone **SWR**; aquí es TanStack Query sobre la única instancia de axios (regla 2, ADR-0010).
- Los ejemplos de estilado de `web-design-guidelines` vienen en clases de **Tailwind**; la regla se traduce al tema, el nombre de clase no se copia (regla 1, ADR-0002).
- `err-fallback-data` propone rellenar con datos cuando la query falla; aquí un error se pinta como error, con su `code` (regla 8, ADR-0019).

Y una limitación propia de `web-design-guidelines`: baja sus reglas por red en cada revisión, así que su contenido **no queda fijado por el hash** de `skills-lock.json`. Úsala para revisar una pantalla ya escrita, no como fuente de verdad. Tabla completa de conflictos en `.claude/skills/README.md`.
