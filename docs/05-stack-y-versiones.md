# Stack y versiones

> **Fuente de verdad de:** qué versión de cada tecnología se usa y la política de actualización.
> **Léelo si:** vas a añadir una dependencia o dudas de qué versión fijar.
> **No decide:** por qué se eligió cada tecnología (eso está en los ADRs).

Versiones verificadas por búsqueda web en agosto de 2026, no de memoria — este ecosistema se mueve rápido y las versiones exactas envejecen. **Antes de fijar una versión en `gradle/libs.versions.toml`/`package.json`, confirma que sigue siendo la última estable**; este documento fija la *línea* (major.minor) a seguir, no un compromiso eterno con el patch.

## Backend

| Pieza | Versión de referencia | Notas |
|---|---|---|
| Java | **25 LTS** (Temurin 25.0.3) | Última LTS disponible en Adoptium a fecha de escritura. Boot 4.1 soporta desde Java 17 hasta 26 — 25 es la LTS recomendada, no la mínima. |
| Spring Boot | **4.1.0** | Sobre Spring Framework 7. Cambio mayor de versión respecto al 3.2.3 actual del legacy — no hay ruta de upgrade in-place, es un proyecto nuevo. |
| Spring Cloud | **2025.1.2 "Oakwood"** | Release train compatible con Boot 4.1.0; trae Spring Cloud Gateway en versión compatible. Importar siempre como BOM (`platform(...)`), nunca heredarlo — ver ADR-0001 y ADR-0021, donde esa distinción deja de poder romperse por accidente. |
| Spring Security / OAuth2 Authorization Server | **Spring Security 7.x**, `spring-security-oauth2-authorization-server` | El Authorization Server dejó de ser un proyecto standalone y vive dentro de Spring Security 7 — un único ciclo de release, no dos dependencias a sincronizar. |
| Liquibase | gestionada por el BOM de Spring Boot (`spring-boot-starter-data-jpa` + `liquibase-core`) | No fijar versión de Liquibase a mano salvo que el BOM se quede corto para una feature concreta. |
| Postgres | **17** | El legacy usa `postgres:14`; el nuevo `compose.yaml` sube a 17. Sin datos que migrar (ver `04-roadmap.md`), no hay fricción de upgrade. |
| Lombok | última compatible con Java 25 en el momento de crear el proyecto | Riesgo conocido: Lombok suele ir por detrás en soporte de JDK nuevos. **Tarea de v0.1, día 1**: "hola mundo" con Boot 4.1 + Lombok + MapStruct + Testcontainers + springdoc sobre JDK 25 antes de construir nada encima. Si Lombok falla, se elimina (records + getters del IDE) — dirección deseable de todos modos en las entidades, ver `03-analisis-y-patrones.md`. |
| MapStruct | **1.6.3** estable (1.7 en beta a fecha de escritura — no fijar una beta) | Mapeo entidad↔DTO, ver ADR-0009 y `03-analisis-y-patrones.md`. Su procesador de anotaciones debe declararse **después** del de Lombok en la configuración `annotationProcessor` — Gradle construye el `-processorpath` en el orden de declaración, y en el orden contrario Lombok no ha generado aún los getters/setters cuando MapStruct los lee. Lo vigila `StackValidationIT`. |
| springdoc-openapi | **v3.0.x** (`springdoc-openapi-starter-webmvc-ui`) | Primera línea con soporte de Spring Framework 7 / Boot 4 y OpenAPI 3.1. Solo activo en el perfil de desarrollo (Swagger UI) — el contrato versionado en `docs/api/openapi/` sigue siendo la fuente de verdad, springdoc alimenta el check de deriva en CI (`03-analisis-y-patrones.md`). |
| Micrometer Tracing / `spring-boot-starter-opentelemetry` | incluido en el BOM de Boot 4.1 | Starter oficial desde Boot 4.0: agrupa la API de OpenTelemetry, el bridge de Micrometer Tracing y los exportadores OTLP sin arrastrar Actuator entero. Entra en v0.5 (gateway↔api↔auth), no antes — ver `11-observabilidad-y-logging.md`. |
| Spring Modulith | **2.1.x** (verificado por búsqueda web en agosto de 2026; la serie 2.x es la que declara compatibilidad con Boot 4.1 — confirmar el patch vigente antes de fijarlo en el catálogo) | Módulos de aplicación + verificación de fronteras (`ApplicationModules.verify()`) en `ukun-api` y `ukun-auth`, ver ADR-0017 y `docs/14-modules.md`. `spring-modulith-starter-test` y `spring-modulith-docs` (para `Documenter`), ambos con alcance `test`. |
| ArchUnit | **1.5.0** (verificado en Maven Central el 2026-08-08; lee hasta class file 71 / Java 27, por encima del 69 que emite este build). `spring-modulith-core` arrastra una versión anterior de forma transitiva; en el classpath de test gana la 1.5.0 por resolución de conflicto, y conviene comprobarlo con `./gradlew :ukun-api:dependencyInsight --dependency archunit --configuration testRuntimeClasspath` si se toca la versión de Modulith | Las siete reglas de `03-analisis-y-patrones.md` §3 que Modulith no cubre (ADR-0017, «qué reemplaza y qué no»). Artefacto `archunit` a secas, no `archunit-junit5`: las reglas se comprueban desde tests JUnit 5 normales con AssertJ, como exige `13-testing.md`. Dos de las siete no son expresables con su API y llevan condición propia — la de `@Data` depende de `services/lombok.config`, y la de logging analiza bytecode con `java.lang.classfile`. |
| WireMock | **4.0 beta** en desarrollo activo, **3.x** como línea estable para fijar hoy | Doble de `AccountsPort` en los tests de la saga de signup (`03-analisis-y-patrones.md`). |
| Awaitility | **4.3.x** | Sincronización del test de concurrencia de sobreventa (dos hilos, `03-analisis-y-patrones.md`), evita `Thread.sleep` en el test más valioso del proyecto. |
| Bucket4j | **8.19.x** | Limitación de intentos de login en `ukun-auth` (`08-identidad-y-seguridad.md`). |

## Frontend

| Pieza | Versión de referencia | Notas |
|---|---|---|
Fijadas en `apps/ukun-web/package.json` el 2026-08-09, con instalación limpia verificada: build, tests, lint y `npm audit` en cero.

| Pieza | Versión de referencia | Notas |
|---|---|---|
| React | **19.2.8** | |
| Vite | **8.2.1** | Usa Rolldown (bundler en Rust) por defecto. |
| `@vitejs/plugin-react` | **6.0.5** | Usa Oxc para el React Refresh transform; ya no depende de Babel. |
| react-bootstrap | **2.10.10** sobre Bootstrap **5.3.8** | Continuidad con el Bootstrap 5.3 ya usado hoy vía CDN — ver decisión en `adr/0002-frontend-react-bootstrap.md`. |
| Node.js | **24 LTS** (24.19.0, npm 11) | Runtime de build (Vite, scripts); no se despliega Node en producción, el build es estático. Fijado en `apps/ukun-web/.node-version`, en `engines` del `package.json` y en `Dockerfile.dev`. Node 20 ya no vale: `jsdom` 30 pide `^22.22.2 \|\| ^24.15.0`. |
| TanStack Query | **5.101.4** | Cache y sincronización de datos del servidor — evita reinventar loading/error/retry a mano en cada pantalla. |
| react-hook-form + zod | **7.85.0** + **zod 4.4.3** (`@hookform/resolvers` 5.7.1) | Formularios + validación; zod también valida las respuestas del API en el cliente si hace falta. El salto a zod 4 se hizo sin coste porque todavía no hay ningún esquema escrito. |
| TypeScript | **5.9.3** — *no* 6 ni 7 | **Es el techo que impone el ecosistema, no una elección conservadora.** `openapi-typescript` 7.13 declara peer `typescript@^5.x`, y `typescript-eslint` 8.66 declara `>=4.8.4 <6.1.0`. Mientras `openapi-typescript` no soporte TS 6, subir rompe la generación de `types/api.d.ts`, que es una regla dura (`07-frontend-ux-ui.md`). Revisar en cada release. |
| openapi-typescript | **7.13.0** | Genera `types/api.d.ts` desde `docs/api/openapi/ukun-api.yaml` — nunca se escriben tipos de API a mano. Es quien fija el techo de TypeScript. |
| MSW (Mock Service Worker) | **2.15.0** | **Solo dependencia de test** (`msw/node` en Vitest, ver `13-testing.md` §2). No se registra service worker: el navegador habla siempre con el API real (`07-frontend-ux-ui.md`). |
| axios | **1.19.0** | Cliente HTTP, ver ADR-0010 — interceptores para `XSRF-TOKEN` y traducción de `ProblemDetail`. |
| react-router | **7.18.2** | Enrutado del SPA. Node 20+, React 19+, ESM-only — coherente con el resto del stack de este documento. |
| material-symbols | **0.45.10** | Iconografía, ver `07-frontend-ux-ui.md`. Fuente variable autohospedada, nunca CDN. |
| sass | **1.102.0** | Overrides del tema sobre las variables de Bootstrap 5.3 (`07-frontend-ux-ui.md`). `@import` está deprecado y desaparece en sass 3: el tema usa `@use "bootstrap/scss/bootstrap" with (...)`. |
| Vitest + React Testing Library | **4.1.10** + **16.3.2** (jsdom 30) | Testing de componentes y hooks (`03-analisis-y-patrones.md`, `07-frontend-ux-ui.md`) — capa que Playwright no cubre de forma barata. |
| ESLint | **10.8.1** (`typescript-eslint` 8.66, `eslint-plugin-react-hooks` 7.1) | Incluye la regla `i18next/no-literal-string` que rompe el build ante un literal en JSX (ADR-0019). |
| react-i18next + i18next-browser-languagedetector | **17.0.11** + **8.2.1**, sobre i18next **26.3.6** | i18n del frontend, ver ADR-0019. Namespaces por feature, `errors.json` indexado por el `code` de `docs/12-error-contract.md`. react-i18next 17 exige i18next ≥ 26.2. |

Un `override` de `js-yaml` a `^4.3.1` en el `package.json` cierra CVE-2026-59870, que llega de forma transitiva por `openapi-typescript` → `@redocly/openapi-core`. Se quita en cuanto Redocly publique una versión que ya no arrastre la vulnerable.

## Build

Antes de esta tabla, la versión de cada plugin del build vivía solo en el `pom.xml` y no estaba documentada en ninguna parte. Ver ADR-0021 para por qué el build es Gradle.

| Pieza | Versión de referencia | Notas |
|---|---|---|
| Gradle | **9.7.0** (verificada contra `services.gradle.org` el 2026-08-09) | Java 25 está soportado desde 9.1.0. El wrapper se versiona y `gradle-wrapper.properties` fija el `distributionSha256Sum`: la distribución que se descarga es verificable, no "la que responda la URL". |
| Kotlin DSL | la que trae Gradle | No se fija aparte: el `kotlin-dsl` de `buildSrc` usa la versión embebida en el Gradle del wrapper. Fijarla a mano es la forma habitual de romper `buildSrc` en la siguiente subida de Gradle. |
| Plugin de Spring Boot (`org.springframework.boot`) | la misma que Spring Boot (**4.1.0**), desde `[plugins]` del catálogo | Requiere Gradle 8.14+ o 9.x. Aporta `bootJar` y el `-parameters` del compilador; la gestión de dependencias **no** viene con él, se importa con `platform(...)`. |
| `com.adarshr.test-logger` | **4.0.0** | Salida legible de las dos tandas de test. Es de 2023 y anterior a Gradle 9 — se verificó que funciona sobre 9.7.0; si una subida de Gradle lo rompe, se sustituye por el bloque `testLogging` nativo y no se busca reemplazo. |
| Jacoco | el que trae el plugin `jacoco` de Gradle | Cobertura informativa, sin umbral global. Ver `13-testing.md` §3. |
| Java toolchain | **25** | Fijado en el convention plugin, no en el `JAVA_HOME` del host: Gradle localiza el JDK 25 o lo descarga. Es lo que hace tolerable que el ciclo de Tilt compile en el host (ADR-0021). |

## Infraestructura de desarrollo

| Pieza | Versión de referencia | Notas |
|---|---|---|
| Tilt | última estable | Instalación de usuario, sin root — ver `06-entorno-local-tilt.md`. |
| JDK en el host | cualquiera que arranque Gradle (9.7 admite de 17 a 26) | Requisito **nuevo** desde ADR-0021: el ciclo de recompilación de Tilt invoca `./gradlew` en el host para conservar el daemon. El toolchain se encarga de que se compile con 25 aunque el host tenga otro. |
| Docker / Docker Compose | la que ya tienes instalada | Sin cambios de versión requeridos por este plan. |

## Política de actualización

- Las versiones de este documento se revisan **al empezar cada release** de `04-roadmap.md`, no continuamente — evita que "actualizar dependencias" se convierta en una tarea infinita paralela al refactor.
- Un salto de versión mayor (p. ej. React 19→20, Spring Boot 4.1→5) durante el refactor se trata como una decisión de producto, con su propio ADR — no se cuela dentro de un release que ya tiene su propio objetivo.
- Dependabot/Renovate se activa en v1.0 junto con el resto de CI (ver `04-roadmap.md`), no antes — antes de tener CI, un PR automático de actualización no se puede verificar y es ruido.
