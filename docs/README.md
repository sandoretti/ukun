# Documentación de Ukun

Fuente de verdad de la refactorización de Ukun: de dos monolitos Spring Boot + Thymeleaf compartiendo base de datos, a un monolito API + servicio de auth + gateway + frontend React. `CLAUDE.md` en la raíz del repo apunta aquí.

**Premisa que atraviesa todo el plan:** los monolitos legacy **no están en servicio** y sus datos son desechables (ADR-0007). Se conservan sus *requisitos* (`02-requisitos.md`) y se evitan sus *defectos* (`03-analisis-y-patrones.md`), pero no hay convivencia, ni migración de datos, ni renombrado escalonado de esquema: se construye desde cero.

## Cómo navegar

Cada documento arranca con un bloque de cuatro líneas (*fuente de verdad de · léelo si · no decide · idioma*). Si no estás seguro de si un documento te sirve, lee ese bloque y decide en cinco segundos.

| Documento | Para qué sirve |
|---|---|
| [`01-arquitectura.md`](./01-arquitectura.md) | Diagramas: sistema legacy, sistema objetivo, ER, secuencias de login y de checkout |
| [`02-requisitos.md`](./02-requisitos.md) | Qué debe hacer el sistema (RF-01…RF-12), matriz de permisos, requisitos no funcionales |
| [`03-analisis-y-patrones.md`](./03-analisis-y-patrones.md) | Catálogo numerado de defectos del legacy (#1–#23), los patrones que los resuelven, reglas ArchUnit, y lo descartado por sobreingeniería |
| [`04-roadmap.md`](./04-roadmap.md) | Línea temporal de releases (v0.1→v2.0), qué es paralelizable y qué depende de qué |
| [`05-stack-y-versiones.md`](./05-stack-y-versiones.md) | Versión de cada tecnología y política de actualización |
| [`06-entorno-local-tilt.md`](./06-entorno-local-tilt.md) | Cómo levantar y trabajar en el entorno local |
| [`07-frontend-ux-ui.md`](./07-frontend-ux-ui.md) | React, react-bootstrap, tema, formularios, iconos, i18n, accesibilidad |
| [`08-identidad-y-seguridad.md`](./08-identidad-y-seguridad.md) | Frontera api/auth, saga de signup, claims, autorización en tres capas, OWASP |
| [`09-trabajo-con-agentes-ia.md`](./09-trabajo-con-agentes-ia.md) | Cómo repartir este trabajo entre varias sesiones de Claude Code sin que se pisen |
| [`10-modelo-de-datos.md`](./10-modelo-de-datos.md) | Tipos, `CHECK`, índices (uno a uno con su consulta), `ON DELETE`, instantáneas del pedido |
| [`11-observabilidad-y-logging.md`](./11-observabilidad-y-logging.md) | Registro cerrado de eventos, qué nunca se loguea, formato/tracing, tabla `audit_log` |
| [`12-error-contract.md`](./12-error-contract.md) | Registro cerrado de `code`s de error y proceso para añadir uno |
| [`13-testing.md`](./13-testing.md) | Cómo se escribe un test: nomenclatura, AssertJ, object mothers, Testcontainers, fases de CI |
| [`14-modules.md`](./14-modules.md) | Cómo se escribe un módulo de Spring Modulith y qué hacer si `verify()` falla |
| [`adr/`](./adr/) | Decisiones estructurales, una por fichero, con su alternativa descartada |
| [`api/openapi/`](./api/openapi/) | El contrato REST (`ukun-api.yaml`, `ukun-auth.yaml`), en inglés, escrito antes que la implementación |

## Dónde se decide qué

Para no buscar la misma decisión en tres sitios:

| Si buscas... | Está en |
|---|---|
| qué debe hacer una pantalla o un endpoint | `02-requisitos.md` (RF-nn) |
| por qué se tomó una decisión estructural | `adr/` — nunca duplicado en otro sitio |
| el nombre exacto de una tabla, columna o índice | `10-modelo-de-datos.md`, e idioma en ADR-0018 |
| el `code` de un error | `12-error-contract.md` — registro **cerrado** |
| el nombre de un evento de negocio o de log | `11-observabilidad-y-logging.md` §3 — registro **cerrado** |
| la ruta de un endpoint | `docs/api/openapi/*.yaml` — congelado desde SINC-1 |
| cómo se escribe código en una tecnología | `.claude/skills/` (`spring-api`, `react-web`, …) |
| cuándo entra algo | `04-roadmap.md` |
| en qué estado está una tarea | issues y milestones de GitHub — `./scripts/roadmap-status.sh` |

## Cómo se usa esto con Claude Code

Las convenciones específicas de cada tecnología viven como **skills de proyecto** en `.claude/skills/`, no repetidas aquí — invócalas con `/spring-api`, `/react-web`, `/testing`, etc. Esta carpeta `docs/` es el "qué y por qué"; las skills son el "cómo se escribe en este repo".

Para repartir trabajo entre sesiones: `/slice` genera el encargo de una tarea con su contrato, sus límites y su criterio de terminado; `/worktree` aísla dos sesiones simultáneas. El razonamiento detrás de ambas está en `09-trabajo-con-agentes-ia.md`.

## Regla de mantenimiento

Este conjunto de documentos sustituye a `REFACTOR-PLAN.md` (borrado). Si una decisión aquí descrita cambia, actualiza el documento correspondiente, el ADR relacionado **y la skill que la repite**, en el mismo commit. No dupliques la decisión en un tercer sitio: si dos documentos la necesitan, uno la contiene y el otro la enlaza.
