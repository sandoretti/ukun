# Catálogo de skills

Dos clases de skill conviven en esta carpeta. La diferencia importa porque **no tienen el mismo peso**.

## Skills propias (fuente de verdad)

Escritas para este repo, en español, y trazadas a `docs/` y a los ADR. Son la autoridad sobre cómo se escribe código aquí.

| Skill | Cubre |
|---|---|
| `spring-api` | `services/ukun-api` — módulos, DTOs, `ProblemDetail`, concurrencia, autorización |
| `spring-auth` | `services/ukun-auth` — Authorization Server, saga de signup, claims |
| `spring-gateway` | `services/ukun-gateway` — BFF, CSRF, enrutado, cabeceras |
| `liquibase` | Changelogs de cualquier servicio con base propia |
| `react-web` | `apps/ukun-web` |
| `testing` | Tests de backend y frontend |
| `tilt-dev` | Entorno local |
| `slice`, `worktree` | Reparto de trabajo entre sesiones |

## Skills externas (instaladas desde skills.sh)

Instaladas con el CLI [`vercel-labs/skills`](https://github.com/vercel-labs/skills); su origen y su hash están en `skills-lock.json` en la raíz del repo.

```bash
npx skills add <owner/repo> --skill <nombre> --agent claude-code --copy
npx skills update            # trae cambios de upstream y actualiza el lockfile
```

Son siete, repartidas en dos capas según la pista del roadmap a la que sirven.

### Backend (pistas P1 y P2)

| Skill | Origen | Para qué sirve aquí |
|---|---|---|
| `java-springboot` | `github/awesome-copilot` | Repaso general de buenas prácticas de Spring Boot: inyección por constructor, `@ConfigurationProperties`, perfiles, SLF4J parametrizado |
| `springboot-patterns` | `affaan-m/everything-claude-code` | Ejemplos de estructura REST, capa de servicio, paginación, filtros y observabilidad |
| `spring-boot-test-patterns` | `giuseppe-trisciuoglio/developer-kit` | Catálogo amplio de patrones de test por slice, con ocho ficheros de referencia |

### Frontend (pista P3)

| Skill | Origen | Para qué sirve aquí |
|---|---|---|
| `vercel-react-best-practices` | `vercel-labs/agent-skills` | 72 reglas de rendimiento de React, cada una con su fichero y su ejemplo malo/bueno. Lo que de verdad aplica aquí es la categoría de re-renders |
| `vercel-composition-patterns` | `vercel-labs/agent-skills` | Diseño de la API de un componente reutilizable: variantes explícitas en vez de props booleanas, `children` sobre render props, compound components, React 19 sin `forwardRef` |
| `web-design-guidelines` | `vercel-labs/agent-skills` | Revisor de UI ya escrita: accesibilidad, estados de foco, formularios, movimiento, tipografía, contenido largo |
| `tanstack-query-best-practices` | `deckardger/tanstack-agent-skills` | Query keys, caché, invalidación, mutaciones y prefetch de TanStack Query — la parte operativa que `react-web` §2 daba por sabida |

Están instaladas con `--copy`, no con symlink: son ficheros versionados, revisables en un diff.

### Descartada: `react:components` de Stitch

`google-labs-code/stitch-skills` aparece en skills.sh como skill de componentes React. No lo es, y **no se instala**. Es un pipeline de Stitch, la herramienta de diseño de Google, y falla por tres motivos independientes:

- Exige el **MCP de Stitch** (`list_tools`, `[prefix]:get_screen`, `scripts/fetch-stitch.sh`) para bajarse los diseños de un proyecto de Stitch. Aquí no hay ni proyecto ni MCP: la skill no llega a terminar su primera fase.
- Extrae tokens de un `tailwind.config` y obliga a **clases Tailwind con variantes `dark:`** en todos los componentes. ADR-0002 elige react-bootstrap y descarta Tailwind explícitamente.
- Impone `src/components/` + `src/hooks/` + `src/data/mockData.ts`. Aquí la estructura es `features/` + `shared/ui/` (`docs/07-frontend-ux-ui.md`) y los datos de desarrollo son handlers de MSW generados del OpenAPI, no un fichero de mocks escrito a mano.

Lo que se buscaba de ella —cómo se diseña un componente React reutilizable— lo cubre `vercel-composition-patterns` sin ninguna de esas ataduras.

## Regla de precedencia

> **Ante cualquier discrepancia, mandan las skills propias, `docs/` y los ADR.** Las externas son material de apoyo genérico: aportan vocabulario y ejemplos, no deciden nada de este repo.

Son genéricas por construcción. Las de backend se escribieron sin conocer Spring Boot 4.1, Java 25, Spring Modulith ni el registro cerrado de `code`s de error de `docs/12-error-contract.md`; las de frontend, para Next.js y no para un SPA de Vite con react-bootstrap. Aplican donde este repo no se ha pronunciado; donde sí lo ha hecho, se ignoran.

Y por encima de las tres: **código limpio y las reglas duras de `CLAUDE.md`**. Si una skill externa propone un atajo que viola una regla dura (`@Data` en una entidad, `Float` para dinero, una mutación por `GET`), no es un caso de precedencia sino un error a no copiar.

### Conflictos concretos ya identificados

No hace falta redescubrirlos. Un ✗ marca lo que además **no compila** con el stack actual.

**`spring-boot-test-patterns`** — es la que más choca, porque asume Spring Boot 3.x:

| Dice | Aquí |
|---|---|
| ✗ `@MockBean` (en `SKILL.md` y en tres ficheros de `references/`) | Eliminado en Spring Boot 4. Se usa `@MockitoBean` / `@MockitoSpyBean` de `org.springframework.test.context.bean.override.mockito` |
| ✗ `org.testcontainers:junit-jupiter:1.19.0` con la versión fijada a mano | Testcontainers 2.x lo gestiona el BOM de Boot, y los artefactos son `testcontainers-junit-jupiter` / `testcontainers-postgresql`; ninguna versión se declara fuera de `gradle/libs.versions.toml`. El paquete es `org.testcontainers.postgresql`, no `org.testcontainers.containers` |
| `@Testcontainers` + `@Container` para el ciclo de vida | Inicializador estático en `AbstractIntegrationTest`. JUnit para el contenedor al final de **cada** clase, que es exactamente lo que convierte el singleton en un contenedor por clase (issue #11) |
| "Prefiere `@ServiceConnection` sobre `@DynamicPropertySource`" | Al revés, y razonado en el javadoc de `AbstractIntegrationTest`: hacen falta dos bases (contextos `prod` y `demo` de Liquibase) en un mismo contenedor, y `@ServiceConnection` no permite elegir base |
| `postgres:16-alpine`, JDK 17 en el CI de ejemplo | `postgres:17-alpine`, Java 25 (`docs/05-stack-y-versiones.md`) |
| `withReuse(true)` + `TESTCONTAINERS_REUSE_ENABLE` | El singleton es por JVM; no dependemos de reutilización entre ejecuciones |
| Aserciones sin librería fijada y `verify(...)` de Mockito abundante | **AssertJ y solo AssertJ**; mocks **solo en fronteras** (`AccountsPort`, `FileStoragePort`, `Clock`), nunca un repositorio JPA |
| `User.builder()` dentro del test | Object mothers (`ProductMother.sofa()`) |
| No distingue `*Test` de `*IT` | tarea `test` para `*Test` / tarea `integrationTest` para `*IT`, sin mezclar y sin source set aparte |
| Perfiles de Maven (`-Punit-tests`, `-Pintegration-tests`) con `maven-surefire-plugin`/`maven-failsafe-plugin`, y `./mvnw test` en sus ejemplos de CI | El build es Gradle (ADR-0021): `./gradlew test` e `./gradlew integrationTest`, filtrando por nombre de clase desde el convention plugin. No hay perfiles |
| H2 como base en memoria entre las opciones de `references/test-dependencies.md` | **Prohibido**, ni siquiera como dependencia de test (`docs/13-testing.md`) — media docena de tests dependen de `unaccent()`, que H2 no tiene |

Lo que sí sirve de esta skill: la tabla de qué slice usar para cada capa, los objetivos de tiempo por tipo de test, el rechazo de `@DirtiesContext` y el aviso sobre `@TestPropertySource` fragmentando el cacheo de contexto. Todo eso coincide con `docs/13-testing.md`.

**`springboot-patterns`**:

| Dice | Aquí |
|---|---|
| `@ControllerAdvice` que devuelve un `ApiError` propio | `ProblemDetail` (RFC 9457) con `code` estable del registro **cerrado** de `docs/12-error-contract.md` — `spring-api` §5 |
| `MarketResponse.from(...)` / `MarketEntity.from(request)`, factorías estáticas | **MapStruct** (`componentModel = "spring"`, `unmappedTargetPolicy = ERROR`). ADR-0009 revierte explícitamente las factorías estáticas: no es una alternativa de estilo, es una decisión ya tomada y revertida una vez |
| "RFC 7807" | RFC 9457, que la obsoleta. Y `spring.mvc.problemdetails.enabled` no basta: el `code` lo pone el `@RestControllerAdvice` |
| `@ExceptionHandler(Exception.class)` devolviendo texto genérico | Jerarquía `UkunException` con `code`; nunca `null` como retorno de un servicio |
| `@Cacheable`/`@CacheEvict`, `@Async`, Kafka, rate limiting en el API | Sobreingeniería descartada en `docs/03-analisis-y-patrones.md`. Bucket4j sí se usa, pero solo en `ukun-auth` para el límite de intentos de login (`spring-auth` §9) |
| `RequestLoggingFilter` que loguea peticiones a mano | Micrometer para métricas y `BusinessEvents` para eventos de negocio, con nombres del registro **cerrado** de `docs/11-observabilidad-y-logging.md` §3 |
| `Long id` autoincremental como identificador visible | `product.sku` es el identificador de negocio del empleado (ADR-0015) |

Lo que sí sirve: paginación con `Sort`, `@Transactional(readOnly = true)` en consultas, inyección por constructor, dimensionado de HikariCP, y el aviso de seguridad sobre no leer `X-Forwarded-For` sin proxy de confianza.

**`java-springboot`** es la más alineada — estructura por feature, inyección por constructor con campos `private final`, DTOs en la frontera sin exponer entidades, `@ConfigurationProperties`, perfiles, SLF4J parametrizado y BCrypt coinciden con lo que ya manda este repo. Matices:

| Dice | Aquí |
|---|---|
| "Maven o Gradle" | Gradle con Kotlin DSL (ADR-0021), un build agregador en la raíz y los BOM de plataforma **importados** con `platform(...)`, nunca heredados (ADR-0001). Lo compartido entre servicios va en un convention plugin de `buildSrc/`, no en `subprojects {}` |
| "`@Query` o la Criteria API para consultas complejas" | Antes que eso, la escalera de `docs/03-analisis-y-patrones.md` §2: proyección → `@EntityGraph` → fetch join → `default_batch_fetch_size` |
| "Considera Testcontainers" | Obligatorio, y **nunca H2**, ni siquiera como dependencia de test |
| No menciona `@Data`, `BigDecimal` ni mutaciones por `GET` | Las reglas duras de `CLAUDE.md` se aplican encima, siempre |

Las cuatro de frontend comparten un desajuste de fondo: **están escritas para Next.js**, con Server Components, renderizado en servidor y su router. `apps/ukun-web` es un SPA de Vite que habla con el gateway por HTTP; nada de eso existe aquí.

**`vercel-react-best-practices`** — la que más sobra por volumen: de sus 72 reglas, diez son de servidor y no tienen equivalente local.

| Dice | Aquí |
|---|---|
| `client-swr-dedup`: usar SWR para deduplicar peticiones | **TanStack Query** sobre la única instancia de axios de `shared/api/http.ts` (ADR-0010, `react-web` §2). SWR no es dependencia del proyecto y no entra: la deduplicación la da la query key |
| Las diez reglas `server-*` (`server-cache-react`, `server-parallel-fetching`, `server-dedup-props`, `server-serialization`, `server-after-nonblocking`, `server-auth-actions`…) | No aplican: no hay SSR, ni Server Components, ni server actions. El equivalente local de "no encadenes cascadas" es lanzar las queries en paralelo y prefetch por intención |
| `bundle-dynamic-imports` y `bundle-defer-third-party` con `next/dynamic` | `React.lazy` + `Suspense` sobre el code-splitting por ruta de react-router 7. Los namespaces i18n ya se cargan perezosamente por ruta (ADR-0019) |
| Categoría 7 (`js-*`): cachear accesos a propiedades, fusionar `filter`+`map`, `Map` para búsquedas repetidas | Subordinadas al **código limpio** de `CLAUDE.md`. Un listado de veinte productos por página no justifica ofuscar una función legible: se aplican con una medición delante, no por defecto |

Lo que sí sirve, y es la razón de instalarla: la categoría 5 entera —`rerender-derived-state-no-effect` (derivar en el render, no en un efecto), `rerender-lazy-state-init`, `rerender-no-inline-components`, `rerender-dependencies` con dependencias primitivas—, `rendering-conditional-render` (ternario y no `&&`, que pinta un `0` literal cuando el contador vale cero: exactamente el caso del carrito vacío), `bundle-barrel-imports`, que refuerza por otro camino la regla de `docs/07-frontend-ux-ui.md` de que las `features/` no se importen entre sí, y `rendering-content-visibility` para el listado paginado del catálogo.

**`vercel-composition-patterns`** — la más alineada de las cuatro. Dos matices:

| Dice | Aquí |
|---|---|
| `state-lift-state`, `state-context-interface`, `state-decouple-implementation`: subir el estado compartido a un provider con contexto | Vale para estado **de UI** (tema, idioma, sesión, apertura de un modal). El estado **de servidor** nunca va a contexto: va a TanStack Query (`react-web` §2). Un provider que cachea respuestas del API reimplementa peor lo que ya hay |
| `react19-no-forwardref` | Aplica a **nuestros** componentes de `shared/ui/`. react-bootstrap 2.x usa `forwardRef` por dentro, y eso ni se toca ni se envuelve para "arreglarlo" |

Lo que sí sirve, y llena un hueco real: `architecture-avoid-boolean-props` y `patterns-explicit-variants` son la guía que faltaba para pasar de los cuatro componentes base actuales a los seis u ocho que `docs/07-frontend-ux-ui.md` exige antes de construir pantallas, sin que cada uno acabe con seis booleanos encima.

**`web-design-guidelines`** — antes de la tabla, la limitación que la distingue de todas las demás: **su contenido no queda fijado por el hash del lockfile**. El `SKILL.md` instalado son treinta líneas que se bajan las reglas de `vercel-labs/web-interface-guidelines` por red en cada revisión. Si upstream cambia una regla, aquí cambia sin aparecer en ningún diff. Es una limitación conocida, no un defecto a arreglar.

| Dice | Aquí |
|---|---|
| Ejemplos en clases de Tailwind (`focus-visible:ring-*`, `truncate`, `line-clamp-*`, `min-w-0`, `text-pretty`) | La regla se traduce al tema de `shared/theme/bootstrap-overrides.scss`; el nombre de clase **no se copia**. Tailwind no entra (ADR-0002) |
| "Los placeholders acaban en `…`", comillas tipográficas, `&nbsp;` en `10 MB` | Correcto, pero ese texto vive en `i18n/*/*.json` (ADR-0019), no escrito en el JSX. La regla se cumple en el fichero de traducción, no en el componente |
| "La URL refleja el estado: filtros, pestañas, paginación en query params" | El repo no se había pronunciado. Se adopta, y queda escrito en `docs/07-frontend-ux-ui.md` como convención propia — no como préstamo que haya que ir a consultar fuera |

Lo que sí sirve: es el material operativo que le faltaba a la §Accesibilidad de `docs/07-frontend-ux-ui.md`, que enumeraba criterios sin decir cómo se comprueban. `aria-label` en botones de solo icono, `aria-hidden` en iconos decorativos, `aria-live="polite"` en toasts y validación, semántica antes que ARIA, `:focus-visible` en vez de `:focus` y prohibición de `outline: none` sin sustituto, error inline junto al campo con foco al primero al enviar, no bloquear el pegado, `prefers-reduced-motion`, y `tabular-nums` en columnas de precio.

**`tanstack-query-best-practices`**:

| Dice | Aquí |
|---|---|
| Categoría SSR (`ssr-dehydration`, `ssr-client-per-request`, `ssr-stale-time-server`, `ssr-hydration-boundary`) | No aplica: SPA de Vite, un único `QueryClient` en `app/main.tsx` |
| `err-error-boundaries` con `useQueryErrorResetBoundary` como mecanismo de error | El error de una pantalla se pinta con los cuatro estados obligatorios y `t('errors:CODE', {...})` a partir del `code` del `ProblemDetail`, nunca `detail` crudo (ADR-0019, `react-web` §8). El error boundary es la red para lo que se escape, no el camino por defecto |
| `err-fallback-data`: devolver datos de relleno cuando la query falla | No. Un error se muestra como error; rellenarlo lo esconde justo donde `docs/12-error-contract.md` quiere que sea visible y accionable |
| El índice anuncia 38 reglas | Solo **21 tienen fichero** en `rules/`. Las demás existen como línea del índice y nada más: si citas una que no está, no hay nada que leer detrás |

Lo que sí sirve, y es la razón de instalarla ahora que `shared/api/` está todavía vacío: `qk-factory-pattern` y `qk-hierarchical-organization` (factoría de query keys y jerarquía entidad → id → filtros), `cache-invalidation` (invalidar por prefijo y dirigido, no un `invalidateQueries()` a pelo), `perf-select-transform`, y `cache-placeholder-vs-initial`, que es lo que hace que el catálogo pagine sin parpadear.

## Al actualizar

`npx skills update` reescribe el contenido externo desde upstream y puede reintroducir cualquiera de los conflictos de arriba. Después de actualizar, revisa el diff contra esta tabla y corrígela si upstream ha cambiado de postura — igual que con cualquier otra decisión del repo, la tabla y la skill que la enlaza se tocan en el mismo commit.
