#!/usr/bin/env bash
#
# Estado del roadmap de Ukun, leído de los milestones e issues de GitHub.
#
#   ./scripts/roadmap-status.sh            avance por release + abiertos por pista
#   ./scripts/roadmap-status.sh v0.3       issues abiertos de ese release
#   ./scripts/roadmap-status.sh --next     lo siguiente accionable (release en curso)
#
# El "qué" y el "por qué" de cada release viven en docs/04-roadmap.md; aquí solo
# vive el estado de ejecución. Si los dos discrepan, manda docs/04-roadmap.md.
set -euo pipefail

command -v gh >/dev/null || { echo "Falta gh (https://cli.github.com)"; exit 1; }
command -v jq >/dev/null || { echo "Falta jq"; exit 1; }

REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"

bar() { # bar <cerrados> <total>  ->  [####------]
  local done=$1 total=$2 filled=0
  [ "$total" -gt 0 ] && filled=$(( done * 10 / total ))
  printf '['
  for ((i = 0; i < 10; i++)); do [ "$i" -lt "$filled" ] && printf '#' || printf '-'; done
  printf ']'
}

resumen() {
  echo
  echo "Ukun — estado del roadmap   ($REPO)"
  echo

  gh api "repos/$REPO/milestones?state=all&per_page=100" \
    --jq '.[] | [.title, .closed_issues, (.open_issues + .closed_issues), (.due_on // "")] | @tsv' \
    | sort \
    | while IFS=$'\t' read -r title closed total due; do
        # Padding a mano: printf %-Ns cuenta bytes, y los títulos llevan tildes.
        printf '  %s%*s %s %3s/%-3s %s\n' \
          "$title" "$(( 26 - ${#title} ))" "" \
          "$(bar "$closed" "$total")" "$closed" "$total" "${due%%T*}"
      done

  echo
  local pistas
  pistas="$(gh issue list --repo "$REPO" --state open --limit 300 --json labels \
    --jq '[.[].labels[].name | select(startswith("pista:"))] | group_by(.) | map("\(.[0][6:8]):\(length)") | join("  ")')"
  echo "  Abiertos por pista:  ${pistas:-ninguno}"

  echo
  echo "  Hitos de sincronización pendientes:"
  gh issue list --repo "$REPO" --state open --label sinc --limit 20 \
    --json number,title --jq '.[] | "    \(.title)  →  #\(.number)"' \
    | sort

  echo
  echo "  Detalle: ./scripts/roadmap-status.sh v0.3   ·   Contexto: docs/04-roadmap.md"
  echo
}

detalle() { # detalle <prefijo de milestone>
  local ms
  ms="$(gh api "repos/$REPO/milestones?state=all&per_page=100" --jq '.[].title' \
        | grep -i "^$1" | head -1 || true)"
  [ -n "$ms" ] || { echo "No hay ningún release que empiece por '$1'."; exit 1; }

  echo
  echo "$ms — issues abiertos"
  echo
  gh issue list --repo "$REPO" --state open --milestone "$ms" --limit 100 \
    --json number,title,labels \
    --jq '.[] | "  #\(.number)  \(.title)\n        \([.labels[].name] | join(", "))"'
  echo
  echo "  Cerrados:"
  gh issue list --repo "$REPO" --state closed --milestone "$ms" --limit 100 \
    --json number,title --jq '.[] | "  ✓ #\(.number)  \(.title)"'
  echo
}

siguiente() {
  local ms
  ms="$(gh api "repos/$REPO/milestones?state=all&per_page=100" \
        --jq '[.[] | select(.open_issues > 0)] | sort_by(.title) | .[0].title // ""')"
  [ -n "$ms" ] || { echo "Nada abierto: todos los releases están cerrados."; exit 0; }
  detalle "$ms"
}

case "${1:-}" in
  "")        resumen ;;
  --next|-n) siguiente ;;
  -h|--help) sed -n '3,9p' "$0" | sed 's/^# \{0,1\}//' ;;
  *)         detalle "$1" ;;
esac
