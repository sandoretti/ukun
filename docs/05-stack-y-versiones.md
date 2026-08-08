# Stack y versiones

> **Fuente de verdad de:** qué versión de cada tecnología se usa y la política de actualización.
> **Léelo si:** vas a añadir una dependencia o dudas de qué versión fijar.
> **No decide:** por qué se eligió cada tecnología (eso está en los ADRs).

Versiones verificadas por búsqueda web en agosto de 2026, no de memoria — este ecosistema se mueve rápido y las versiones exactas envejecen. **Antes de fijar una versión en un `pom.xml`/`package.json` nuevo, confirma que sigue siendo la última estable**; este documento fija la *línea* (major.minor) a seguir, no un compromiso eterno con el patch.

## Backend

| Pieza | Versión de referencia | Notas |
|---|---|---|
| Java | **25 LTS** (Temurin 25.0.3) | Última LTS disponible en Adoptium a fecha de escritura. Boot 4.1 soporta desde Java 17 hasta 26 — 25 es la LTS recomendada, no la mínima. |
| Spring Boot | **4.1.0** | Sobre Spring Framework 7. Cambio mayor de versión respecto al 3.2.3 actual del legacy — no hay ruta de upgrade in-place, es un proyecto nuevo. |
| Spring Cloud | **2025.1.2 "Oakwood"** | Release train compatible con Boot 4.1.0; trae Spring Cloud Gateway en versión compatible. Importar siempre como BOM (`<dependencyManagement>`), nunca como `<parent>`. |
| Spring Security / OAuth2 Authorization Server | **Spring Security 7.x**, `spring-security-oauth2-authorization-server` | El Authorization Server dejó de ser un proyecto standalone y vive dentro de Spring Security 7 — un único ciclo de release, no dos dependencias a sincronizar. |
| Liquibase | gestionada por el BOM de Spring Boot (`spring-boot-starter-data-jpa` + `liquibase-core`) | No fijar versión de Liquibase a mano salvo que el BOM se quede corto para una feature concreta. |
| Postgres | **17** | El legacy usa `postgres:14`; el nuevo `compose.yaml` sube a 17. Sin datos que migrar (ver `04-roadmap.md`), no hay fricción de upgrade. |
| Lombok | última compatible con Java 25 en el momento de crear el proyecto | Riesgo conocido: Lombok suele ir por detrás en soporte de JDK nuevos. **Tarea de v0.1, día 1**: "hola mundo" con Boot 4.1 + Lombok + MapStruct + Testcontainers + springdoc sobre JDK 25 antes de construir nada encima. Si Lombok falla, se elimina (records + getters del IDE) — dirección deseable de todos modos en las entidades, ver `03-analisis-y-patrones.md`. |
| MapStruct | **1.6.3** estable (1.7 en beta a fecha de escritura — no fijar una beta) | Mapeo entidad↔DTO, ver ADR-0009 y `03-analisis-y-patrones.md`. Su procesador de anotaciones debe declararse **después** del de Lombok en `annotationProcessorPaths` — en el orden contrario, Lombok no ha generado aún los getters/setters cuando MapStruct los lee. |
| springdoc-openapi | **v3.0.x** (`springdoc-openapi-starter-webmvc-ui`) | Primera línea con soporte de Spring Framework 7 / Boot 4 y OpenAPI 3.1. Solo activo en el perfil de desarrollo (Swagger UI) — el contrato versionado en `docs/api/openapi/` sigue siendo la fuente de verdad, springdoc alimenta el check de deriva en CI (`03-analisis-y-patrones.md`). |
| Micrometer Tracing / `spring-boot-starter-opentelemetry` | incluido en el BOM de Boot 4.1 | Starter oficial desde Boot 4.0: agrupa la API de OpenTelemetry, el bridge de Micrometer Tracing y los exportadores OTLP sin arrastrar Actuator entero. Entra en v0.5 (gateway↔api↔auth), no antes — ver `11-observabilidad-y-logging.md`. |
| Spring Modulith | **2.1.x** (verificado por búsqueda web en agosto de 2026; la serie 2.x es la que declara compatibilidad con Boot 4.1 — confirmar el patch vigente antes de fijarlo en el POM) | Módulos de aplicación + verificación de fronteras (`ApplicationModules.verify()`) en `ukun-api` y `ukun-auth`, ver ADR-0017 y `docs/14-modules.md`. `spring-modulith-starter-test` y `spring-modulith-docs` (para `Documenter`), ambos con alcance `test`. |
| ArchUnit | **1.5.0** (verificado en Maven Central el 2026-08-08; lee hasta class file 71 / Java 27, por encima del 69 que emite este build) | Las siete reglas de `03-analisis-y-patrones.md` §3 que Modulith no cubre (ADR-0017, «qué reemplaza y qué no»). Artefacto `archunit` a secas, no `archunit-junit5`: las reglas se comprueban desde tests JUnit 5 normales con AssertJ, como exige `13-testing.md`. Dos de las siete no son expresables con su API y llevan condición propia — la de `@Data` depende de `services/lombok.config`, y la de logging analiza bytecode con `java.lang.classfile`. |
| WireMock | **4.0 beta** en desarrollo activo, **3.x** como línea estable para fijar hoy | Doble de `AccountsPort` en los tests de la saga de signup (`03-analisis-y-patrones.md`). |
| Awaitility | **4.3.x** | Sincronización del test de concurrencia de sobreventa (dos hilos, `03-analisis-y-patrones.md`), evita `Thread.sleep` en el test más valioso del proyecto. |
| Bucket4j | **8.19.x** | Limitación de intentos de login en `ukun-auth` (`08-identidad-y-seguridad.md`). |

## Frontend

| Pieza | Versión de referencia | Notas |
|---|---|---|
| React | **19.2.8** | |
| Vite | **8.0.9** | Usa Rolldown (bundler en Rust) por defecto. |
| `@vitejs/plugin-react` | **6.x** | Usa Oxc para el React Refresh transform; ya no depende de Babel. |
| react-bootstrap | última 2.x compatible con Bootstrap 5.3 | Continuidad con el Bootstrap 5.3 ya usado hoy vía CDN — ver decisión en `adr/0002-frontend-react-bootstrap.md`. |
| Node.js | **24 LTS** | Runtime de build (Vite, scripts); no se despliega Node en producción, el build es estático. |
| TanStack Query | última 5.x | Cache y sincronización de datos del servidor — evita reinventar loading/error/retry a mano en cada pantalla. |
| react-hook-form + zod | últimas estables | Formularios + validación; zod también valida las respuestas del API en el cliente si hace falta. |
| openapi-typescript | última estable | Genera `types/api.d.ts` desde `docs/api/openapi/ukun-api.yaml` — nunca se escriben tipos de API a mano. |
| MSW (Mock Service Worker) | última estable | Mocks de API para que el frontend avance sin depender del backend real (ver `04-roadmap.md`). |
| axios | última estable | Cliente HTTP, ver ADR-0010 — interceptores para `XSRF-TOKEN` y traducción de `ProblemDetail`. |
| react-router | **7.x** | Enrutado del SPA. Node 22+, Vite 7+, React 19+, ESM-only — coherente con el resto del stack de este documento. |
| material-symbols | última estable | Iconografía, ver `07-frontend-ux-ui.md`. Fuente variable autohospedada, nunca CDN. |
| sass | última estable | Overrides del tema sobre las variables de Bootstrap 5.3 (`07-frontend-ux-ui.md`). |
| Vitest + React Testing Library | última estable, alineada con la versión de Vite del proyecto | Testing de componentes y hooks (`03-analisis-y-patrones.md`, `07-frontend-ux-ui.md`) — capa que Playwright no cubre de forma barata. |
| react-i18next + i18next-browser-languagedetector | **17.x** (verificado por búsqueda web en agosto de 2026 — confirmar el patch vigente antes de fijarlo en el `package.json`) | i18n del frontend, ver ADR-0019. Namespaces por feature, `errors.json` indexado por el `code` de `docs/12-error-contract.md`. |

## Infraestructura de desarrollo

| Pieza | Versión de referencia | Notas |
|---|---|---|
| Tilt | última estable | Instalación de usuario, sin root — ver `06-entorno-local-tilt.md`. |
| Docker / Docker Compose | la que ya tienes instalada | Sin cambios de versión requeridos por este plan. |

## Política de actualización

- Las versiones de este documento se revisan **al empezar cada release** de `04-roadmap.md`, no continuamente — evita que "actualizar dependencias" se convierta en una tarea infinita paralela al refactor.
- Un salto de versión mayor (p. ej. React 19→20, Spring Boot 4.1→5) durante el refactor se trata como una decisión de producto, con su propio ADR — no se cuela dentro de un release que ya tiene su propio objetivo.
- Dependabot/Renovate se activa en v1.0 junto con el resto de CI (ver `04-roadmap.md`), no antes — antes de tener CI, un PR automático de actualización no se puede verificar y es ruido.
