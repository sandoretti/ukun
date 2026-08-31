# Testing: cómo se escribe un test en este repo

> **Fuente de verdad de:** nomenclatura, estilo de aserción, datos de prueba, Testcontainers y el reparto por fase de CI.
> **Léelo si:** vas a escribir o revisar cualquier test, backend o frontend.
> **No decide:** qué se prueba y con qué herramienta — esa es la estrategia de `03-analisis-y-patrones.md` §3.

`03-analisis-y-patrones.md` §3 fija la **estrategia** (qué se prueba, con qué herramienta, y por qué) — no se duplica aquí. Este documento es el «cómo se escribe» un test concreto: nomenclatura, estilo de aserción, gestión de datos y fixtures, y qué corre en cada fase de CI. La skill `spring-api` enlaza aquí en su sección de testing; este documento es la fuente completa.

## 1. Backend

### Nomenclatura y organización
- `*Test` — unitario, corre en la tarea `test`. `*IT` — integración (Testcontainers, `@WebMvcTest` con contexto real, etc.), corre en la tarea `integrationTest`. La distinción no es cosmética: `test` corre en cada build local, `integrationTest` solo en CI y antes de un push, porque levanta contenedores. Las dos tandas comparten `src/test/java` y se separan **por el nombre de la clase**, no por su ubicación: el filtro está en el convention plugin del build (ADR-0021), no en un source set aparte.
- `@Nested` por escenario cuando un método bajo test tiene varias ramas de comportamiento — agrupa los tests relacionados en vez de repetir el nombre del método bajo test como prefijo de cada uno.
- `@DisplayName` y nombres de método **en inglés** (ADR-0018), describiendo el comportamiento, no el método invocado: `shouldRejectOrderWhenAnyLineLacksStock`, no `testCreateOrder2`.

### Aserciones
**AssertJ y solo AssertJ.** Ni `assertEquals`/`assertTrue` de JUnit, ni Hamcrest — un solo estilo de aserción en todo el repo, sin excepción por módulo o por autor.

Estructura *given / when / then* en tres bloques separados por una línea en blanco, sin comentarios `// given` etiquetando cada uno — la separación visual ya lo dice:

```java
@Test
void shouldRejectOrderWhenAnyLineLacksStock() {
    var order = OrderMother.withLines(lineOf(SOFA, 2), lineOf(LAMP, 10));
    givenStock(SOFA, 5).and(LAMP, 3);

    var thrown = catchThrowable(() -> checkoutService.confirm(order));

    assertThat(thrown)
        .isInstanceOf(InsufficientStockException.class)
        .extracting("sku").isEqualTo(LAMP.sku());
}
```

**Un hecho por test.** Si un escenario produce varias aserciones sobre el mismo hecho (p. ej. "el pedido se rechazó, con el código correcto, y no descontó stock"), se agrupan con `assertThat(x).satisfies(...)` o `assertSoftly(...)` — no cinco `assertThat` sueltos verificando cosas distintas sin relación en el mismo método, que es una señal de que el test prueba más de un comportamiento a la vez.

### Datos de prueba
**Object mothers** (`ProductMother.sofa()`, `OrderMother.withLines(...)`) + builders para variar campos concretos sobre la base que da el mother. Prohibidos: fixtures compartidos mutables entre tests (un `static Product` reutilizado y modificado por distintos tests es una fuente de acoplamiento oculto entre ellos) y `@Sql` con literales de datos dispersos por las clases de test — si un test necesita datos concretos en la base, los inserta él mismo vía repositorio o mother, no vía un script SQL que nadie lee junto al test.

### Mocks solo en fronteras
Se mockean únicamente los puertos que cruzan un límite externo real: `AccountsPort`, `FileStoragePort`, `Clock`. **Nunca** un repositorio JPA en un test cuyo propósito es verificar el comportamiento de una consulta o una transacción — ese test va contra Testcontainers, porque mockear el repositorio ahí no prueba nada (el mock siempre devuelve lo que se le programe, independientemente de si la query real funciona).

`Clock` se inyecta en producción (nunca `LocalDateTime.now()`/`Instant.now()` sueltos en código de dominio) y se fija con `Clock.fixed(...)` en test — necesario para el TTL de 30 minutos del intento de checkout (ADR-0016) y para cualquier lógica de expiración.

### Testcontainers
Contenedor Postgres **singleton, reutilizado entre clases** vía una `AbstractIntegrationTest` con `@ServiceConnection` (Boot 4, dos líneas de configuración) — nunca un contenedor por clase de test, que multiplica el tiempo de build sin ganar aislamiento real.

Excepción cuando dos clases necesitan **estados de base incompatibles** — hoy, el contexto `prod` de Liquibase (esquema a secas) y el `demo` (esquema más datos sembrados). Ahí el singleton se mantiene y lo que cambia es el mecanismo: una base por estado dentro del mismo contenedor, elegida con `@DynamicPropertySource`, porque `@ServiceConnection` fija la base del contenedor y no admite otra. Lo que **no** es aceptable es resolverlo con un contenedor por clase, ni dejar que el orden de ejecución decida qué afirma cada test. Limpieza de datos entre tests por truncado ordenado de tablas (respetando FK), nunca `@DirtiesContext` (recrea el contexto de Spring entero por test, coste desproporcionado).

Nunca H2, tampoco como dependencia transitoria de test — `03-analisis-y-patrones.md` §3 ya explica por qué (claves compuestas con `@MapsId`, `Specification` dinámicas y `SELECT ... FOR UPDATE` no se comportan igual).

### Prohibiciones explícitas
- `Thread.sleep` para sincronizar hilos — usa Awaitility (el test de concurrencia de sobreventa, `03` §3, es el caso de uso que lo exige).
- `@SpringBootTest` cuando `@WebMvcTest`/`@DataJpaTest` bastan — el contexto completo es el último recurso, no el primero.
- Aserciones sobre el contenido literal de un mensaje de log — acopla el test a texto que puede cambiar sin que el comportamiento cambie; si hace falta verificar que algo se logueó, se verifica el evento de negocio (`docs/11-observabilidad-y-logging.md`), no la cadena de texto.
  - **La excepción, y por qué no lo es**: asertar que un secreto **no** aparece en una línea (`AccessLogWebFilterTest`, `EcsMaskedLoggingIT`) no fija el texto, fija su ausencia — el texto puede reescribirse entero sin romper el test, que es justo lo que la regla protege. Es la única forma de comprobar la capa 3 de `docs/11` §4, cuyo modo de fallo es silencioso: sin ella el log de producción sale perfecto y sin enmascarar.

### Verificación mecánica del N+1
Test de persistencia con contador de sentencias (Hibernate `Statistics.getPrepareStatementCount()` o `datasource-proxy`) que falla si un listado emite más sentencias SQL de las declaradas como esperadas. Aplica a las cuatro consultas del defecto #12 (`03-analisis-y-patrones.md` §2, "N+1 y paginación") y a `AvailabilityQueryService` (`docs/14-modules.md` §3) — el cruce entre módulos es exactamente donde un N+1 nuevo podría colarse sin que nadie lo note hasta producción.

## 2. Frontend

- **Vitest + React Testing Library**, alineado con la versión de Vite del proyecto (`05-stack-y-versiones.md`).
- **`getByRole`/`getByLabelText` sobre `getByTestId`** — un test que consulta por rol/etiqueta verifica accesibilidad de paso; uno que consulta por `data-testid` no verifica nada que un lector de pantalla necesite.
- **`userEvent` sobre `fireEvent`** — simula la secuencia real de eventos del navegador (focus, keydown, keyup, ...) en vez de disparar un único evento sintético.
- **MSW en `src/test/msw/`, y solo ahí.** No hay modo mock en el navegador (`07-frontend-ux-ui.md`): en desarrollo el SPA habla con el `ukun-api` que levanta Tilt. El doble existe únicamente dentro de la ejecución de Vitest, porque un test de componente que necesita el stack arriba y una base sembrada deja de ser un test unitario y no corre en CI. Va en la **frontera HTTP** —nunca mockeando axios ni el hook de TanStack Query—: así el test ejercita el cliente entero (interceptores, cabecera CSRF, traducción de `ProblemDetail`) y solo finge lo que hay al otro lado del cable. Los handlers se tipan con `types/api.d.ts`, de modo que un cambio de contrato también los rompe a ellos.
- **`onUnhandledRequest: 'error'`** en el `server.listen(...)` del setup: una petición que ningún handler cubre es un fallo del test, no algo que se deja pasar en silencio.
- **Un test por estado de la trilogía** carga/vacío/error de cada componente de `shared/ui/` (`07-frontend-ux-ui.md`) — no basta el camino feliz.
- **Test de paridad de traducciones** (ADR-0019 §"Test de cobertura de traducción"): claves de `es/*.json` y `en/*.json` coinciden, y todo `code` de `docs/12-error-contract.md` tiene entrada en `errors.json` de ambos idiomas.

## 3. Reparto por fase de CI

| Fase | Qué corre | Cuándo |
|---|---|---|
| `./gradlew test` | `*Test` backend — sin Testcontainers, sin contexto de Spring salvo `@WebMvcTest`/`@DataJpaTest` puntual | cada build local y cada push |
| `./gradlew integrationTest` | `*IT` backend — Testcontainers, `@SpringBootTest` de los 4 recorridos end-to-end (`03` §3) | CI, antes de merge |
| Vitest | componentes y hooks del frontend, la paridad de traducciones y la auditoría de accesibilidad con axe | cada build local y cada push |
| Playwright | los tres recorridos de `03` §3 (cliente completo, admin crea producto, empleado edita stock), con el escaneo de accesibilidad que necesita un navegador | a mano contra el stack de Tilt, y en CI en cada PR desde v1.0 |
| Jacoco (`jacocoTestReport`) | cobertura informativa en todo el backend, sobre las dos tandas | CI |
| Jacoco (`jacocoTestCoverageVerification`) | **umbral obligatorio solo en `orders` e `inventory`** (`03` §3 — es donde está el dinero y la concurrencia), 90% de línea y 75% de rama por paquete | CI |

`./gradlew check` de un servicio corre las dos tandas, el informe de cobertura y la verificación del umbral; `./gradlew build` añade el empaquetado. Para una sola clase, `--tests`:

```bash
./gradlew :ukun-api:test                                  # solo unitarios
./gradlew :ukun-api:integrationTest                       # solo integración (necesita Docker)
./gradlew :ukun-api:integrationTest --tests '*StackValidationIT'
```

Las dos tandas no se solapan nunca: una clase entra en una o en la otra según su nombre, y ninguna corre dos veces. Es comprobable comparando los informes de `build/test-results/test/` y `build/test-results/integrationTest/`.

### La auditoría de accesibilidad corre en dos sitios, y no por duplicar

`07-frontend-ux-ui.md` §Accesibilidad pide una auditoría con axe sobre lo ya construido. Está partida
en dos porque **hay dos preguntas distintas y solo una de ellas se le puede hacer a jsdom**.

| Dónde | Qué comprueba | Por qué ahí |
|---|---|---|
| `npm run test:a11y` (Vitest) | axe sobre las pantallas de los tres recorridos y sobre los tres cascarones, con MSW en la frontera HTTP | Corre sin stack, en segundos, sobre el código de la rama. Es el que se ejecuta mientras se escribe |
| `@axe-core/playwright`, dentro de los tres recorridos | contraste de color, anillo de foco y orden de tabulación | jsdom no maqueta: no hay cajas ni colores compuestos, así que esas tres preguntas no tienen respuesta ahí. **Los dos defectos de más alcance de v1.0 solo se veían aquí** |

Dos reglas que se ganaron a base de equivocarse:

- **Lo que jsdom no puede juzgar se apaga explícitamente** (`src/test/a11y.ts`), no se deja pasar. axe
  devuelve esos casos como `incomplete`, y una auditoría que trata un «no lo sé» como un aprobado es
  peor que no tenerla.
- **Cuidado con la regla `region`**: solo mira contenido *visible*, y en jsdom nada tiene tamaño, así
  que pasa igual con los landmarks puestos que sin ellos. La contención en landmarks se comprueba con
  aserciones de rol (`getByRole('banner')`) en los tests de los cascarones, y con axe en el navegador.
- **El check lleva una guarda contra sí mismo**, como la del registro de errores: un test que
  comprueba que axe *detecta* un incumplimiento conocido. Sin ella, un axe mal configurado o un
  contenedor vacío dan una fila de verdes indistinguible de la de verdad.

El escaneo de navegador crece dentro de los tres recorridos que ya existen —más `test.step`, nunca un
cuarto fichero— porque aprovecha que el recorrido ya ha llegado hasta ahí con sesión, que es la única
forma de auditar las pantallas que la piden.

### Cómo se ejecuta el smoke de Playwright

Vive en `apps/ukun-web/e2e/`, dentro del proyecto npm que ya existe — no hay un segundo `package.json` que mantener. Un recorrido por fichero, y son tres: el de cliente (v0.6) en `customer-journey.spec.ts`, y los dos de back-office (v0.7) en `admin-journey.spec.ts` y `employee-journey.spec.ts`.

```bash
tilt up                       # en otra pestaña: el smoke NO levanta el stack, solo lo usa
cd apps/ukun-web
npx playwright install chromium   # una vez por máquina
npm run test:e2e              # o test:e2e:ui para verlo paso a paso
```

Cuatro decisiones que no son de estilo:

- **Contra el gateway** (`http://localhost:8080`), nunca contra el dev server de Vite: el 5173 lleva `/api` por proxy directamente a `ukun-api` y se salta la sesión y el CSRF, que es justo lo que un recorrido completo tiene que ejercitar (ADR-0003). `E2E_BASE_URL` lo reapunta desde un worktree, donde los puertos van desplazados (`06-entorno-local-tilt.md`).
- **Sin `webServer` y con `globalSetup`**: el stack es de Tilt y el test no lo arranca ni lo para; lo que hace es comprobar que está y fallar diciendo «arranca `tilt up`» en vez de dejar un `ERR_CONNECTION_REFUSED` dentro del primer `goto`.
- **`workers: 1` y `retries: 0`**: el recorrido escribe en la base y consume stock, así que no hay paralelismo que ganar; y un smoke que pasa a la segunda no dice que el recorrido funcione, dice que a veces funciona.
- **Ninguna aserción sobre cantidades absolutas de stock ni sobre un SKU concreto**: el propio smoke las cambia en cada ejecución. Elige del catálogo lo que la pantalla pinta como disponible, y da de alta a una persona nueva por ejecución (correo con marca de tiempo). Los datos que deja se limpian con `npm run test:e2e:cleanup` (el botón `e2e-cleanup` de Tilt, `scripts/e2e-cleanup.sh`), que borra por esos mismos patrones y respeta los datos demo; `db-reset` sigue siendo la opción bruta cuando lo que quieres es también reponer el stock consumido. Las dos son manuales a propósito: un teardown automático se lleva por delante justo lo que hay que mirar cuando el recorrido falla.

Vitest está acotado a `src/` en `vite.config.ts` para que no recoja estos `*.spec.ts`, y `test-results/`, `playwright-report/` y `e2e/` están en el `.dockerignore` del frontend: el contexto de build de su imagen es ese directorio, así que sin esa línea cada ejecución reconstruye —y reinicia— el dev server contra el que está corriendo.

Sin umbral global de cobertura — invita a tests basura que solo rellenan líneas sin verificar comportamiento (`03-analisis-y-patrones.md` §3).

### Qué mide exactamente el umbral de `orders` e `inventory`

La barra se aplica **por paquete** y **excluye `..domain..`**, que no es un atajo sino la consecuencia de otra regla dura de este repo. La prohibición de `@Data` (`03` §3) obliga a que cada `@Entity` escriba a mano `equals`/`hashCode`/`toString`; ese código existe por mandato, no lo ejercita ningún test de negocio, y pesa más que la lógica. Medido: `orders` entero da 83,6% de línea y 33,1% de rama, y descontando esos tres métodos, 96,9% y 80,4%.

Un umbral sobre el módulo completo mediría por tanto **cuántas entidades tiene, no cómo de probadas están**: añadir una entidad escrita según las reglas del repo bajaría la cifra y rompería el build, empujando a escribir tests de `equals` — exactamente los tests basura que el párrafo de arriba prohíbe. Jacoco sabe excluir clases, no métodos, así que el paquete es la exclusión más fina disponible.

Lo que se pierde con ella está medido y es poco: la lógica de agregado de `..domain..` (los invariantes de `CustomerOrder`, el `freeze` y el `hasExpired` de `CheckoutAttempt`) está al 98,1% de línea, y no por casualidad — se ejercita desde los casos de uso, que sí entran en la barra.

Las barras están **por debajo** de lo medido a propósito: son un suelo contra la regresión, no un objetivo. Subirlas hasta rozar la cifra de hoy convierte cualquier refactor honesto en un build rojo. El mecanismo se engancha a `check` en el convention plugin, para que ningún servicio pueda olvidarlo; **qué** se mide lo declara cada servicio, porque hoy solo `ukun-api` tiene módulos con dinero dentro.
