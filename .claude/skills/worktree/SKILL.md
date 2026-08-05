---
name: worktree
description: Aísla el trabajo de una slice en un git worktree propio con su rama y su stack de Tilt en puertos distintos, para que dos o más sesiones de Claude Code trabajen a la vez sin colisionar en ficheros, en el índice de git ni en los puertos de Docker. Usar al arrancar trabajo simultáneo en dos pistas del roadmap, o al terminarlo para limpiar.
---

# Worktrees para trabajo simultáneo

Dos sesiones sobre el mismo checkout se pisan de tres formas: ficheros abiertos, índice de git, y puertos de Docker. Un worktree resuelve las dos primeras; la reasignación de puertos, la tercera — que es la que se olvida a mano y produce el error confuso de "un contenedor no arranca y no sé por qué".

Uso: `/worktree add <slice>` · `/worktree list` · `/worktree rm <slice>`.

## Cuándo NO usarlo

- Una sola sesión trabajando. El checkout normal es más simple y no añade nada.
- Tareas cortas o de solo lectura (revisar docs, responder una pregunta).
- Dos slices de la misma pista que van a tocar los mismos ficheros — ahí el worktree no evita el conflicto, solo lo retrasa hasta el merge. Hazlas en secuencia.

Regla práctica: worktree cuando el trabajo es **de verdad simultáneo** y de **más de una sesión larga**.

## `/worktree add <slice>`

1. **Comprueba el estado del repo principal.** Si hay cambios sin commitear que pertenezcan a la slice nueva, avisa antes de nada — no los muevas por tu cuenta.
2. **Deriva los nombres** de la slice, en kebab-case y en inglés (ADR-0018):
   - rama: `<release>-<slice>` → `v0.3-catalog-api`
   - directorio: `../ukun-<slice>` → `../ukun-catalog-api`
3. **Crea el worktree** desde `main` actualizado:
   ```bash
   git worktree add -b v0.3-catalog-api ../ukun-catalog-api main
   ```
4. **Copia lo que no está versionado y hace falta para arrancar:** `.env` (nunca se commitea). Si no existe en el origen, dilo y apunta a `.env.example`.
5. **Asigna puertos.** Escribe `.env.ports` en el worktree con un desplazamiento por worktree activo (+100 por cada uno), y confirma que el `Tiltfile` los lee en vez de literales:

   | Servicio | Base | Worktree 2 | Worktree 3 |
   |---|:-:|:-:|:-:|
   | gateway | 8080 | 8180 | 8280 |
   | ukun-api | 8081 | 8181 | 8281 |
   | ukun-auth | 9000 | 9100 | 9200 |
   | ukun-web (Vite) | 5173 | 5273 | 5373 |
   | Postgres | 5432 | 5532 | 5632 |
   | UI de Tilt | 10350 | 10450 | 10550 |
   | JDWP api/auth/gateway | 5005-5007 | 5105-5107 | 5205-5207 |

   Tilt también necesita nombre de proyecto distinto para no reutilizar contenedores: `COMPOSE_PROJECT_NAME=ukun-<slice>` en el mismo `.env.ports`.
6. **Informa de qué hacer a continuación:** abrir una sesión nueva de Claude Code **con el worktree como directorio de trabajo** (no `cd` desde la sesión actual — cada sesión debe tener su propio directorio raíz), y el encargo que genera la skill `slice`.

## `/worktree list`

`git worktree list`, más para cada uno: rama, si tiene cambios sin commitear, si su stack de Tilt está levantado (`docker ps` filtrando por `COMPOSE_PROJECT_NAME`), y a cuántos commits está de `main`. Sirve para responder "¿qué tengo abierto?" sin ir directorio por directorio.

## `/worktree rm <slice>`

1. **Comprueba que no queda trabajo sin integrar**: cambios sin commitear, o commits que no están en `main`. Si los hay, **para y dilo** — no borres. Ofrece: commitear, hacer PR, o `git worktree remove --force` con confirmación explícita del usuario.
2. Baja su stack: `tilt down` (o `docker compose -p ukun-<slice> down`) desde ese worktree.
3. `git worktree remove ../ukun-<slice>`.
4. Borra la rama solo si ya está mergeada, y dilo. Si no lo está, la rama se queda.

## Reglas

- **Nunca borres un worktree con trabajo sin integrar** sin confirmación explícita. Es exactamente el tipo de acción irreversible que `docs/09-trabajo-con-agentes-ia.md` §5 exige confirmar.
- **Un worktree por slice, no por fichero.** Si acabas con más de tres activos, el problema es de granularidad del reparto, no de aislamiento.
- **El punto de sincronización sigue siendo el del roadmap.** Dos worktreees que avanzan en paralelo no eliminan la dependencia de `SINC-1`…`SINC-5`; solo evitan que se estorben mientras tanto.
- **Cada sesión trabaja en su propio directorio raíz.** Una sesión que hace `cd` a otro worktree acaba con contexto mezclado de dos ramas, que es justo lo que esto evita.
