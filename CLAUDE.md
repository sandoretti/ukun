# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

This repo holds **two legacy Spring Boot monoliths** (`ukun-clientes` and `ukun-empleados`), each with its own Thymeleaf server-rendered frontend, both talking directly to the **same** PostgreSQL database, fronted by an nginx reverse proxy.

**Neither monolith is in service.** They are kept only as (a) the executable specification of what the business does, and (b) a catalogue of defects not to repeat. Their data is disposable (ADR-0007). This means: **no coexistence strategy, no strangler fig, no data migration, no staged schema renames** — the new system is built from scratch. What carries over is the *requirements* (`docs/02-requisitos.md`) and the *defect list* (`docs/03-analisis-y-patrones.md` §1), not the code.

The target: a single Spring Boot API monolith (REST, no Thymeleaf) + a React frontend + a dedicated Spring-based auth service (Spring Security's Authorization Server, not Keycloak) + Liquibase per service with its own DB + a Spring Cloud Gateway replacing nginx as a BFF, dropping the current Azure-based deployment. **`docs/` is the source of truth** — start at `docs/README.md`, which has a "dónde se decide qué" table. Every doc opens with a four-line header saying what it owns and what it doesn't. In particular: `docs/01-arquitectura.md` (diagrams), `docs/04-roadmap.md` (releases and what's parallelizable — supersedes the old `REFACTOR-PLAN.md`), `docs/03-analisis-y-patrones.md` (defects and the patterns that fix them), and `docs/adr/` (why each structural decision was made). Tech-specific conventions live as project skills in `.claude/skills/` (`spring-api`, `spring-auth`, `spring-gateway`, `liquibase`, `react-web`, `tilt-dev`, `testing`), plus `slice` (generate a work assignment for another session) and `worktree` (isolate two concurrent sessions).

Three **third-party Spring skills** installed from skills.sh also sit in `.claude/skills/` — `java-springboot`, `springboot-patterns`, `spring-boot-test-patterns` (origins and hashes in `skills-lock.json`). They are generic support material, not authority: **on any conflict the project's own skills, `docs/`, and the ADRs win**, and clean code plus the hard rules below win over all three. `.claude/skills/README.md` is the catalogue, holds the precedence rule, and lists the conflicts already identified — including two that don't even compile on Spring Boot 4 (`@MockBean`, a hand-pinned Testcontainers 1.19.0).

The two Maven modules below are independent Spring Boot apps (no parent multi-module POM, no shared code — entities/DAOs like `Producto`, `Tienda`, `StockProducto`, `Cuenta` are duplicated in both, each mapped to the same underlying tables).

## Progress tracking

The roadmap's **execution state** lives in GitHub issues + milestones, not in a checklist file — one milestone per release, one issue per parallelizable item, labels `pista:P1…P5` matching the tracks in `docs/09-trabajo-con-agentes-ia.md` §1, plus `sinc` (blocking sync points) and `revision-humana` (concurrency/money/identity/schema — never merged unreviewed).

```bash
./scripts/roadmap-status.sh          # progress per release + open issues per track
./scripts/roadmap-status.sh v0.3     # open and closed issues of one release
./scripts/roadmap-status.sh --next   # the release currently in flight
```

`docs/04-roadmap.md` stays the source of truth for *what* and *in what order*; issues only carry state and link back to the RF/ADR/skill they depend on. If the two disagree, the doc wins — fix the issue. Close issues from commits (`closes #42`) so the milestone bar stays current.

## Commands

Each app is built/run independently with its Maven wrapper from its own directory.

```bash
# ukun-clientes (customer-facing app, port 8081, context path "/")
cd ukun-clientes
./mvnw clean install          # build + run tests
./mvnw test                   # run tests only
./mvnw test -Dtest=ClassName  # run a single test class
./mvnw spring-boot:run        # run locally

# ukun-empleados (staff/admin app, port 8082, context path "/empleados")
cd ukun-empleados
./mvnw clean install
./mvnw test
./mvnw test -Dtest=ClassName
./mvnw spring-boot:run
```

Both apps require the datasource env vars below (see `application.properties`); without them, `spring-boot:run` will fail to connect.

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/<db>
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<password>
```

### Legacy compose files (reference only — not the dev environment)

`docker-compose.yml` still brings up `db` + `ukun-clientes` + `ukun-empleados` + `nginx`, but that stack is **not** what you work against: the monoliths are not in service. `docker-compose-sandoretti.yml` and `docker-compose-azure.yml` are old deployment variants; Azure is out of scope (`docs/04-roadmap.md`). All four files get deleted in v0.7.

The legacy Postgres container seeds itself from `database/init.sql` on first startup. That file has no migration tool and only affects fresh databases — it is a **reference for what data existed**, not a schema to evolve. The new services use Liquibase from day one, with a complete baseline per database (skill `liquibase`, `docs/10-modelo-de-datos.md` §8).

### Full stack via Tilt (the dev environment)

```bash
tilt up             # db + ukun-api + ukun-auth + ukun-gateway + ukun-web, live update in ~2-4s
```

Orchestrates `compose.yaml` + `compose.dev.yaml` (non-destructive override) with fast Java recompilation and container restart instead of full image rebuilds — see `docs/06-entorno-local-tilt.md` for the manual (including the port table) and the `tilt-dev` skill for how to extend the `Tiltfile`. `.env` at the repo root supplies the Postgres credentials (gitignored — copy `.env.example`; it used to be committed with a real password, now purged from tracking but still present in history).

## Architecture (legacy, for reference)

**Two independently deployed Spring Boot 3.2 / Java 17 apps**, each following the same layered structure: `controllers` → `services` (interface + `*ServiceImpl`) → `models/dao` (Spring Data JPA repositories) → `models/entity`. Both use Lombok, Thymeleaf, and session-based auth (no Spring Security in either app).

- **`ukun-clientes`** (`dev.sandoretti.ukun.clientes.app`): customer storefront — browsing products (`ProductosController`, `TiendaController`), cart (`CarritoController`), orders (`PedidoController`), profile/signup (`PerfilController`, `SignUpController`). Auth is a custom `LoginService` that queries `Cuenta`/`Cliente` by plaintext email+password match and stores the resulting `Cliente` in the HTTP session; `ClienteInterceptor`/`NoClienteInterceptor` gate routes based on session presence (`WebConfig`).
- **`ukun-empleados`** (`dev.sandoretti.ukun.empleados.app`): staff back-office — managing employees (`EmpleadosController`), products (`ProductosController`), stock (`StockController`). Same session-based pattern via `EmpleadoInterceptor` (applies to all routes except `/login`, static assets) and `AdminInterceptor` (gates `/admin/**` for the `admin` flag on `Empleado`). Runs under context path `/empleados`. Handles file uploads for product images (`UploadFileServiceImpl`, 10MB limit).
- **Shared database, no shared code.** Both apps map JPA entities onto the same Postgres schema (`database/init.sql`): `cuenta` is the base identity table; `cliente` and `empleado` both FK to `cuenta.id` (table-per-type style). `producto`, `tienda`, `stock_tienda` (composite key `producto_id`+`tienda_id`) are shared catalog/inventory data both apps read/write independently — there is no API boundary between them, only direct DB access, which is the main driver for the planned merge into one API service.
- **nginx** (`nginx/nginx.conf`) is a plain reverse proxy: `/` → `ukun-clientes:8081`, `/empleados` → `ukun-empleados:8082`. No routing logic beyond path prefix.
- Uploaded/static resources (`resources/`) are bind-mounted into both containers at `/opt/resources`, served via each app's `WebConfig.addResourceHandlers` at `/resources/**`.

## Notable legacy defects (behaviours the new system must NOT reproduce)

- Passwords (`cuenta.contrasenna`) are stored and compared in **plaintext**, capped at `VARCHAR(16)`. `ukun-auth` hashes from its first commit; there is nothing to "fix in place".
- No CSRF/Spring Security — access control is purely session-attribute checks in hand-written `HandlerInterceptor`s.
- `tarjeta` stores the full card number **and CVV** in plaintext — storing the CVV is a hard PCI-DSS violation, not a style issue. See `docs/08-identidad-y-seguridad.md`.
- The order in `Pedido(Cliente)` **shares** the `direccion`/`tarjeta` row with the customer's live profile instead of copying it — editing your profile silently rewrites the shipping address and payment details of every past order. See `docs/10-modelo-de-datos.md` §5 and ADR-0011.
- The full list of concrete defects (race conditions, N+1s, mutating `GET`s, `Float` for money, etc.), each tied to the pattern that fixes it, lives in `docs/03-analisis-y-patrones.md` — don't rediscover these, read that first.

## Target stack (new services only — see `docs/05-stack-y-versiones.md` for the full rationale and update policy)

Java 25 LTS · Spring Boot 4.1.0 (Spring Framework 7) · Spring Cloud 2025.1.2 (Gateway) · Spring Security 7.x with `spring-security-oauth2-authorization-server` · Liquibase · React 19 + Vite 8 + react-bootstrap · Postgres 17. Verify these are still current before pinning a new dependency — this ecosystem moves fast and this list is a snapshot from August 2026.

## Hard rules for new code (enforced by ArchUnit + Spring Modulith `ApplicationModules.verify()` once they exist — see `docs/03-analisis-y-patrones.md`, ADR-0017)

- No `@Data` on JPA entities (it caused a real `StackOverflowError` between `Pedido`/`ProductoPedido` in the legacy code via mutual `equals`/`toString`, and separately leaks passwords/PAN/CVV into logs via `toString()` — see `docs/11-observabilidad-y-logging.md`). Use `@Getter @Setter`, hand-written `equals`/`hashCode` by id.
- DTOs are `record`s mapped with **MapStruct** (`componentModel = "spring"`, `unmappedTargetPolicy = ERROR`), not hand-written static factories — see ADR-0009 for why this reverses an earlier decision in this same repo. `ModelMapper`/`BeanUtils.copyProperties` stay banned.
- Money is `BigDecimal`, never `Float`/`Double`.
- No mutating endpoint on `GET`. Ever.
- No query inside a bean constructor (the legacy `ProductoServiceImpl`/`PedidoServiceImpl` do this looking up `tienda.nombre = 'Ukun Online'` and fail to start if that row is missing).
- Errors are `ProblemDetail` (RFC 9457) with a stable machine-readable `code` from the closed registry in `docs/12-error-contract.md`, not `null` returns or generic flash messages.
- No entity is ever passed as an argument to `log.*(...)`, and no `Request`/`Command` DTO carrying a secret field ships without a hand-written `toString()` that masks it — see `docs/11-observabilidad-y-logging.md`.
- **All new code — Java and TypeScript/React identifiers, tests, logs, the REST contract, and the schema of the two new databases — is in English** (ADR-0018, reversing this repo's earlier "Spanish domain vocabulary" call). Only `docs/`, ADRs, and skills stay in Spanish. User-facing text is bilingual (`es`/`en`) via react-i18next (ADR-0019), never hardcoded in a component. The new schema is born in English: **no `renameTable`/`renameColumn` changesets and no `@Table(name = "producto")`-style translation mappings ever** — if you're writing one, you've copied from an outdated source.
- Two **closed registries**: error `code`s (`docs/12-error-contract.md`) and business event names (`docs/11-observabilidad-y-logging.md` §3). Don't invent a value for either — each has its own process for adding one.
- `ukun-api` and `ukun-auth` are structured as **Spring Modulith** application modules with a verified dependency graph, not just feature packages by convention — see ADR-0017 and `docs/14-modules.md`.
- An identifier arriving in a request (`{storeId}`, an order id) **identifies** a resource; it never **authorises** access to it. Resource ownership is always resolved server-side from `account_id` — see `docs/08-identidad-y-seguridad.md` §4.
