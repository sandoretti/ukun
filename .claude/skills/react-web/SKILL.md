---
name: react-web
description: Convenciones de este repo para el frontend (apps/ukun-web) — React 19 + Vite 8 + react-bootstrap, estructura por feature, TanStack Query, react-hook-form + zod, tipos generados del OpenAPI, accesibilidad y estados de carga/vacío/error. Usar al implementar o revisar cualquier pantalla o componente.
---

# Convenciones de `apps/ukun-web`

Lee `docs/07-frontend-ux-ui.md` antes de construir una pantalla nueva — este skill resume las reglas operativas, el documento explica el porqué y el criterio de "buen UX/UI" acotado.

## Estructura

```
src/
├── app/            router, providers (QueryClient, tema, i18n), layout raíz
├── features/       una carpeta por dominio: catalog, cart, orders, profile, auth, backoffice, inventory
├── shared/ui/       componentes base envueltos sobre react-bootstrap
├── shared/api/       cliente HTTP + hooks de TanStack Query
├── i18n/            namespaces de react-i18next: common, catalog, cart, checkout, profile, backoffice, errors
├── mocks/           handlers MSW
└── types/api.d.ts   generado — NUNCA editar a mano
```

Nombres de carpeta en inglés (ADR-0018), alineados con los módulos de Spring Modulith del backend donde aplica (`catalog`, `cart`, `orders`, `inventory` — ADR-0017). Las carpetas de `features/` no se importan entre sí — si dos features necesitan lo mismo, sube a `shared/`.

## Reglas duras

1. **react-bootstrap, no Bootstrap por CDN ni CSS a mano por pantalla.** `bootstrap` + `react-bootstrap` como dependencias npm. Un único tema centralizado (ver ADR-0002) — nunca un color o espaciado hardcodeado dentro de una pantalla. Paleta fijada: `--bs-primary #456e0f` (6.02:1 AA), `--bs-primary-hover #365a08`, `--bs-accent #5d9d0b` (solo decorativo, 3.34:1 insuficiente para texto), `--bs-primary-bg #E9F5D8`. Ningún token con ratio < 4.5:1 va en texto ni en botón con texto encima.
2. **Todo dato del servidor pasa por TanStack Query** (`useQuery`/`useMutation`) sobre una única instancia de **axios** en `shared/api/http.ts` (ver ADR-0010: interceptores de `XSRF-TOKEN` y de `401`, traducción de `ProblemDetail`). Nunca un `fetch` suelto en `useEffect`, ni una instancia de axios distinta por feature.
3. **Iconos: un único componente `<Icon name="..."/>`** sobre `material-symbols` (npm, autohospedado — nunca CDN ni mezclar con otro set como Bootstrap Icons). Tamaño, peso óptico y color salen del tema, nunca de un `style` inline. Mapa de icono por acción en `docs/07-frontend-ux-ui.md`.
4. **Todo formulario usa react-hook-form + zod.** El esquema zod es la única fuente de validación de cliente; los errores del servidor (`code: VALIDATION_FAILED`, extensión `errors[].field/message` — `docs/12-error-contract.md`) se mapean a los mismos campos.
5. **`types/api.d.ts` se genera con `openapi-typescript`** desde `docs/api/openapi/*.yaml` — si el tipo que necesitas no existe, el contrato está desactualizado o incompleto, no se escribe el tipo a mano como parche.
6. **Toda pantalla que muestra datos cubre cuatro estados**: carga, vacío, error (`code` del `ProblemDetail` resuelto vía `t('errors:CODE', {...})`, nunca `detail` crudo — ver ADR-0019), éxito. Se implementan en los componentes base de `shared/ui/`, no ad-hoc por pantalla.
7. **Accesibilidad no es una pasada final**: foco visible, labels asociados a inputs, agrupación de campos relacionados con `fieldset`/`legend` en formularios largos (el signup legacy no lo hace), contraste suficiente en la paleta del tema, `<html lang>` sincronizado con el idioma activo.
8. **i18n con react-i18next** (ver ADR-0019): todo texto de usuario sale de `t(...)`, nunca hardcodeado — regla de ESLint que rompe el build si aparece un literal en JSX. Namespaces por feature; `errors.json` indexado por el `code` del registro de `docs/12-error-contract.md`, en `es` y `en`. Idioma activo: `customer.locale` si hay sesión, `localStorage`/`navigator.language` si es anónimo.
9. **Todo identificador de código en inglés** (componentes, props, hooks, variables) — ver ADR-0018. El texto en pantalla es lo único bilingüe, y vive en `i18n/`, nunca en el propio componente.
10. **Prohibido rediseñar una pantalla ya entregada antes de v1.0** salvo bug de usabilidad real — el pulido general tiene su propio hueco en el roadmap (`docs/04-roadmap.md`, v1.0).
11. **Componentes y hooks se testean con Vitest + React Testing Library**, consultando por rol/label — nunca solo por `data-testid` salvo que no haya alternativa accesible. Playwright cubre los tres recorridos end-to-end; Vitest cubre todo lo demás, incluido el test de paridad de claves de traducción. Convenciones completas: skill `testing` y `docs/13-testing.md`.

## Desarrollo contra mocks

Mientras el backend real de una feature no exista, se desarrolla contra los mocks de MSW en `mocks/`, alineados con el mismo OpenAPI que genera los tipos. Cambiar de mocks a backend real es una variable de entorno, no una reescritura.
