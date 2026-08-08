---
name: worktree
description: Aísla el trabajo de una slice en un git worktree propio con su rama y su stack de Tilt en puertos distintos, y arranca ahí una sesión de Claude Code ya cargada con el encargo — opcionalmente con tilt up y el dev server del frontend en sus propias pestañas de Konsole. Usar al arrancar trabajo simultáneo en dos pistas del roadmap, o al terminarlo para limpiar.
---

# Worktrees para trabajo simultáneo

Dos sesiones sobre el mismo checkout se pisan de tres formas: ficheros abiertos, índice de git, y puertos de Docker. Un worktree resuelve las dos primeras; la reasignación de puertos, la tercera — que es la que se olvida a mano y produce el error confuso de "un contenedor no arranca y no sé por qué".

Uso: `/worktree add <slice> [opciones]` · `/worktree list` · `/worktree rm <slice>`.

| Opción | Por defecto | Efecto |
|---|---|---|
| `--mode=<modo>` | `auto` | `--permission-mode` de la sesión de Claude. Ver tabla de modos |
| `--no-claude` | — | Prepara el worktree y los scripts, pero no abre sesión |
| `--tilt` | no | Pestaña extra con `tilt up --stream` (backend completo) |
| `--web` | no | Pestaña extra con el dev server de Vite de `apps/ukun-web` |
| `--here` | no | No abre nada: imprime los comandos para pegarlos en tu terminal |

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
5. **Asigna puertos.** Escribe `.env.ports` en el worktree con un desplazamiento por worktree activo (+100 por cada uno). El `Tiltfile` carga ese fichero si existe, así que basta con escribirlo:

   | Servicio | Variable | Base | Worktree 2 | Worktree 3 |
   |---|---|:-:|:-:|:-:|
   | gateway | `GATEWAY_PORT` | 8080 | 8180 | 8280 |
   | ukun-api | `API_PORT` | 8081 | 8181 | 8281 |
   | ukun-auth | `AUTH_PORT` | 9000 | 9100 | 9200 |
   | ukun-web (Vite) | `WEB_PORT` | 5173 | 5273 | 5373 |
   | Postgres | `DB_PORT` | 5432 | 5532 | 5632 |
   | UI de Tilt | `TILT_PORT` | 10350 | 10450 | 10550 |
   | JDWP api/auth/gateway | `JDWP_*_PORT` | 5005-5007 | 5105-5107 | 5205-5207 |

   Tilt también necesita nombre de proyecto distinto para no reutilizar contenedores: `COMPOSE_PROJECT_NAME=ukun-<slice>` en el mismo `.env.ports`. Sin él, el segundo `tilt up` se engancha a los contenedores del primero.

   **`TILT_PORT` es el único que tiene que venir del shell.** El servidor HTTP de Tilt arranca antes de que se evalúe el `Tiltfile`, así que ahí ya es tarde; por eso `tilt.sh` hace `set -a; . ./.env.ports; set +a` antes de llamar a `tilt up`. Los demás los carga el `Tiltfile` solo.
6. **Genera el encargo** con la skill `slice` para esa misma slice y escríbelo en `<worktree>/.claude/ENCARGO.md`. Es lo que le da contexto a la sesión nueva: sin él arranca en frío y vuelve a deducir de `docs/` lo que ya estaba decidido.
7. **Escribe los scripts de arranque** en `<worktree>/.claude/session/` (ver abajo) y **lanza las pestañas**, salvo `--here` o `--no-claude`.

`.claude/ENCARGO.md` y `.claude/session/` están en `.gitignore`: son estado local del worktree, no artefactos del repo. `.env.ports` ya lo cubre la regla `.env.*`.

## Lanzamiento de la sesión

### Modos de Claude

`--mode=<modo>` se traduce directo a `claude --permission-mode <modo>`. **`auto` es el valor por defecto** y el correcto para casi toda slice del roadmap.

| Modo | Cuándo |
|---|---|
| `auto` | **Por defecto.** Trabajo normal de una slice acotada por sus límites de ficheros |
| `plan` | La slice propone y no aplica: cambios de esquema (`liquibase`), cualquier issue con label `revision-humana` |
| `acceptEdits` | Edición continua sin confirmar cada fichero, pero preguntando para el resto |
| `manual` / `dontAsk` | Casos puntuales; no los uses como default de una slice |
| `bypassPermissions` | **No lo pongas en un worktree de este repo.** Ver reglas |

### Los tres scripts

El campo `command:` de Konsole **no admite comillas anidadas**, así que cada pestaña apunta a un script propio, que además deja el entorno cargado y es depurable a mano. Los tres terminan en `exec zsh` para que la pestaña sobreviva al proceso y conserves el scrollback si algo peta.

`.claude/session/claude.sh`:
```sh
#!/usr/bin/env zsh
cd /home/sandoretti/developer/repos/ukun-catalog-api || exit 1
set -a; . ./.env.ports; set +a
claude --permission-mode auto -n 'v0.3-catalog-api' "$(cat .claude/ENCARGO.md)"
exec zsh
```

`.claude/session/tilt.sh` (solo con `--tilt`):
```sh
#!/usr/bin/env zsh
cd /home/sandoretti/developer/repos/ukun-catalog-api || exit 1
set -a; . ./.env.ports; set +a
tilt up --stream
exec zsh
```

`.claude/session/web.sh` (solo con `--web`):
```sh
#!/usr/bin/env zsh
cd /home/sandoretti/developer/repos/ukun-catalog-api/apps/ukun-web || exit 1
set -a; . ../../.env.ports; set +a
[ -d node_modules ] || npm ci
npm run dev -- --port "${WEB_PORT:-5173}" --strictPort
exec zsh
```

`--strictPort` es obligatorio: sin él Vite salta al siguiente puerto libre en silencio y acabas mirando el frontend del *otro* worktree.

Todos con `chmod +x`. Comprueba antes de escribir `web.sh` que `apps/ukun-web` existe — hasta que cierre el issue de scaffolding de v0.1 no existe, y entonces `--web` no aplica: dilo en vez de generar un script que muere en el `cd`.

### Konsole

Una ventana con las pestañas pedidas, vía `--tabs-from-file`. Formato: una línea por pestaña, campos separados por `;;`.

`.claude/session/tabs`:
```
title: claude v0.3-catalog-api;; workdir: /home/sandoretti/developer/repos/ukun-catalog-api;; command: /home/sandoretti/developer/repos/ukun-catalog-api/.claude/session/claude.sh
title: tilt v0.3-catalog-api;; workdir: /home/sandoretti/developer/repos/ukun-catalog-api;; command: /home/sandoretti/developer/repos/ukun-catalog-api/.claude/session/tilt.sh
```

```bash
konsole --tabs-from-file <worktree>/.claude/session/tabs &
```

Rutas **absolutas** en `command:` y `workdir:` — el fichero se interpreta desde el cwd de Konsole, no desde el del worktree.

Sobre pestaña nueva **en la ventana actual** (`--new-tab`): Konsole solo lo respeta con «Ejecutar todas las ventanas de Konsole en un único proceso» activado (`RunAllKonsoleInSingleProcess=true` en `~/.config/konsolerc`). Si no está, `--new-tab` abre una ventana nueva sin avisar. No lo actives por tu cuenta: es configuración personal del usuario. Comprueba la clave; si falta, usa `--tabs-from-file` y menciónalo de pasada.

### `--here`

No lanzas nada. Escribes igualmente los scripts y el encargo, y devuelves las líneas para pegar:

```bash
/home/sandoretti/developer/repos/ukun-catalog-api/.claude/session/claude.sh
```

Es el modo correcto cuando el usuario está en una terminal que ya tiene colocada, o cuando trabaja sobre SSH y no hay Konsole al otro lado.

## `/worktree list`

`git worktree list`, más para cada uno: rama, si tiene cambios sin commitear, si su stack de Tilt está levantado (`docker ps` filtrando por `COMPOSE_PROJECT_NAME`), y a cuántos commits está de `main`. Sirve para responder "¿qué tengo abierto?" sin ir directorio por directorio.

## `/worktree rm <slice>`

1. **Comprueba que no queda trabajo sin integrar**: cambios sin commitear, o commits que no están en `main`. Si los hay, **para y dilo** — no borres. Ofrece: commitear, hacer PR, o `git worktree remove --force` con confirmación explícita del usuario.
2. Baja su stack: `tilt down` (o `docker compose -p ukun-<slice> down`) desde ese worktree.
3. `git worktree remove ../ukun-<slice>`.
4. Borra la rama solo si ya está mergeada, y dilo. Si no lo está, la rama se queda.

Las pestañas de Konsole no se cierran solas. Recuérdale al usuario que cierre las del worktree que acaba de borrar — una sesión de Claude viva sobre un directorio que ya no existe falla de forma poco obvia.

## Reglas

- **Nunca borres un worktree con trabajo sin integrar** sin confirmación explícita. Es exactamente el tipo de acción irreversible que `docs/09-trabajo-con-agentes-ia.md` §5 exige confirmar.
- **Nunca arranques una sesión en `bypassPermissions`.** El aislamiento de un worktree es de ficheros y puertos, no de sistema: la sesión sigue teniendo la misma máquina, el mismo Docker y las mismas credenciales. Si el usuario lo pide explícitamente, avísale de eso una vez y hazle caso.
- **Un worktree por slice, no por fichero.** Si acabas con más de tres activos, el problema es de granularidad del reparto, no de aislamiento.
- **Sin encargo no se lanza sesión.** Si la skill `slice` no puede generar uno (la slice no está en el roadmap, `docs/api/openapi/` aún no tiene el contrato que necesita), para ahí: el worktree se queda creado y sin sesión. Una sesión arrancada en frío sobre una slice mal acotada es justo el fallo que `slice` existe para evitar.
- **El punto de sincronización sigue siendo el del roadmap.** Dos worktrees que avanzan en paralelo no eliminan la dependencia de `SINC-1`…`SINC-5`; solo evitan que se estorben mientras tanto.
- **Cada sesión trabaja en su propio directorio raíz.** Una sesión que hace `cd` a otro worktree acaba con contexto mezclado de dos ramas, que es justo lo que esto evita.
- **`--tilt` levanta un stack entero por worktree** (cinco contenedores y un Postgres). Con dos worktrees eso son dos bases de datos y dos JVM por servicio. Si la slice no necesita el backend corriendo — un endpoint que aún no existe, trabajo de contrato o de docs — no lo pidas.
