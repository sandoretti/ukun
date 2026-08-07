#!/usr/bin/env bash
#
# Abre una pestaña nueva de Konsole dividida en dos paneles para trabajar en un worktree:
# a la izquierda `tilt up`, a la derecha una sesión de Claude Code con el encargo cargado.
#
#   scripts/worktree-konsole.sh --dir ../ukun-catalog-api --title catalog-api \
#       --tilt-port 10450 --claude-mode acceptEdits
#
# Lo invoca el paso 7 de la skill `worktree`. Se puede lanzar a mano contra cualquier
# directorio; no toca git ni Docker, solo pide cosas a Konsole por D-Bus.
#
#   --dir <ruta>            directorio de trabajo de los dos paneles (obligatorio)
#   --title <texto>         etiqueta de la pestaña y de cada panel (obligatorio)
#   --tilt-port <n>         puerto de la UI de Tilt (TILT_PORT de .env.ports)
#   --no-tilt               no lanzar Tilt; dejar el panel izquierdo en un shell
#   --claude-mode <modo>    acceptEdits | plan | bypassPermissions | default | none
#   --prompt-file <ruta>    encargo inicial de Claude, relativo a --dir
#                           (por omisión .claude/encargo.md)
#
# Si ni Tilt ni Claude van a arrancar, no divide la pestaña: no hay nada que separar.
set -euo pipefail

DIR=""
TITLE=""
TILT_PORT=""
WITH_TILT=1
CLAUDE_MODE="acceptEdits"
PROMPT_FILE=".claude/encargo.md"

die() { echo "worktree-konsole: $*" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --dir)          DIR="${2:-}"; shift 2 ;;
    --title)        TITLE="${2:-}"; shift 2 ;;
    --tilt-port)    TILT_PORT="${2:-}"; shift 2 ;;
    --no-tilt)      WITH_TILT=0; shift ;;
    --claude-mode)  CLAUDE_MODE="${2:-}"; shift 2 ;;
    --prompt-file)  PROMPT_FILE="${2:-}"; shift 2 ;;
    -h|--help)      sed -n '3,20p' "$0"; exit 0 ;;
    *)              die "opción desconocida: $1 (--help para el uso)" ;;
  esac
done

[ -n "$DIR" ]   || die "falta --dir"
[ -n "$TITLE" ] || die "falta --title"
[ -d "$DIR" ]   || die "el directorio no existe: $DIR"
DIR="$(cd "$DIR" && pwd)"   # Konsole necesita ruta absoluta

case "$CLAUDE_MODE" in
  acceptEdits|plan|bypassPermissions|default|none) ;;
  *) die "--claude-mode inválido: $CLAUDE_MODE (acceptEdits|plan|bypassPermissions|default|none)" ;;
esac

# ---------------------------------------------------------------------------
# Guardas. Cada una explica qué hacer, porque este script se lanza desde una skill
# y el usuario no tiene por qué saber de D-Bus.

QDBUS=""
for candidate in qdbus-qt6 qdbus6 qdbus; do
  if command -v "$candidate" >/dev/null 2>&1; then QDBUS="$candidate"; break; fi
done
[ -n "$QDBUS" ] || die "falta qdbus (instala qt6-qttools / qt5-qttools-devel)"

[ -n "${KONSOLE_DBUS_SERVICE:-}" ] || die \
  "esta sesión no corre dentro de Konsole (falta KONSOLE_DBUS_SERVICE).
   Abre la pestaña a mano:  konsole --new-tab --workdir '$DIR'"

SVC="$KONSOLE_DBUS_SERVICE"
WIN="${KONSOLE_DBUS_WINDOW:-/Windows/1}"

kwin()  { "$QDBUS" "$SVC" "$WIN" "$@"; }
ksess() { local id="$1"; shift; "$QDBUS" "$SVC" "/Sessions/$id" "$@"; }

# ---------------------------------------------------------------------------
# Comandos que acabará ejecutando cada panel.

if [ "$WITH_TILT" -eq 1 ]; then
  LEFT_CMD="cd '$DIR' && tilt up"
  if [ -n "$TILT_PORT" ]; then LEFT_CMD="$LEFT_CMD --port $TILT_PORT"; fi
else
  LEFT_CMD="cd '$DIR'"
fi

case "$CLAUDE_MODE" in
  bypassPermissions) CLAUDE_FLAGS="--dangerously-skip-permissions" ;;
  default)           CLAUDE_FLAGS="" ;;
  none)              CLAUDE_FLAGS="" ;;
  *)                 CLAUDE_FLAGS="--permission-mode $CLAUDE_MODE" ;;
esac

if [ "$CLAUDE_MODE" = "none" ]; then
  RIGHT_CMD="cd '$DIR'"
elif [ -f "$DIR/$PROMPT_FILE" ]; then
  # El encargo se pasa como prompt inicial de una sesión interactiva. La sustitución
  # de $(cat ...) la hace la shell del panel, no esta: así el encargo no tiene que
  # sobrevivir a dos niveles de comillas.
  RIGHT_CMD="cd '$DIR' && claude $CLAUDE_FLAGS \"\$(cat '$PROMPT_FILE')\""
else
  echo "worktree-konsole: aviso — no existe $DIR/$PROMPT_FILE; abro Claude sin encargo inicial." >&2
  RIGHT_CMD="cd '$DIR' && claude $CLAUDE_FLAGS"
fi

SPLIT=1
if [ "$WITH_TILT" -eq 0 ] && [ "$CLAUDE_MODE" = "none" ]; then
  SPLIT=0   # dos shells idénticos no aportan nada
fi

# ---------------------------------------------------------------------------
# Pestaña nueva.
#
# newSession() devuelve el id de sesión, pero createSplit() necesita un id de VISTA y
# no devuelve nada útil, así que ambos ids nuevos se obtienen diferenciando el estado
# de antes y el de después. viewHierarchy() imprime líneas tipo "(0)[1 2]": splitter
# entre paréntesis, vistas entre corchetes.

views_now()    { kwin viewHierarchy | grep -oP '(?<=\[)[^]]*(?=\])' | tr ' ' '\n' | sed '/^$/d' | sort -n || true; }
sessions_now() { kwin sessionList | tr ' ' '\n' | sed '/^$/d' | sort -n || true; }

PROFILE="$(kwin defaultProfile)"

views_before="$(views_now)"
sessions_before="$(sessions_now)"

LEFT_SESSION="$(kwin newSession "$PROFILE" "$DIR")"
[ -n "$LEFT_SESSION" ] || die "Konsole no devolvió una sesión nueva"

LEFT_VIEW="$(comm -13 <(echo "$views_before") <(views_now) | head -1)"
[ -n "$LEFT_VIEW" ] || die "no se pudo identificar la vista de la pestaña nueva"

kwin setCurrentSession "$LEFT_SESSION" >/dev/null

RIGHT_SESSION=""
if [ "$SPLIT" -eq 1 ]; then
  sessions_before_split="$(sessions_now)"
  # true = división vertical, paneles lado a lado (izquierda/derecha).
  kwin createSplit "$LEFT_VIEW" true >/dev/null
  RIGHT_SESSION="$(comm -13 <(echo "$sessions_before_split") <(sessions_now) | head -1)"
  [ -n "$RIGHT_SESSION" ] || die "createSplit no creó una sesión nueva"
fi

# ---------------------------------------------------------------------------
# Títulos y arranque.

ksess "$LEFT_SESSION" setTitle 0 "$([ "$SPLIT" -eq 1 ] && echo "tilt · $TITLE" || echo "$TITLE")" >/dev/null
ksess "$LEFT_SESSION" runCommand "$LEFT_CMD" >/dev/null
if [ "$WITH_TILT" -eq 0 ] && [ "$SPLIT" -eq 1 ]; then
  ksess "$LEFT_SESSION" runCommand \
    "echo 'Tilt no se lanzó. Para levantarlo:  tilt up${TILT_PORT:+ --port $TILT_PORT}'" >/dev/null
fi

if [ -n "$RIGHT_SESSION" ]; then
  ksess "$RIGHT_SESSION" setTitle 0 "claude · $TITLE" >/dev/null
  ksess "$RIGHT_SESSION" runCommand "$RIGHT_CMD" >/dev/null
  kwin setCurrentSession "$RIGHT_SESSION" >/dev/null   # el foco acaba en Claude
fi

# ---------------------------------------------------------------------------

echo "Pestaña «$TITLE» abierta en $DIR"
if [ "$SPLIT" -eq 1 ]; then
  echo "  izquierda (sesión $LEFT_SESSION):  $LEFT_CMD"
  echo "  derecha   (sesión $RIGHT_SESSION):  $RIGHT_CMD"
else
  echo "  sesión $LEFT_SESSION (sin dividir): $LEFT_CMD"
fi
