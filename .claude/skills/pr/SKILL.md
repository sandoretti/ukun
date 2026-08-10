---
name: pr
description: Abre el pull request de la rama con la plantilla del repo — título según convención, cuerpo por secciones y la palabra de cierre del issue decidida por sus labels (un issue con revision-humana nunca autocierra). Usar al terminar una slice, cuando el usuario pida "haz el PR", "sube esto" o "abre la pull request".
---

# Abrir el PR de una slice

Un PR de este repo no es "aquí está el diff". Es el documento donde queda escrito **qué entra, qué se decidió y qué se comprobó**, y es lo único que sobrevive a la sesión que lo produjo. Los PR #95, #93 y #85 son el listón; esta skill lo reproduce sin tener que recordarlo cada vez.

La parte que se olvida a mano y hace daño en silencio es la palabra de cierre: un `Closes #N` en un issue marcado `revision-humana` se salta la revisión entera en el momento del merge.

Uso: `/pr` · `/pr <issue>` · `/pr --draft` · `/pr --no-close` (fuerza `Refs`) · `/pr --close` (fuerza `Closes`, salvo label bloqueante).

## Paso 1 — Comprobaciones previas

Si alguna falla, **para y dilo** en vez de abrir un PR que habrá que rehacer.

- **No estás en `main`.** Si lo estás, no abras nada: hay que crear rama primero.
- **La pasada de refactor está hecha** (skill `refactor`). Es el último commit y de ahí sale una sección del cuerpo.
- **Los tests están en verde**, con las cifras a mano: van literales a «Verificación» y no se inventan.
- **No hay cambios sin commitear.** `git status`; si los hay, pregunta antes de incluirlos.
- **El diff no lleva secretos:** `git diff main...HEAD --name-only | grep -E '^\.env'` vacío, y una mirada a contraseñas o tokens en fixtures. Este repo ya tuvo un `.env` con contraseña real en el historial.
- **La rama está en el remoto:** `git push -u origin <rama>`.

## Paso 2 — Localiza el issue

Por orden: lo que diga el usuario → `.claude/ENCARGO.md` del worktree → un `closes #N` en los mensajes de commit → `./scripts/roadmap-status.sh --next` y `gh issue list --milestone "<release>"`.

Si no existe issue y el trabajo sí está en el roadmap, propón crearlo antes (el milestone es la barra de progreso del release). Si de verdad no hay issue —un `fix/*` puntual—, PR sin referencia y se dice en el cuerpo por qué.

## Paso 3 — Los labels del issue deciden el cierre

```bash
gh issue view <N> --json number,title,labels,milestone
```

| Label del issue | Palabra en el cuerpo | Merge |
|---|---|---|
| `revision-humana` | **`Refs #N`** — nunca `Closes` | No lo mergeas tú. Lo revisa y lo cierra una persona (`docs/09` §5: concurrencia, dinero, identidad, esquema) |
| `sinc` | `Refs #N` | Bloquea el trabajo posterior; lo cierra quien coordina el punto de sincronización |
| `contrato` | `Refs #N` salvo que el usuario diga lo contrario | Tocar OpenAPI tras SINC-1 invalida tipos y mocks: es coordinación, no implementación |
| Cualquier otro | `Closes #N` por defecto | Autocierre al mergear |

Cuando ningún label fuerza la mano, **pregunta** si el usuario no lo ha dicho ya: autocierre (por defecto) o `Refs` + cierre manual tras revisarlo él. Si elige revisar antes, `Refs #N` y una línea en el cuerpo diciendo que el issue se cierra a mano; `--draft` si además quiere que no se pueda mergear por accidente.

### La palabra de cierre vive en un sitio y solo en uno

`CLAUDE.md` pide cerrar issues desde el commit (`closes #42`). Eso convive bien con el cuerpo del PR —GitHub cierra una vez—, **pero con `revision-humana` no puede estar en ninguno de los dos**: un `closes #N` enterrado en un mensaje de commit autocierra el issue al mergear aunque el cuerpo diga `Refs`. Compruébalo siempre antes de abrir:

```bash
git log main..HEAD --format='%h %s%n%b' | grep -inE '(clos|fix|resolv)e?[sd]? #[0-9]+'
```

Si aparece sobre un issue de revisión humana, dilo y reescribe el mensaje. En una rama propia todavía sin mergear es seguro (`git rebase -i` / `commit --amend`), pero **el force-push posterior se pide explícitamente**, nunca como efecto colateral (`docs/09` §5).

Y un detalle que evita falsos positivos: **GitHub no cierra nada por el título del PR**, solo por el cuerpo y por los mensajes de commit. El `(closes #19)` que llevan varios títulos del historial es decorativo. En un issue de revisión humana escribe `(refs #N)` para no dar a entender lo contrario.

## Paso 4 — El título

Dos formas, las dos ya en el historial:

| Caso | Forma | Ejemplo real |
|---|---|---|
| Slice del roadmap | `v0.X · Descripción con mayúscula inicial` | `v0.3 · Pantalla de ficha de producto (closes #19)` |
| Cambio acotado (fix, chore, docs) | `tipo(scope): descripción en minúscula` | `fix(api): servir /media/products/** — las fotos de producto daban 404 (closes #87)` |

`tipo` ∈ `feat` · `fix` · `refactor` · `chore` · `docs` · `test`. `scope` ∈ `api` · `auth` · `gateway` · `web` · `dev` · `skills` · `docs`. Sin punto final. **La prosa va en español**; los identificadores que cites dentro, en inglés (ADR-0018 rige el código, no el idioma del PR).

## Paso 5 — El cuerpo: siempre la plantilla

Está en [`plantilla.md`](plantilla.md), junto a esta skill. Cópiala, rellénala y **borra las secciones que no apliquen** — una sección con «N/A» es ruido. Cómo se escribe:

- **Explica el porqué, no repitas el diff.** Quien revisa ya ve los ficheros; lo que no ve es qué alternativa descartaste.
- **Cada afirmación de negocio, con su fuente**: RF-xx de `docs/02-requisitos.md`, el ADR, el número de defecto de `docs/03-analisis-y-patrones.md`.
- **Verificación solo de lo que has corrido de verdad.** Lo que quede sin comprobar (típicamente lo visual) va como *pendiente de revisión humana*, con la ruta concreta que mirar — el #95 lo hace y es el ejemplo a copiar.
- Escríbelo a fichero y pásalo con `--body-file`: el cuerpo lleva backticks, comillas y saltos de línea, y `--body "…"` en la shell los destroza.

## Paso 6 — Crear el PR

```bash
gh pr create \
  --base main --head "$(git branch --show-current)" \
  --title "v0.3 · Pantalla de ficha de producto (closes #19)" \
  --body-file "$SCRATCHPAD/pr-body.md" \
  --label "pista:P3-frontend" \
  --milestone "v0.3 Catálogo REST"
```

- **Labels:** copia del issue la pista `pista:P1…P5` y, si lo lleva, `revision-humana` — así la señal viaja también en el PR y no hay que abrir el issue para saberlo. Añade `contrato` si el diff toca `docs/api/openapi/`.
- **Milestone:** el mismo del issue, o la barra del release se queda desfasada.
- `--draft` si el trabajo se enseña pero no se mergea todavía.

## Paso 7 — Después de abrirlo

- Devuelve la URL y el resumen de qué label rigió la decisión de cierre.
- **No mergees si no te lo piden.** Y aunque te lo pidan: con `revision-humana` en el issue, la respuesta es que lo mergea una persona.
- Si el usuario pide merge y no hay label bloqueante, este repo usa **commit de merge** (`Merge pull request #94 from sandoretti/…`): `gh pr merge <N> --merge --delete-branch`. Nada de `--auto` sobre un PR de revisión humana.
- Tras mergear, `./scripts/roadmap-status.sh` confirma que la barra del milestone se ha movido; si no, el `closes` no enganchó.

## Reglas

- **Nunca `Closes` en un issue con `revision-humana`**, ni en el cuerpo ni escondido en un commit.
- **Nunca mergeas sin que te lo pidan explícitamente**, y nunca un PR de revisión humana.
- **Un PR por slice.** Si el cuerpo necesita dos párrafos de apertura sin relación entre sí, eran dos PR.
- **No se inventa verificación.** Lo no comprobado se declara no comprobado.
- **El PR no es donde se decide algo nuevo.** Si al escribirlo descubres una convención sin documentar, va a la skill o a `docs/` en el mismo PR (`docs/09` §7).
- **Force-push y reescritura de historia, siempre con confirmación explícita.**
