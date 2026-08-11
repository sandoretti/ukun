---
name: testing
description: Convenciones de este repo para escribir tests — nomenclatura, AssertJ como única librería de aserciones, object mothers, Testcontainers singleton, mocks solo en fronteras, y el reparto entre las tareas test/integrationTest de Gradle, Vitest y Playwright. Usar al escribir o revisar cualquier test, backend o frontend, en cualquiera de los servicios nuevos.
---

# Convenciones de testing en este repo

Este documento resume `docs/13-testing.md` — léelo entero si vas a diseñar la estrategia de test de una feature nueva; esta skill es el recordatorio rápido al escribir un test concreto. La **estrategia** (qué se prueba y con qué herramienta) vive en `docs/03-analisis-y-patrones.md` §3, no aquí.

## Backend

- `*Test` unitario (tarea `test`) — `*IT` integración con Testcontainers (tarea `integrationTest`). No mezclar. Las dos tandas comparten `src/test/java` y se separan por el **nombre de la clase**, no por su ubicación: no crees `src/integrationTest/java`.
- `@DisplayName` y nombres de método **en inglés** (ADR-0018): `shouldRejectOrderWhenAnyLineLacksStock`, no `testCreateOrder2`.
- **AssertJ y solo AssertJ.** Nunca `assertEquals` de JUnit ni Hamcrest.
- *Given/when/then* en tres bloques separados por línea en blanco, sin comentarios que los etiqueten.
- Un hecho por test; agrupa aserciones del mismo hecho con `satisfies`/`assertSoftly`, no las esparzas en `assertThat` sueltos sin relación.
- Datos de prueba con **object mothers** + builders (`ProductMother.sofa()`). Prohibidos los fixtures compartidos mutables y los `@Sql` con literales dispersos.
- Mocks **solo en fronteras**: `AccountsPort`, `FileStoragePort`, `Clock`. Nunca un repositorio JPA en un test que verifica SQL o transacciones — eso va contra Testcontainers.
- `Clock` inyectado en producción (bean de `shared/time`), y en test **un reloj que se mueve**, no un `Clock.fixed(...)`: la caducidad del intento de checkout (30 min, ADR-0016) no se puede probar con un reloj parado sin reescribir dos columnas por detrás del servicio, que es asertar que la base se deja editar. `MutableClock` (`ukun-api`, `src/test`) arranca en la hora real y solo avanza cuando un test lo dice; de propina, dos escrituras del mismo test comparten instante y reproducen gratis el empate del defecto #23.
- **Un `@TestConfiguration` anidado en un fixture *abstracto* no se registra.** Solo se detecta en la clase que lleva las anotaciones, así que declarado dentro de la clase base se ignora en silencio y las subclases fallan al inyectar. Se saca a una clase propia y se nombra en `@SpringBootTest(classes = X.class)` —Spring Boot la mezcla con la configuración de la aplicación precisamente porque es un `@TestConfiguration`—. Y el bean **no puede llamarse igual** que el de producción: sobrescribir definiciones está desactivado, así que se le da otro nombre y `@Primary`.
- Testcontainers **singleton reutilizado** vía `AbstractIntegrationTest` + `@ServiceConnection`; limpieza por truncado ordenado, nunca `@DirtiesContext`. Nunca H2, ni como dependencia de test.
- Si dos clases necesitan **estados de base incompatibles** (contexto `prod` vs `demo` de Liquibase), una base por estado **dentro del mismo contenedor**, elegida con `@DynamicPropertySource` — `@ServiceConnection` no permite cambiar de base. Nunca un contenedor por clase, y nunca dejar que el orden de ejecución decida qué ve cada test.
- **Lo compartido y caro vive en `AbstractIntegrationTest`, cerrado por la JVM y nunca por un `@AfterAll`**: el contenedor, el `JwksTestServer` único (`useJwks(registry)`, `customerToken`, `employeeToken`, `bearer`), el tope de conexiones de Hikari, el `JdbcTemplate` (`jdbc`, heredado — no lo redeclares) y el par `productIdOf(sku)`/`storeIdOf(name)` para recuperar la fila que sembró un object mother. El `@DynamicPropertySource` sí se queda en cada fixture: la clave de caché de contexto se construye con esos métodos, y uno heredado le daría a dos suites la misma clave —y con ella, la base de la otra—. Un `@AfterAll` sobre algo que comparten varias clases lo cierra bajo los pies de la siguiente, y los contextos que apuntan a él siguen cacheados y vivos.
- **Cada suite nueva es un contexto más, y los contextos no se cierran.** Antes de añadir una clase `*IT` con `@SpringBootTest` propio, mira si encaja en un fixture que ya existe; el síntoma de haberse pasado no aparece en tu clase sino en otra cualquiera, como `FATAL: sorry, too many clients already`.
- Prohibidos: `Thread.sleep` (usa Awaitility), `@SpringBootTest` cuando basta `@WebMvcTest`/`@DataJpaTest`, aserciones sobre el texto literal de un log.
- Test de conteo de sentencias (Hibernate `Statistics`/`datasource-proxy`) para todo listado que deba estar libre de N+1 (`03-analisis-y-patrones.md` §2) y para el cruce `inventory`→`catalog` (`docs/14-modules.md` §3).

## Frontend

- Vitest + React Testing Library.
- `getByRole`/`getByLabelText` sobre `getByTestId`; `userEvent` sobre `fireEvent`.
- MSW **solo dentro de Vitest**, en `src/test/msw/`, y en la frontera HTTP — nunca mockeando axios ni el hook de TanStack Query, y nunca registrando un service worker: el navegador habla siempre con el API real (skill `react-web`).
- `server.listen({ onUnhandledRequest: 'error' })` — una petición sin handler es un fallo del test.
- Un test por estado de la trilogía carga/vacío/error de `shared/ui/`.
- Test de paridad de claves `es/*.json` ↔ `en/*.json`, y de que todo `code` de `docs/12-error-contract.md` tiene entrada en `errors.json` de ambos idiomas (ADR-0019).

## Skill externa de apoyo: `spring-boot-test-patterns`

Instalada desde skills.sh (ver `.claude/skills/README.md`). Su tabla de qué slice usar por capa, los objetivos de tiempo por tipo de test, el rechazo de `@DirtiesContext` y el aviso sobre `@TestPropertySource` coinciden con lo de arriba y son útiles. **Pero asume Spring Boot 3.x**, así que dos cosas suyas ni siquiera compilan aquí:

- `@MockBean` → **eliminado en Spring Boot 4**. Usa `@MockitoBean`/`@MockitoSpyBean` (`org.springframework.test.context.bean.override.mockito`).
- `org.testcontainers:junit-jupiter:1.19.0` con versión a mano → Testcontainers 2.x lo gestiona el BOM de Boot (los artefactos son `testcontainers-junit-jupiter` y `testcontainers-postgresql`), y el paquete es `org.testcontainers.postgresql`. Ninguna dependencia declara versión fuera de `gradle/libs.versions.toml`.

Y tres donde este repo ya decidió lo contrario: `@Testcontainers`/`@Container` (aquí el ciclo de vida es del inicializador estático de `AbstractIntegrationTest`), `@ServiceConnection` por encima de `@DynamicPropertySource` (aquí al revés, porque hacen falta dos bases en un contenedor), y `postgres:16-alpine` (aquí 17). Tabla completa en `.claude/skills/README.md`.

## CI

`./gradlew test` y Vitest en cada push · `./gradlew integrationTest` (Testcontainers) y Playwright (los tres recorridos de `03` §3) antes de merge · Jacoco informativo en todo el backend sobre las dos tandas, **umbral obligatorio solo en `orders` e `inventory`** (90% de línea y 75% de rama por paquete, vía `jacocoTestCoverageVerification`) · sin umbral global de cobertura.

El umbral **excluye `..domain..`**, y no por comodidad: la prohibición de `@Data` obliga a escribir `equals`/`hashCode`/`toString` a mano, ese código pesa más que la lógica y no lo ejercita ningún test de negocio. Contarlo haría que **añadir una entidad correcta rompiera el build**, empujando a escribir tests de `equals`. Si vas a tocar las barras, lee antes `docs/13-testing.md` §3 — llevan las cifras medidas y están por debajo de ellas a propósito.

Detalle completo, ejemplos de código y la tabla de reparto por fase: `docs/13-testing.md`.
