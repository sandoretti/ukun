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

| Skill | Origen | Para qué sirve aquí |
|---|---|---|
| `java-springboot` | `github/awesome-copilot` | Repaso general de buenas prácticas de Spring Boot: inyección por constructor, `@ConfigurationProperties`, perfiles, SLF4J parametrizado |
| `springboot-patterns` | `affaan-m/everything-claude-code` | Ejemplos de estructura REST, capa de servicio, paginación, filtros y observabilidad |
| `spring-boot-test-patterns` | `giuseppe-trisciuoglio/developer-kit` | Catálogo amplio de patrones de test por slice, con ocho ficheros de referencia |

Están instaladas con `--copy`, no con symlink: son ficheros versionados, revisables en un diff.

## Regla de precedencia

> **Ante cualquier discrepancia, mandan las skills propias, `docs/` y los ADR.** Las externas son material de apoyo genérico: aportan vocabulario y ejemplos, no deciden nada de este repo.

Son genéricas por construcción — se escribieron sin conocer Spring Boot 4.1, Java 25, Spring Modulith ni el registro cerrado de `code`s de error de `docs/12-error-contract.md`. Aplican donde este repo no se ha pronunciado; donde sí lo ha hecho, se ignoran.

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

## Al actualizar

`npx skills update` reescribe el contenido externo desde upstream y puede reintroducir cualquiera de los conflictos de arriba. Después de actualizar, revisa el diff contra esta tabla y corrígela si upstream ha cambiado de postura — igual que con cualquier otra decisión del repo, la tabla y la skill que la enlaza se tocan en el mismo commit.
