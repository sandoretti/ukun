---
name: slice
description: Genera el encargo completo de una slice vertical del roadmap, listo para pegar en otra sesión o pasar a otro agente — contrato, convenciones, reglas de negocio con su fuente, límites de ficheros y criterio de terminado. Usar cuando se vaya a repartir trabajo entre sesiones, cuando el usuario pida "prepara la tarea de X", o antes de empezar una slice para fijar su alcance.
---

# Generar el encargo de una slice

Un encargo mal acotado es la causa habitual de que un agente reinvente una regla de negocio, invente un `code` de error o toque ficheros de otra pista. Esta skill construye el encargo a partir de lo que ya está decidido en `docs/`, en vez de redactarlo de memoria.

Uso: `/slice <release> <slice>` — p. ej. `/slice v0.3 catálogo backend`, `/slice v0.6 checkout`, `/slice v0.7 stock`.

## Paso 1 — Localiza la slice en el roadmap

Lee la sección del release en `docs/04-roadmap.md`. Cada release lista explícitamente qué es paralelizable; **esas son las slices válidas**. Si lo que te piden no coincide con ninguna:

- Si es más grande que una slice ("todo v0.6"), pártelo y dilo — genera el encargo de la primera y lista las demás.
- Si es más pequeño (una parte de una slice), está bien, pero hereda los mismos límites.
- Si no aparece en ningún release, para y pregunta. No inventes trabajo que el roadmap no contempla.

## Paso 2 — Reúne las cinco piezas

Un encargo sin las cinco está incompleto. Búscalas siempre en la fuente, nunca de memoria:

| Pieza | Dónde se saca |
|---|---|
| **Objetivo** | la línea de la slice en `docs/04-roadmap.md` |
| **Contrato** | `docs/api/openapi/*.yaml` (rutas y esquemas concretos). Congelado desde SINC-1 |
| **Convenciones** | la skill de la pista: `spring-api`, `spring-auth`, `spring-gateway`, `react-web`, `liquibase`, `testing` |
| **Reglas de negocio con su fuente** | el RF de `docs/02-requisitos.md` + los defectos numerados de `docs/03-analisis-y-patrones.md` §1 que esta slice tiene que evitar |
| **Límites** | qué rutas de fichero puede tocar y cuáles no (ver tabla de pistas en `docs/09-trabajo-con-agentes-ia.md` §1) |
| **Terminado cuando** | condición verificable: tests que existen, `verify()` en verde, endpoint que responde según contrato |

### Qué buscar según el tipo de slice

- **Endpoint REST** → RF correspondiente · esquema del OpenAPI · `code`s de error aplicables de `docs/12-error-contract.md` · eventos de `docs/11-observabilidad-y-logging.md` §3 · módulo de Spring Modulith al que pertenece (`docs/14-modules.md`).
- **Pantalla React** → RF · tipos generados del OpenAPI · namespace de i18n (ADR-0019) · estados carga/vacío/error obligatorios (`docs/07-frontend-ux-ui.md`).
- **Cambio de esquema** → `docs/10-modelo-de-datos.md` (que ya justifica cada tipo, `CHECK` e índice) · skill `liquibase`. **Marca el encargo como "propone, no aplica"**: los changesets los revisa una persona (`docs/09` §5).
- **Concurrencia / dinero / identidad** → el ADR correspondiente, citado explícitamente, y aviso de revisión humana antes de mergear.

## Paso 3 — Emite el encargo

Formato fijo, en un bloque copiable. Ejemplo real de `/slice v0.3 catálogo backend`:

> **Objetivo.** Implementa `GET /api/products` (paginado, filtro por nombre y por tipo, orden por precio) y `GET /api/products/{id}` en `services/ukun-api`.
>
> **Contrato.** `docs/api/openapi/ukun-api.yaml`, congelado desde SINC-1. Si algo no encaja, **para y pregunta** — no modifiques el YAML.
>
> **Convenciones.** Skill `spring-api` (DTO `record` + MapStruct, `ProblemDetail`, `@EntityGraph`, `Page<T>`, `@Transactional(readOnly=true)` a nivel de clase). Skill `testing` para los tests. Módulo `catalog` (`docs/14-modules.md`) — no importes nada de `inventory`.
>
> **Reglas de negocio, con su fuente. No las reinventes:**
> - Disponibilidad es `store_stock.quantity > 0`; un producto sin fila de stock cuenta como **no disponible**, no como desconocido (`docs/02-requisitos.md` RF-01).
> - La disponibilidad **no** va en esta respuesta: es `GET /api/availability`, endpoint separado (ADR-0017 §3).
> - Búsqueda por nombre con `unaccent(lower(...))` — defecto #22.
> - Todo `Sort` lleva `id` como último criterio — defecto #23, hay un empate real en los datos demo.
> - Filtros dinámicos con una `Specification` compuesta y **un solo** método paginado, no una explosión de derived queries (`docs/03-analisis-y-patrones.md` §2).
>
> **Límites.** Solo `services/ukun-api/src/{main,test}/java/**/catalog/**`. **No toques** `services/ukun-auth`, `apps/ukun-web`, `docs/api/openapi/`, ni ningún changelog de Liquibase.
>
> **Terminado cuando.** El endpoint responde según el contrato · hay un `*IT` con Testcontainers que cubre filtro + orden + paginación · el test de contador de sentencias demuestra que no hay N+1 · `ApplicationModules.verify()` sigue en verde.

## Paso 4 — Enlaza el issue

Cada ítem paralelizable del roadmap tiene su issue en GitHub (milestone = release, label `pista:*`). Localízalo y **cierra el encargo con su número**, para que el trabajo se pueda cerrar con `closes #N` y la barra del milestone se mantenga sola:

```bash
./scripts/roadmap-status.sh v0.3     # issues abiertos de ese release
```

Si la slice que te piden **no** tiene issue, no lo inventes en silencio: o es más pequeña que un ítem del roadmap (hereda el issue del ítem padre) o es trabajo que el roadmap no contempla, y entonces aplica el Paso 1 — para y pregunta.

Si el issue lleva la label `revision-humana` (concurrencia, dinero, identidad, esquema), el encargo debe decirlo en el apartado "Terminado cuando": no se mergea sin que lo revise una persona (`docs/09-trabajo-con-agentes-ia.md` §5).

## Reglas al generar

1. **Cita fuentes, no las parafrasees.** "RF-01" y "defecto #22" son punteros estables; "el producto tiene que estar disponible" es una invitación a que el agente lo interprete a su manera.
2. **Los límites son la parte más valiosa.** Siempre incluye qué **no** tocar, aunque parezca obvio.
3. **Nunca digas "usa las convenciones del repo" a secas.** Nombra la skill concreta.
4. **Si la slice depende de un punto de sincronización** (`SINC-1`…`SINC-5`) que aún no ha pasado, dilo en el encargo y no lo lances en paralelo.
5. **Todo identificador en inglés** (ADR-0018). Si al redactar el encargo escribes `/api/productos` o `stock_tienda`, has copiado de una fuente desactualizada — comprueba contra ADR-0018.
6. Si dos slices que se van a ejecutar a la vez tocan la misma carpeta, dilo y sugiere la skill `worktree`.
