---
name: testing
description: Convenciones de este repo para escribir tests — nomenclatura, AssertJ como única librería de aserciones, object mothers, Testcontainers singleton, mocks solo en fronteras, y el reparto entre surefire/failsafe/Vitest/Playwright. Usar al escribir o revisar cualquier test, backend o frontend, en cualquiera de los servicios nuevos.
---

# Convenciones de testing en este repo

Este documento resume `docs/13-testing.md` — léelo entero si vas a diseñar la estrategia de test de una feature nueva; esta skill es el recordatorio rápido al escribir un test concreto. La **estrategia** (qué se prueba y con qué herramienta) vive en `docs/03-analisis-y-patrones.md` §3, no aquí.

## Backend

- `*Test` unitario (surefire) — `*IT` integración con Testcontainers (failsafe). No mezclar.
- `@DisplayName` y nombres de método **en inglés** (ADR-0018): `shouldRejectOrderWhenAnyLineLacksStock`, no `testCreateOrder2`.
- **AssertJ y solo AssertJ.** Nunca `assertEquals` de JUnit ni Hamcrest.
- *Given/when/then* en tres bloques separados por línea en blanco, sin comentarios que los etiqueten.
- Un hecho por test; agrupa aserciones del mismo hecho con `satisfies`/`assertSoftly`, no las esparzas en `assertThat` sueltos sin relación.
- Datos de prueba con **object mothers** + builders (`ProductMother.sofa()`). Prohibidos los fixtures compartidos mutables y los `@Sql` con literales dispersos.
- Mocks **solo en fronteras**: `AccountsPort`, `FileStoragePort`, `Clock`. Nunca un repositorio JPA en un test que verifica SQL o transacciones — eso va contra Testcontainers.
- `Clock` inyectado en producción, `Clock.fixed(...)` en test.
- Testcontainers **singleton reutilizado** vía `AbstractIntegrationTest` + `@ServiceConnection`; limpieza por truncado ordenado, nunca `@DirtiesContext`. Nunca H2, ni como dependencia de test.
- Si dos clases necesitan **estados de base incompatibles** (contexto `prod` vs `demo` de Liquibase), una base por estado **dentro del mismo contenedor**, elegida con `@DynamicPropertySource` — `@ServiceConnection` no permite cambiar de base. Nunca un contenedor por clase, y nunca dejar que el orden de ejecución decida qué ve cada test.
- Prohibidos: `Thread.sleep` (usa Awaitility), `@SpringBootTest` cuando basta `@WebMvcTest`/`@DataJpaTest`, aserciones sobre el texto literal de un log.
- Test de conteo de sentencias (Hibernate `Statistics`/`datasource-proxy`) para todo listado que deba estar libre de N+1 (`03-analisis-y-patrones.md` §2) y para el cruce `inventory`→`catalog` (`docs/14-modules.md` §3).

## Frontend

- Vitest + React Testing Library.
- `getByRole`/`getByLabelText` sobre `getByTestId`; `userEvent` sobre `fireEvent`.
- MSW con los **mismos** handlers que el modo dev — nunca un segundo juego de mocks para test.
- Un test por estado de la trilogía carga/vacío/error de `shared/ui/`.
- Test de paridad de claves `es/*.json` ↔ `en/*.json`, y de que todo `code` de `docs/12-error-contract.md` tiene entrada en `errors.json` de ambos idiomas (ADR-0019).

## Skill externa de apoyo: `spring-boot-test-patterns`

Instalada desde skills.sh (ver `.claude/skills/README.md`). Su tabla de qué slice usar por capa, los objetivos de tiempo por tipo de test, el rechazo de `@DirtiesContext` y el aviso sobre `@TestPropertySource` coinciden con lo de arriba y son útiles. **Pero asume Spring Boot 3.x**, así que dos cosas suyas ni siquiera compilan aquí:

- `@MockBean` → **eliminado en Spring Boot 4**. Usa `@MockitoBean`/`@MockitoSpyBean` (`org.springframework.test.context.bean.override.mockito`).
- `org.testcontainers:junit-jupiter:1.19.0` con `<version>` a mano → Testcontainers 2.x lo gestiona `spring-boot-dependencies`, y el paquete es `org.testcontainers.postgresql`.

Y tres donde este repo ya decidió lo contrario: `@Testcontainers`/`@Container` (aquí el ciclo de vida es del inicializador estático de `AbstractIntegrationTest`), `@ServiceConnection` por encima de `@DynamicPropertySource` (aquí al revés, porque hacen falta dos bases en un contenedor), y `postgres:16-alpine` (aquí 17). Tabla completa en `.claude/skills/README.md`.

## CI

`surefire` y Vitest en cada push · `failsafe` (Testcontainers) y Playwright (los tres recorridos de `03` §3) antes de merge · Jacoco informativo en todo el backend, **umbral obligatorio solo en `orders` e `inventory`** · sin umbral global de cobertura.

Detalle completo, ejemplos de código y la tabla de reparto por fase: `docs/13-testing.md`.
