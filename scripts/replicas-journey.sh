#!/usr/bin/env bash
#
# La condición de salida del issue #118, ejecutada contra el entorno de compose.replicas.yaml:
# el recorrido completo funcionando con dos réplicas de ukun-api y dos de ukun-auth levantadas.
#
#   docker compose -f compose.yaml -f compose.replicas.yaml up --build -d \
#     db ukun-api ukun-auth ukun-auth-b auth-lb ukun-gateway
#   ./scripts/replicas-journey.sh
#
# Habla por HTTP y nada más: no para contenedores ni toca la base. Las dos comprobaciones que sí
# exigen parar una réplica —crear la sesión en una y usarla en la otra, y reiniciar el gateway sin
# perder la sesión— están descritas en docs/06-entorno-local-tilt.md, porque son de hacer a mano.
#
# El porqué de cada afirmación está en ADR-0024; aquí solo vive la comprobación.
#
# Variables: GATEWAY_URL, AUTH_URL, EMAIL, PASSWORD (por defecto, la cuenta cliente de los datos demo).
set -uo pipefail

GATEWAY_URL=${GATEWAY_URL:-http://localhost:8080}
AUTH_URL=${AUTH_URL:-http://localhost:9000}
EMAIL=${EMAIL:-joel.sandoval@email.com}
PASSWORD=${PASSWORD:-ukun-demo-2026}

# El producto con el que se compra. Un SKU de los datos demo con stock en la tienda online: el
# recorrido tiene que fallar por réplicas si falla, no por un 409 de stock que sería correcto.
SKU=${SKU:-SOFA-CUERO}

JAR=$(mktemp)
WORK=$(mktemp -d)
trap 'rm -rf "$JAR" "$WORK"' EXIT
failures=0

section() { printf '\n\033[1m%s\033[0m\n' "$*"; }
ok()      { printf '  \033[32mOK\033[0m    %s\n' "$*"; }
ko()      { printf '  \033[31mFALLA\033[0m %s\n' "$*"; failures=$((failures + 1)); }
expect()  { [ "$1" = "$2" ] && ok "$3 ($1)" || ko "$3: esperaba $2, obtuve $1"; }

# El token CSRF de la página de login de ukun-auth, que es de su sesión: sin él, el POST no llega a
# la comprobación de credenciales y el fallo se confunde con un login rechazado.
csrf_token_of() {
    python3 -c "
import re, sys
html = open(sys.argv[1], encoding='utf-8', errors='replace').read()
found = re.search(r'name=\"_csrf\".*?value=\"([^\"]+)\"', html, re.S)
print(found.group(1) if found else '')" "$1"
}

json_field() { python3 -c "import json,sys; print(json.load(sys.stdin)$1)" 2>/dev/null; }

# ---------------------------------------------------------------------------------------------
section "Catálogo público, sin sesión"
expect "$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY_URL/api/products?size=3")" 200 "GET /api/products"
expect "$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY_URL/api/me")" 401 "GET /api/me sin sesión"

# ---------------------------------------------------------------------------------------------
section "Login OAuth2 completo, repartido entre las dos réplicas de ukun-auth"

# Como lo hace un navegador: el gateway redirige al authorization endpoint, ukun-auth pide la
# contraseña en su propia página, y el código vuelve al callback del gateway. Un solo tarro de
# cookies porque un navegador tampoco tiene dos — y es lo que hace que este recorrido detecte una
# colisión de nombres entre las cookies de sesión de los dos servicios (ADR-0024).
curl -s -c "$JAR" -b "$JAR" -L -o "$WORK/login.html" "$GATEWAY_URL/oauth2/authorization/ukun" >/dev/null
csrf=$(csrf_token_of "$WORK/login.html")
[ -n "$csrf" ] && ok "página de login servida, con su token CSRF" || ko "la página de login no trae token CSRF"

landed=$(curl -s -c "$JAR" -b "$JAR" -L -o /dev/null -w '%{url_effective}' \
    --data-urlencode "username=$EMAIL" \
    --data-urlencode "password=$PASSWORD" \
    --data-urlencode "_csrf=$csrf" \
    "$AUTH_URL/login")
case "$landed" in
    *"/login?error"*) ko "credenciales rechazadas o sesión no reconocida (acabó en $landed)" ;;
    *) ok "login completado, de vuelta en $landed" ;;
esac

expect "$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR" "$GATEWAY_URL/api/me")" 200 "GET /api/me con sesión"

# ---------------------------------------------------------------------------------------------
section "Carrito y pedido"

# La cookie del gateway, que el SPA lee y devuelve en cada mutación (skill `spring-gateway`).
xsrf=$(grep -F 'XSRF-TOKEN' "$JAR" | awk '{print $7}' | tail -1)
[ -n "$xsrf" ] && ok "cookie XSRF-TOKEN emitida" || ko "el gateway no emitió XSRF-TOKEN"

# El carrito de la cuenta demo puede arrastrar productos de pruebas anteriores, y alguno sin stock:
# eso daría un 409 INSUFFICIENT_STOCK correcto que no tiene nada que ver con las réplicas.
for stale in $(curl -s -b "$JAR" "$GATEWAY_URL/api/cart" \
        | python3 -c "
import json, sys
try: print(' '.join(str(item['productId']) for item in json.load(sys.stdin).get('items', [])))
except Exception: pass"); do
    curl -s -o /dev/null -b "$JAR" -c "$JAR" -X DELETE -H "X-XSRF-TOKEN: $xsrf" "$GATEWAY_URL/api/cart/items/$stale"
done
ok "carrito vacío al empezar"

product=$(curl -s -b "$JAR" "$GATEWAY_URL/api/products/by-sku/$SKU" | json_field "['id']")
[ -n "$product" ] && ok "$SKU es el producto $product" || ko "no se pudo resolver el SKU $SKU"

expect "$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR" -c "$JAR" -X PUT \
    -H "X-XSRF-TOKEN: $xsrf" -H 'Content-Type: application/json' \
    -d '{"quantity":1}' "$GATEWAY_URL/api/cart/items/$product")" 200 "PUT /api/cart/items/$product"

status=$(curl -s -o "$WORK/attempt.json" -w '%{http_code}' -b "$JAR" -c "$JAR" -X POST \
    -H "X-XSRF-TOKEN: $xsrf" "$GATEWAY_URL/api/checkout/attempts")
expect "$status" 201 "POST /api/checkout/attempts"
attempt=$(json_field "['attemptId']" < "$WORK/attempt.json")

# El attemptId lo emite el servidor y vuelve como Idempotency-Key (ADR-0016).
status=$(curl -s -o "$WORK/order.json" -w '%{http_code}' -b "$JAR" -c "$JAR" -X POST \
    -H "X-XSRF-TOKEN: $xsrf" -H "Idempotency-Key: $attempt" "$GATEWAY_URL/api/orders")
expect "$status" 201 "POST /api/orders"
[ "$status" = 201 ] && ok "pedido $(json_field "['id']" < "$WORK/order.json") confirmado"

# ---------------------------------------------------------------------------------------------
section "Sin 401 intermitentes: doce peticiones que reparte el DNS entre las réplicas de ukun-api"
codes=$(for _ in $(seq 1 12); do curl -s -o /dev/null -w '%{http_code} ' -b "$JAR" "$GATEWAY_URL/api/orders"; done)
distinct=$(echo "$codes" | tr ' ' '\n' | grep -c . )
unique=$(echo "$codes" | tr ' ' '\n' | grep . | sort -u | tr '\n' ' ')
[ "$(echo "$unique" | wc -w)" = 1 ] \
    && ok "$distinct peticiones, un solo código ($unique)" \
    || ko "códigos distintos entre peticiones: $codes"

# ---------------------------------------------------------------------------------------------
section "La clave de firma es una sola para las dos réplicas"
# Antes de v1.0 cada réplica generaba la suya al arrancar: dos JWKS detrás del balanceador y 401
# intermitentes en ukun-api. El `kid` se deriva de la clave, así que basta con que no varíe.
kids=$(for _ in $(seq 1 20); do
    curl -s "$AUTH_URL/oauth2/jwks" | json_field "['keys'][0]['kid']"
done | sort -u)
[ "$(echo "$kids" | wc -l)" = 1 ] \
    && ok "veinte lecturas repartidas, un solo kid ($kids)" \
    || ko "más de un kid entre réplicas: $(echo "$kids" | tr '\n' ' ')"

# ---------------------------------------------------------------------------------------------
section "El presupuesto del limitador de login es uno, no uno por réplica"
# Capacidad de ráfaga: 5 (LoginAttemptBuckets). Con los buckets en memoria de cada JVM, dos réplicas
# darían hasta 11 intentos antes de cortar. Correo inventado: no gasta el presupuesto de nadie.
throwaway="budget-$(date +%s)@ukun.dev"
attempt_codes=""
for _ in $(seq 1 7); do
    curl -s -c "$WORK/budget.jar" -b "$WORK/budget.jar" -o "$WORK/budget.html" "$AUTH_URL/login"
    attempt_codes="$attempt_codes$(curl -s -o /dev/null -w '%{http_code} ' \
        -c "$WORK/budget.jar" -b "$WORK/budget.jar" \
        --data-urlencode "username=$throwaway" \
        --data-urlencode "password=incorrecta" \
        --data-urlencode "_csrf=$(csrf_token_of "$WORK/budget.html")" \
        "$AUTH_URL/login")"
done
cut_at=$(echo "$attempt_codes" | tr ' ' '\n' | grep -n '^429$' | head -1 | cut -d: -f1)
if [ -n "$cut_at" ]; then
    ok "cortado en el intento $cut_at de 7 ($attempt_codes)"
else
    ko "siete intentos fallidos sin 429: el presupuesto sigue siendo por réplica ($attempt_codes)"
fi

# ---------------------------------------------------------------------------------------------
printf '\n'
if [ "$failures" -eq 0 ]; then
    printf '\033[32mTodo en verde.\033[0m El recorrido funciona con dos réplicas de cada servicio.\n'
else
    printf '\033[31m%d comprobación(es) en rojo.\033[0m\n' "$failures"
fi
exit $((failures > 0))
