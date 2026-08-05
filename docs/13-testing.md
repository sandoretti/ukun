# Testing: cómo se escribe un test en este repo

> **Fuente de verdad de:** nomenclatura, estilo de aserción, datos de prueba, Testcontainers y el reparto por fase de CI.
> **Léelo si:** vas a escribir o revisar cualquier test, backend o frontend.
> **No decide:** qué se prueba y con qué herramienta — esa es la estrategia de `03-analisis-y-patrones.md` §3.

`03-analisis-y-patrones.md` §3 fija la **estrategia** (qué se prueba, con qué herramienta, y por qué) — no se duplica aquí. Este documento es el «cómo se escribe» un test concreto: nomenclatura, estilo de aserción, gestión de datos y fixtures, y qué corre en cada fase de CI. La skill `spring-api` enlaza aquí en su sección de testing; este documento es la fuente completa.

## 1. Backend

### Nomenclatura y organización
- `*Test` — unitario, corre en `surefire`. `*IT` — integración (Testcontainers, `@WebMvcTest` con contexto real, etc.), corre en `failsafe`. La distinción no es cosmética: `surefire` corre en cada build local, `failsafe` solo en CI y antes de un push, porque levanta contenedores.
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
Contenedor Postgres **singleton, reutilizado entre clases** vía una `AbstractIntegrationTest` con `@ServiceConnection` (Boot 4, dos líneas de configuración) — nunca un contenedor por clase de test, que multiplica el tiempo de build sin ganar aislamiento real. Limpieza de datos entre tests por truncado ordenado de tablas (respetando FK), nunca `@DirtiesContext` (recrea el contexto de Spring entero por test, coste desproporcionado).

Nunca H2, tampoco como dependencia transitoria de test — `03-analisis-y-patrones.md` §3 ya explica por qué (claves compuestas con `@MapsId`, `Specification` dinámicas y `SELECT ... FOR UPDATE` no se comportan igual).

### Prohibiciones explícitas
- `Thread.sleep` para sincronizar hilos — usa Awaitility (el test de concurrencia de sobreventa, `03` §3, es el caso de uso que lo exige).
- `@SpringBootTest` cuando `@WebMvcTest`/`@DataJpaTest` bastan — el contexto completo es el último recurso, no el primero.
- Aserciones sobre el contenido literal de un mensaje de log — acopla el test a texto que puede cambiar sin que el comportamiento cambie; si hace falta verificar que algo se logueó, se verifica el evento de negocio (`docs/11-observabilidad-y-logging.md`), no la cadena de texto.

### Verificación mecánica del N+1
Test de persistencia con contador de sentencias (Hibernate `Statistics.getPrepareStatementCount()` o `datasource-proxy`) que falla si un listado emite más sentencias SQL de las declaradas como esperadas. Aplica a las cuatro consultas del defecto #12 (`03-analisis-y-patrones.md` §2, "N+1 y paginación") y a `AvailabilityQueryService` (`docs/14-modules.md` §3) — el cruce entre módulos es exactamente donde un N+1 nuevo podría colarse sin que nadie lo note hasta producción.

## 2. Frontend

- **Vitest + React Testing Library**, alineado con la versión de Vite del proyecto (`05-stack-y-versiones.md`).
- **`getByRole`/`getByLabelText` sobre `getByTestId`** — un test que consulta por rol/etiqueta verifica accesibilidad de paso; uno que consulta por `data-testid` no verifica nada que un lector de pantalla necesite.
- **`userEvent` sobre `fireEvent`** — simula la secuencia real de eventos del navegador (focus, keydown, keyup, ...) en vez de disparar un único evento sintético.
- **MSW con los mismos handlers que el modo dev**, nunca un segundo juego de mocks mantenido aparte para tests — si el mock de dev y el de test divergen, el test deja de decir nada sobre el comportamiento real contra el que desarrolla el equipo.
- **Un test por estado de la trilogía** carga/vacío/error de cada componente de `shared/ui/` (`07-frontend-ux-ui.md`) — no basta el camino feliz.
- **Test de paridad de traducciones** (ADR-0019 §"Test de cobertura de traducción"): claves de `es/*.json` y `en/*.json` coinciden, y todo `code` de `docs/12-error-contract.md` tiene entrada en `errors.json` de ambos idiomas.

## 3. Reparto por fase de CI

| Fase | Qué corre | Cuándo |
|---|---|---|
| `surefire` (unit) | `*Test` backend — sin Testcontainers, sin contexto de Spring salvo `@WebMvcTest`/`@DataJpaTest` puntual | cada build local y cada push |
| `failsafe` (integration) | `*IT` backend — Testcontainers, `@SpringBootTest` de los 4 recorridos end-to-end (`03` §3) | CI, antes de merge |
| Vitest | componentes y hooks del frontend, incluida paridad de traducciones | cada build local y cada push |
| Playwright | los tres recorridos de `03` §3 (cliente completo, admin crea producto, empleado edita stock) | CI, contra el stack levantado por Tilt |
| Jacoco | cobertura informativa en todo el backend; **umbral obligatorio solo en `orders` e `inventory`** (`03` §3 — es donde está el dinero y la concurrencia) | CI |

Sin umbral global de cobertura — invita a tests basura que solo rellenan líneas sin verificar comportamiento (`03-analisis-y-patrones.md` §3).
