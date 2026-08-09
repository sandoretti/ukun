# Trabajar en paralelo con Claude Code y agentes de IA

> **Fuente de verdad de:** cómo se reparte el trabajo de `04-roadmap.md` entre varias sesiones o agentes sin que se pisen.
> **Léelo si:** vas a lanzar más de una sesión a la vez sobre este repo, o a redactar el encargo de una tarea para otro agente.
> **No decide:** qué hay que hacer (`04-roadmap.md`) ni cómo se escribe el código (skills).
> **Herramientas asociadas:** skill `slice` (genera el encargo de una tarea) y skill `worktree` (aísla dos sesiones simultáneas).

No sustituye el juicio de quien revisa — lo hace más barato de ejercer.

## 1. Las pistas del roadmap son las unidades de reparto

`04-roadmap.md` define cinco pistas (P1 Backend Core, P2 Identidad, P3 Frontend, P4 Infra/DevEx, P5 Calidad). Cada una toca ficheros que las demás no tocan casi nunca:

| Pista | Vive en |
|---|---|
| P1 Backend Core | `services/ukun-api/` |
| P2 Identidad | `services/ukun-auth/` |
| P3 Frontend | `apps/ukun-web/` |
| P4 Infra/DevEx | `Tiltfile`, `compose*.yaml`, `services/*/Dockerfile*`, `.github/`, y el build: `settings.gradle.kts`, `build.gradle.kts`, `gradle/`, `buildSrc/` |
| P5 Calidad | tests dentro de los módulos de las demás — **no** su código de producción |

Esto las hace candidatas naturales a **sesiones o agentes separados**, cada uno con contexto acotado a su pista. Un agente de P3 no necesita leer `PedidoServiceImpl.java`; necesita el OpenAPI y los mocks.

## 2. El contrato es el mecanismo de coordinación, no la memoria compartida

Un agente no lee la mente de otro ni la conversación de otra sesión. Lo que sí lee, si se lo dices, son artefactos versionados en el repo:

- **`docs/api/openapi/*.yaml`** — el contrato entre backend y frontend. Un agente al que se le pide "implementa la pantalla de catálogo" debe recibir la ruta a este fichero, no una descripción verbal de los campos.
- **`docs/adr/`** — el porqué de cada decisión estructural. Antes de pedirle a un agente que toque identidad, señálale `08-identidad-y-seguridad.md` y el ADR correspondiente, para que no reabra una decisión ya tomada.
- **`docs/03-analisis-y-patrones.md`** — las reglas duras y el catálogo numerado de defectos. Un agente que no las conoce las viola por desconocimiento, no por descuido.
- **`docs/12-error-contract.md` y `docs/11-observabilidad-y-logging.md` §3** — dos registros **cerrados** (códigos de error, nombres de evento de negocio). Un agente que no los ve inventa un `code` o un `stock.updated` con otro nombre, y eso rompe el frontend o la auditoría en silencio.
- **Skills de `.claude/skills/`** — las convenciones concretas de cómo se escribe código en cada pista (`spring-api`, `spring-auth`, `spring-gateway`, `liquibase`, `react-web`, `tilt-dev`, `testing`). Invócalas al pedirle a un agente que trabaje en esa pista.
- **`CLAUDE.md`** — se carga siempre; es el resumen que toda sesión nueva ve sin que se lo pidas.

Si vas a lanzar dos agentes a la vez sobre pistas relacionadas (p. ej. P1 y P2 en v0.4), el punto de sincronización explícito del roadmap (`SINC-3` en ese caso) es literalmente el momento en que uno debe esperar a que el otro publique su contrato antes de seguir — no lo lances en paralelo sin haber fijado antes ese contrato.

## 3. Aislar el trabajo con git worktrees

Cuando dos pistas van a tener commits simultáneos y no quieres que un `git status` de una sesión se mezcle con el de otra, un worktree por pista activa:

```bash
git worktree add ../ukun-catalog-api  v0.3-catalog-api
git worktree add ../ukun-catalog-web  v0.3-catalog-web
```

Cada worktree es un directorio de trabajo independiente sobre el mismo repo — dos sesiones de Claude Code operan a la vez sin colisionar en ficheros abiertos ni en el índice de git. La skill **`worktree`** automatiza esto, incluida la parte que a mano se olvida: reasignar los puertos del segundo `tilt up` para que los dos stacks convivan.

No hace falta un worktree por cada tarea pequeña — solo cuando el trabajo es de verdad simultáneo y de más de una sesión larga. Para una tarea de una sola sesión, trabajar sobre el checkout normal es más simple y no añade nada.

## 4. Granularidad: la slice vertical

El roadmap está pensado en slices verticales por una razón: son la unidad que un agente puede completar de principio a fin sin perder de vista el objetivo. Ejemplos con buena granularidad:

- "Implementa `GET /api/stores` y la pantalla de listado de tiendas en React, contra el contrato ya congelado" (una slice de v0.3).
- "Implementa el slice de perfil: `PUT /api/me/profile` + pantalla de edición" (una de las tres slices de v0.6).

Encargos demasiado grandes ("implementa todo v0.6") pierden coherencia interna a mitad de camino porque el propio agente pierde de vista las decisiones que tomó al principio. Pártelos en las slices que el roadmap ya define.

## 5. Qué no delegar sin supervisión directa

- **Decisiones de esquema** (una columna nueva, un cambio de tipo, una FK): revisa el changeset antes de aplicarlo contra cualquier base que no sea completamente desechable. Un agente puede *proponer* el changeset; que se aplique lo decide una persona.
- **Cualquier cosa marcada como riesgo en un ADR** (la saga de signup, el reparto pesimista/optimista de ADR-0008): son los puntos donde una decisión equivocada es cara de deshacer. Revisión humana antes de mergear, no después.
- **Cambios en el contrato después de SINC-1**: tocar `docs/api/openapi/*.yaml` invalida los tipos generados y los mocks de todo el frontend. Es una decisión de coordinación, no una tarea de implementación.
- **Rotación de secretos, force-push, reescritura de historia de git**: siempre con confirmación explícita, nunca como efecto colateral de una tarea más grande.

## 6. Cómo redactar el encargo de una slice

Para no escribirlo a mano cada vez, usa la skill **`slice`**: le das el release y la slice (`/slice v0.3 catálogo backend`) y genera el encargo completo. Lo que genera, y por qué cada parte importa:

> Implementa el endpoint `GET /api/products` (paginado, filtro por nombre y tipo, orden por precio) en `services/ukun-api`, según el contrato de `docs/api/openapi/ukun-api.yaml`.
>
> Convenciones: usa la skill `spring-api` (DTOs record, `@EntityGraph` para evitar el N+1 del defecto #12, `ProblemDetail` con `code` del registro de `docs/12-error-contract.md`). Tests según la skill `testing`.
>
> Reglas de negocio, con su fuente — no las reinventes: disponibilidad es `quantity > 0` en `store_stock`, y un producto sin fila de stock cuenta como no disponible (`docs/02-requisitos.md` RF-01). La búsqueda por nombre va con `unaccent(lower(...))` y todo `Sort` lleva `id` como último criterio (`docs/10-modelo-de-datos.md` §4, defectos #22 y #23).
>
> Límites: toca solo `services/ukun-api/src/main/java/**/catalog/**` y sus tests. **No** toques `services/ukun-auth`, `apps/ukun-web`, ni `docs/api/openapi/` (el contrato está congelado desde SINC-1).
>
> Terminado cuando: el endpoint responde según el contrato, hay un `*IT` con Testcontainers que cubre filtro+orden+paginación, el test de contador de sentencias demuestra que no hay N+1, y `ApplicationModules.verify()` sigue en verde.

Las cinco partes: **contrato** fijo · **convenciones** por skill · **reglas de negocio con su fuente** (evita que el agente las adivine mal) · **límites** de ficheros (lo que más evita que P1 pise trabajo de P2/P3) · **criterio de terminado** verificable.

## 7. `CLAUDE.md` y las skills como memoria compartida entre sesiones

Una sesión nueva no recuerda nada de sesiones anteriores salvo lo que esté escrito en el repo. `CLAUDE.md` y `.claude/skills/` son exactamente eso: el estado que persiste entre sesiones y agentes.

Si en una sesión se descubre una convención que vale la pena fijar, se escribe en la skill relevante o en `CLAUDE.md` **en esa misma sesión**, no se confía en que quien lo decidió se acuerde de repetirlo. Y si lo que cambia es una decisión estructural, va a un ADR nuevo (o a uno que revierte otro, como ADR-0009/0017/0018 ya hacen) — nunca como una edición silenciosa de un documento que otra sesión ya leyó.

**La contrapartida, que este repo ya pagó una vez:** cuando un ADR cambia una convención, hay que propagarlo a `docs/` **y** a las skills en el mismo commit. ADR-0018 fijó el inglés y varios documentos (`08`, `09`, `11`) se quedaron con endpoints, tablas y clases en español, contradiciéndolo — que es exactamente el tipo de incoherencia que un agente copia sin darse cuenta, porque no tiene forma de saber cuál de los dos documentos es el vigente.
