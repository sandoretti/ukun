# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

**This repo no longer holds any legacy code.** It used to hold two Spring Boot monoliths with their own Thymeleaf frontends, talking straight to one shared PostgreSQL database behind an nginx reverse proxy: `ukun-clientes` was deleted in v0.6 once the customer journey reached parity (issue #40), and `ukun-empleados` in v0.7 once the back-office did (SINC-5, issue #50), together with `nginx/`, `database/` and the three legacy compose files. All of it is read from the **`v0.0-legacy`** tag, which is the only place those paths still exist.

**The legacy was never in service during this refactor.** It was kept only as (a) the executable specification of what the business does, and (b) a catalogue of defects not to repeat — and both of those now live in `docs/`, which is why the code could go. Its data was disposable (ADR-0007). This means: **no coexistence strategy, no strangler fig, no data migration, no staged schema renames** — the new system is built from scratch. What carries over is the *requirements* (`docs/02-requisitos.md`) and the *defect list* (`docs/03-analisis-y-patrones.md` §1), not the code.

The target: a single Spring Boot API monolith (REST, no Thymeleaf) + a React frontend + a dedicated Spring-based auth service (Spring Security's Authorization Server, not Keycloak) + Liquibase per service with its own DB + a Spring Cloud Gateway replacing nginx as a BFF, and a new deployment on Azure Container Apps (ADR-0023) replacing the legacy compose-on-Azure one. **`docs/` is the source of truth** — start at `docs/README.md`, which has a "dónde se decide qué" table. Every doc opens with a four-line header saying what it owns and what it doesn't. In particular: `docs/01-arquitectura.md` (diagrams), `docs/04-roadmap.md` (releases and what's parallelizable — supersedes the old `REFACTOR-PLAN.md`), `docs/03-analisis-y-patrones.md` (defects and the patterns that fix them), and `docs/adr/` (why each structural decision was made). Tech-specific conventions live as project skills in `.claude/skills/` (`spring-api`, `spring-auth`, `spring-gateway`, `liquibase`, `react-web`, `tilt-dev`, `testing`), plus `slice` (generate a work assignment for another session), `worktree` (isolate two concurrent sessions), and the two closing ones: `refactor` (behaviour-preserving cleanup pass that cross-checks the new code against what already exists) and `pr` (open the pull request from the repo's template, with the issue's labels deciding whether it auto-closes).

Seven **third-party skills** installed from skills.sh also sit in `.claude/skills/`, in two layers (origins and hashes in `skills-lock.json`): for the backend, `java-springboot`, `springboot-patterns`, `spring-boot-test-patterns`; for the frontend, `vercel-react-best-practices`, `vercel-composition-patterns`, `web-design-guidelines`, `tanstack-query-best-practices`. They are generic support material, not authority: **on any conflict the project's own skills, `docs/`, and the ADRs win**, and clean code plus the hard rules below win over all seven. `.claude/skills/README.md` is the catalogue, holds the precedence rule, and lists the conflicts already identified — including two Spring ones that don't even compile on Spring Boot 4 (`@MockBean`, a hand-pinned Testcontainers 1.19.0), and, on the frontend side, that all four are written for Next.js: every `server-*` rule, the whole SSR category and `next/dynamic` are dead weight in a Vite SPA, and `client-swr-dedup` proposes SWR where ADR-0010 mandates TanStack Query over a single axios instance. Stitch's `react:components` was evaluated and **rejected** — it requires the Stitch MCP and mandates Tailwind, against ADR-0002; the catalogue records why so it is not reopened.

## Progress tracking

The roadmap's **execution state** lives in GitHub issues + milestones, not in a checklist file — one milestone per release, one issue per parallelizable item, labels `pista:P1…P5` matching the tracks in `docs/09-trabajo-con-agentes-ia.md` §1, plus `sinc` (blocking sync points) and `revision-humana` (concurrency/money/identity/schema — never merged unreviewed).

```bash
./scripts/roadmap-status.sh          # progress per release + open issues per track
./scripts/roadmap-status.sh v0.3     # open and closed issues of one release
./scripts/roadmap-status.sh --next   # the release currently in flight
```

`docs/04-roadmap.md` stays the source of truth for *what* and *in what order*; issues only carry state and link back to the RF/ADR/skill they depend on. If the two disagree, the doc wins — fix the issue. Close issues from commits (`closes #42`) so the milestone bar stays current.

## Commands

**One build tool: Gradle, from the repo root**, covering the three services under `services/` plus `test-support/`. It used to be two — the legacy monolith kept its own Maven wrapper so that neither build had to bend for the other — and ADR-0021 records why that was the right call while it lasted; the Maven half went with `ukun-empleados` in v0.7.

### The new services (Gradle, from the repo root)

```bash
./gradlew build                   # the three services: compile + both test rounds + jars
./gradlew :ukun-api:test          # unit tests only (*Test) — no containers
./gradlew :ukun-api:integrationTest             # *IT with Testcontainers, needs Docker
./gradlew :ukun-api:integrationTest --tests '*StackValidationIT'   # one class
./gradlew :ukun-api:bootJar       # -> services/ukun-api/build/libs/app.jar
./gradlew :ukun-api:classes :ukun-api:copyDeps  # what Tilt's live update runs
```

Project names are flat (`:ukun-api`, not `:services:ukun-api`) — `settings.gradle.kts` remaps `projectDir`. Versions live only in `gradle/libs.versions.toml`; what is shared between the three services lives in `buildSrc/src/main/kotlin/ukun-java-conventions.gradle.kts`. `tilt up` needs a JDK on the host (see `docs/06-entorno-local-tilt.md`).

### Where the legacy went

`ukun-empleados/`, `nginx/`, `database/` and the three `docker-compose*.yml` files were deleted in v0.7 (issue #50) once SINC-5 declared back-office parity. They are read from the **`v0.0-legacy`** tag and nothing in this repo builds or runs them. The legacy schema (`database/init.sql`) is likewise historical: since v0.2 the schema of both new databases is Liquibase and nothing else (skill `liquibase`, `docs/10-modelo-de-datos.md` §8).

`resources/` **stays** and is not legacy: `compose.yaml` bind-mounts `resources/uploads` into `ukun-api` — writable since v0.7, because that is where the product photos of RF-10 land and where `GET /media/products/{key}` serves them from — and `resources/logo/` and `resources/fonts/` hold the brand originals `docs/07-frontend-ux-ui.md` derives from. It goes away in v1.1+ with object storage, not before.

### Full stack via Tilt (the dev environment)

```bash
tilt up             # db + ukun-api + ukun-auth + ukun-gateway + ukun-web, live update in ~2-4s
```

Orchestrates `compose.yaml` + `compose.dev.yaml` (non-destructive override) with fast Java recompilation and container restart instead of full image rebuilds — see `docs/06-entorno-local-tilt.md` for the manual (including the port table) and the `tilt-dev` skill for how to extend the `Tiltfile`. `.env` at the repo root supplies the Postgres credentials (gitignored — copy `.env.example`; it used to be committed with a real password, now purged from tracking but still present in history).

### Deployment (Azure Container Apps)

Nothing here runs from a shell by default: the environment is driven by two `workflow_dispatch` workflows, `deploy.yml` and `teardown.yml`, over the Bicep template in `deploy/azure/main.bicep`. Images come from GHCR, tagged by commit SHA, published by the `images` job of `ci.yml` on pushes to `main`.

**The environment is ephemeral on purpose** — the subscription is Azure for Students, whose spending limit *suspends* resources rather than billing them, so leaving it running is what makes the credit disappear. Updating it means running `deploy.yml` again, never `az containerapp update --image`: that is faster and leaves the template out of sync with the environment. Whoever deploys by hand needs `az` (ADR-0023); nothing in the normal path does.

The runbook — the one-time bootstrap, where each variable comes from, how to tear it down, and the failures whose error message does not name their cause — is `docs/15-despliegue.md`. The decision behind it is ADR-0023, and ADR-0024 is why only the gateway still carries `max=1`.

## Architecture of the legacy (historical — read it in the `v0.0-legacy` tag)

None of the paths in this section exist in the working tree any more. It stays because it is what explains the defect list below and why the two apps became one API service.

**Two Spring Boot 3.2 / Java 17 apps**, following a layered structure: `controllers` → `services` (interface + `*ServiceImpl`) → `models/dao` (Spring Data JPA repositories) → `models/entity`. It uses Lombok, Thymeleaf, and session-based auth (no Spring Security).

- **`ukun-empleados`** (`dev.sandoretti.ukun.empleados.app`): staff back-office — managing employees (`EmpleadosController`), products (`ProductosController`), stock (`StockController`). Session-based pattern via `EmpleadoInterceptor` (applies to all routes except `/login`, static assets) and `AdminInterceptor` (gates `/admin/**` for the `admin` flag on `Empleado`). Ran under context path `/empleados`. Handled file uploads for product images (`UploadFileServiceImpl`, 10MB limit). This is the surface v0.7 reached parity with, RF-08 to RF-13.
- **Shared database, no shared code.** The main driver for the merge into one API service. Both mapped JPA entities onto the same Postgres schema (`database/init.sql`): `cuenta` is the base identity table; `cliente` and `empleado` both FK to `cuenta.id` (table-per-type style). `producto`, `tienda`, `stock_tienda` (composite key `producto_id`+`tienda_id`) were catalog/inventory data both apps read/wrote independently, with no API boundary between them, only direct DB access.
- **nginx** (`nginx/nginx.conf`) was a plain reverse proxy: `/` → `ukun-clientes:8081`, `/empleados` → `ukun-empleados:8082`. No routing logic beyond the path prefix — which is exactly why the gateway that replaced it in v0.5 is a BFF and not a proxy (ADR-0003).
- Uploaded/static resources were bind-mounted at `/opt/resources` and served via `WebConfig.addResourceHandlers` at `/resources/**`. Do not confuse that with today's mount: `resources/uploads` is still bind-mounted into `ukun-api`, but it is served by the `storage` module at `/media/products/{key}` (ADR-0017).

## Notable legacy defects (behaviours the new system must NOT reproduce)

Both modules are gone from the tree — `ukun-clientes` in v0.6, `ukun-empleados` in v0.7 — so every class named here is read from the `v0.0-legacy` tag. The defects stand as written: the trace from a defect to the file it was found in is what makes the catalogue checkable, so those paths are kept on purpose rather than tidied away.

- Passwords (`cuenta.contrasenna`) are stored and compared in **plaintext**, capped at `VARCHAR(16)`. `ukun-auth` hashes from its first commit; there is nothing to "fix in place".
- No CSRF/Spring Security — access control is purely session-attribute checks in hand-written `HandlerInterceptor`s.
- `tarjeta` stores the full card number **and CVV** in plaintext — storing the CVV is a hard PCI-DSS violation, not a style issue. See `docs/08-identidad-y-seguridad.md`.
- The order in `Pedido(Cliente)` **shares** the `direccion`/`tarjeta` row with the customer's live profile instead of copying it — editing your profile silently rewrites the shipping address and payment details of every past order. See `docs/10-modelo-de-datos.md` §5 and ADR-0011.
- The full list of concrete defects (race conditions, N+1s, mutating `GET`s, `Float` for money, etc.), each tied to the pattern that fixes it, lives in `docs/03-analisis-y-patrones.md` — don't rediscover these, read that first.

## Target stack (new services only — see `docs/05-stack-y-versiones.md` for the full rationale and update policy)

Java 25 LTS · Spring Boot 4.1.0 (Spring Framework 7) · Spring Cloud 2025.1.2 (Gateway) · Spring Security 7.x with `spring-security-oauth2-authorization-server` · Liquibase · React 19 + Vite 8 + react-bootstrap · Postgres 17. Verify these are still current before pinning a new dependency — this ecosystem moves fast and this list is a snapshot from August 2026.

## Hard rules for new code (enforced by the seven shared ArchUnit rules in `:ukun-test-support`, plus Spring Modulith `ApplicationModules.verify()` in `ukun-api` — see `docs/03-analisis-y-patrones.md`, ADR-0017, ADR-0022)

- No `@Data` on JPA entities (it caused a real `StackOverflowError` between `Pedido`/`ProductoPedido` in the legacy code via mutual `equals`/`toString`, and separately leaks passwords/PAN/CVV into logs via `toString()` — see `docs/11-observabilidad-y-logging.md`). Use `@Getter @Setter`, hand-written `equals`/`hashCode` by id.
- DTOs are `record`s mapped with **MapStruct** (`componentModel = "spring"`, `unmappedTargetPolicy = ERROR`), not hand-written static factories — see ADR-0009 for why this reverses an earlier decision in this same repo. `ModelMapper`/`BeanUtils.copyProperties` stay banned.
- Money is `BigDecimal`, never `Float`/`Double`.
- No mutating endpoint on `GET`. Ever.
- No query inside a bean constructor (the legacy `ProductoServiceImpl`/`PedidoServiceImpl` do this looking up `tienda.nombre = 'Ukun Online'` and fail to start if that row is missing).
- Errors are `ProblemDetail` (RFC 9457) with a stable machine-readable `code` from the closed registry in `docs/12-error-contract.md`, not `null` returns or generic flash messages.
- No entity is ever passed as an argument to `log.*(...)`, and no `Request`/`Command` DTO carrying a secret field ships without a hand-written `toString()` that masks it — see `docs/11-observabilidad-y-logging.md`.
- **All new code — Java and TypeScript/React identifiers, tests, logs, the REST contract, and the schema of the two new databases — is in English** (ADR-0018, reversing this repo's earlier "Spanish domain vocabulary" call). Only `docs/`, ADRs, and skills stay in Spanish. User-facing text is bilingual (`es`/`en`) via react-i18next (ADR-0019), never hardcoded in a component. The new schema is born in English: **no `renameTable`/`renameColumn` changesets and no `@Table(name = "producto")`-style translation mappings ever** — if you're writing one, you've copied from an outdated source.
- Two **closed registries**: error `code`s (`docs/12-error-contract.md`) and business event names (`docs/11-observabilidad-y-logging.md` §3). Don't invent a value for either — each has its own process for adding one.
- `ukun-api` is structured as **Spring Modulith** application modules with a verified dependency graph, not just feature packages by convention — see ADR-0017 and `docs/14-modules.md`. **`ukun-auth` is not**: ADR-0022 flattened it to conventional layers (`domain`/`infra`/`application`/`config`/`security`) because Modulith prepares a monolith for extraction and that service already *is* the extraction. Don't reintroduce modules there without a new ADR.
- An identifier arriving in a request (`{storeId}`, an order id) **identifies** a resource; it never **authorises** access to it. Resource ownership is always resolved server-side from `account_id` — see `docs/08-identidad-y-seguridad.md` §4.
