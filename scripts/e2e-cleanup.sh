#!/usr/bin/env bash
# Borra de la base local lo que dejan escrito los recorridos de Playwright (apps/ukun-web/e2e).
#
# Existe porque el smoke da de alta datos nuevos en CADA ejecución —una persona, un empleado, un
# tipo, un producto con su foto, un pedido— y nunca los borra: los correos y los SKU llevan marca de
# tiempo justamente para que dos pasadas seguidas no choquen (`e2e/fixtures/staff.ts`). Con el
# tiempo eso convierte el catálogo y el back-office locales en un vertedero de «Tipo E2E …» que
# estorba para mirar cualquier pantalla a ojo.
#
# Hasta ahora la única salida era `db-reset`, que borra el volumen entero: sirve, pero cuesta el
# arranque de Liquibase y se lleva por delante lo que hayas creado tú a mano probando. Esto es la
# versión dirigida: toca solo las filas que casan con los patrones que generan las fixtures, y deja
# intactos los datos demo del changelog.
#
# Uso (desde cualquier sitio; el stack tiene que estar levantado):
#   scripts/e2e-cleanup.sh              # borra
#   scripts/e2e-cleanup.sh --dry-run    # solo cuenta lo que borraría
#
# Como `db-reset`, actúa sobre el proyecto de compose ACTIVO: desde un worktree con su `.env.ports`
# limpia su propia base y no la del checkout principal.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

dry_run=false
case "${1-}" in
  --dry-run) dry_run=true ;;
  '') ;;
  *) echo "Uso: $0 [--dry-run]" >&2; exit 2 ;;
esac

# Mismo apaño que el Tiltfile: `.env.ports` decide el nombre del proyecto cuando esto corre desde un
# worktree, y sin él valen los valores base de un checkout único.
if [[ -f .env.ports ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env.ports
  set +a
fi
project_name=${COMPOSE_PROJECT_NAME:-ukun}

dc() { docker compose -p "$project_name" -f compose.yaml -f compose.dev.yaml "$@"; }

if ! dc ps --status running --services 2>/dev/null | grep -qx db; then
  echo "No hay contenedor 'db' corriendo en el proyecto '$project_name'. Arranca \`tilt up\`." >&2
  exit 1
fi

# Las credenciales las pone el propio contenedor: así este script no tiene que leer `.env` ni
# arriesgarse a que la contraseña acabe en la línea de órdenes o en el historial del shell.
db_user=$(dc exec -T db printenv POSTGRES_USER | tr -d '\r')
app_db=$(dc exec -T db printenv POSTGRES_DB | tr -d '\r')
# La base de identidad está fijada literal en `compose.yaml` (no viene de `.env`), igual aquí.
auth_db=ukun_auth

# Los tres patrones son el contrato con las fixtures. Si alguna vez cambia cómo se generan los
# valores únicos del smoke, se cambian aquí también o esto deja de limpiar sin decir nada:
#   e2e-<ts>@example.com / e2e-staff-<ts>@example.com  ->  fixtures/customer.ts, fixtures/staff.ts
#   E2E-<ts>                                           ->  fixtures/staff.ts newSku()
#   Tipo E2E <ts>                                      ->  fixtures/staff.ts newProductTypeName()
email_pattern='e2e-%@example.com'
sku_pattern='E2E-%'
type_pattern='Tipo E2E %'

psql_app() {
  dc exec -T db psql -q -v ON_ERROR_STOP=1 -U "$db_user" -d "$app_db" \
    -v email="$email_pattern" -v sku="$sku_pattern" -v type="$type_pattern" "$@"
}

psql_auth() {
  dc exec -T db psql -q -v ON_ERROR_STOP=1 -U "$db_user" -d "$auth_db" \
    -v email="$email_pattern" "$@"
}

report() {
  psql_app <<'SQL'
SELECT 'clientes'          AS dato, count(*) FROM customer      WHERE email LIKE :'email'
UNION ALL SELECT 'empleados',       count(*) FROM employee      WHERE email LIKE :'email'
UNION ALL SELECT 'productos',       count(*) FROM product       WHERE sku   LIKE :'sku'
UNION ALL SELECT 'tipos',           count(*) FROM product_type  WHERE name  LIKE :'type'
UNION ALL SELECT 'pedidos',         count(*) FROM customer_order
     WHERE customer_id IN (SELECT id FROM customer WHERE email LIKE :'email');
SQL
  psql_auth <<'SQL'
SELECT 'cuentas' AS dato, count(*) FROM account WHERE email LIKE :'email';
SQL
}

echo "Proyecto de compose: $project_name"
echo
echo "Residuos del smoke en la base:"
report

if $dry_run; then
  echo "(--dry-run: no se ha borrado nada)"
  exit 0
fi

# ---------------------------------------------------------------------------
# La base de la aplicación, en una sola transacción y en el orden que imponen las FK.
#
# Casi todas son `ON DELETE RESTRICT` a propósito (`docs/10-modelo-de-datos.md`), así que aquí no hay
# margen: lo que cuelga va antes que aquello de lo que cuelga. Las tablas temporales fijan el
# conjunto ANTES del primer borrado; si se filtrase por el patrón en cada `DELETE`, el de `customer`
# ya no encontraría las filas que los anteriores necesitan para ubicar sus dependencias.
psql_app <<'SQL'
BEGIN;

CREATE TEMP TABLE tmp_customer ON COMMIT DROP AS
  SELECT id, account_id, address_id, card_id FROM customer WHERE email LIKE :'email';
CREATE TEMP TABLE tmp_employee ON COMMIT DROP AS
  SELECT id, account_id FROM employee WHERE email LIKE :'email';
CREATE TEMP TABLE tmp_product ON COMMIT DROP AS
  SELECT id FROM product WHERE sku LIKE :'sku';
CREATE TEMP TABLE tmp_type ON COMMIT DROP AS
  SELECT id FROM product_type WHERE name LIKE :'type';
CREATE TEMP TABLE tmp_stock ON COMMIT DROP AS
  SELECT id FROM store_stock WHERE product_id IN (SELECT id FROM tmp_product);

-- Un pedido es del smoke si lo hizo una persona del smoke, o si compró un producto del smoke. Lo
-- segundo no es celo: `order_line` referencia el producto con RESTRICT, así que un pedido de un
-- cliente demo con una línea de producto E2E-… impediría borrar ese producto, y el mensaje sería un
-- error de clave ajena a media transacción en vez de una limpieza.
CREATE TEMP TABLE tmp_order ON COMMIT DROP AS
  SELECT id FROM customer_order WHERE customer_id IN (SELECT id FROM tmp_customer)
  UNION
  SELECT order_id FROM order_line WHERE product_id IN (SELECT id FROM tmp_product);

-- El registro de auditoría no tiene FK a nada (`docs/11-observabilidad-y-logging.md` §4: sobrevive
-- al recurso que describe), así que se limpia a mano por las dos vías por las que entra: quién actuó
-- —las cuentas del smoke— y sobre qué, que incluye lo que un admin demo creó por encargo del smoke.
DELETE FROM audit_log
 WHERE actor_account_id IN (SELECT account_id FROM tmp_customer
                            UNION SELECT account_id FROM tmp_employee)
    OR (resource_type = 'product'        AND resource_id IN (SELECT id::text FROM tmp_product))
    OR (resource_type = 'product_type'   AND resource_id IN (SELECT id::text FROM tmp_type))
    OR (resource_type = 'customer'       AND resource_id IN (SELECT id::text FROM tmp_customer))
    OR (resource_type = 'employee'       AND resource_id IN (SELECT id::text FROM tmp_employee))
    OR (resource_type = 'customer_order' AND resource_id IN (SELECT id::text FROM tmp_order))
    OR (resource_type = 'store_stock'    AND resource_id IN (SELECT id::text FROM tmp_stock))
    -- Y la fila de stock que el recorrido de empleada crea y borra sobre un producto DEMO, que no
    -- cae por ninguna de las vías de arriba: la hizo una cuenta demo sobre un producto demo. Se la
    -- reconoce porque su `resource_id` ya no existe en `store_stock`. Lo que sí se queda son los
    -- STOCK_UPDATED sobre stock demo que sigue vivo: eso es indistinguible de trabajo de verdad, y
    -- ante la duda una entrada de auditoría se conserva.
    OR (resource_type = 'store_stock'
        AND NOT EXISTS (SELECT 1 FROM store_stock s WHERE s.id::text = audit_log.resource_id));

DELETE FROM idempotency_key
 WHERE customer_id IN (SELECT id FROM tmp_customer) OR order_id IN (SELECT id FROM tmp_order);

DELETE FROM checkout_attempt
 WHERE customer_id IN (SELECT id FROM tmp_customer)
    OR id IN (SELECT attempt_id FROM checkout_attempt_line
               WHERE product_id IN (SELECT id FROM tmp_product));

DELETE FROM cart_item
 WHERE customer_id IN (SELECT id FROM tmp_customer) OR product_id IN (SELECT id FROM tmp_product);

DELETE FROM customer_order WHERE id IN (SELECT id FROM tmp_order);  -- arrastra order_line en cascada
DELETE FROM store_stock    WHERE product_id IN (SELECT id FROM tmp_product);
DELETE FROM product        WHERE id IN (SELECT id FROM tmp_product);
DELETE FROM product_type   WHERE id IN (SELECT id FROM tmp_type);
DELETE FROM customer       WHERE id IN (SELECT id FROM tmp_customer);
DELETE FROM employee       WHERE id IN (SELECT id FROM tmp_employee);

-- La dirección y la tarjeta van DESPUÉS del cliente: son suyas y solo suyas (ADR-0011 obliga a que
-- el pedido copie sus datos en vez de compartir la fila, que es justo el defecto del legacy), pero
-- el cliente las referencia con RESTRICT mientras existe.
DELETE FROM card    WHERE id IN (SELECT card_id    FROM tmp_customer);
DELETE FROM address WHERE id IN (SELECT address_id FROM tmp_customer);

COMMIT;
SQL

# La base de identidad va la segunda: primero se sueltan las referencias del dominio a la cuenta y
# después la cuenta. Al revés, un fallo a mitad dejaría clientes apuntando a cuentas que ya no están.
#
# `account_role` y `registration_request` cuelgan de `account` en cascada, así que no hace falta
# nombrarlas. `oauth2_authorization` sí: guarda las sesiones por `principal_name` y no tiene FK.
psql_auth <<'SQL'
BEGIN;
DELETE FROM oauth2_authorization WHERE principal_name LIKE :'email';
DELETE FROM account WHERE email LIKE :'email';
COMMIT;
SQL

# ---------------------------------------------------------------------------
# Las fotos subidas por el recorrido de admin (RF-10), que viven fuera de la base: el bind mount de
# `resources/uploads`. Borrar la fila del producto deja el fichero, y son ~100 KB por pasada.
#
# El criterio es doble y por eso es seguro: solo ficheros con nombre `<uuid>.<ext>` —el que genera
# LocalFileSystemStorage, mientras que los del changelog demo son `mesa-madera.jpg` y demás— y solo
# los que ya no referencia ninguna fila de `product`. Eso arrastra también las de ejecuciones
# anteriores a este script.
#
# El directorio es el de ESTE checkout, igual que la base es la del proyecto de compose de este
# checkout: las dos mitades de la limpieza tienen que mirar al mismo stack. Es coherente porque el
# bind mount de `compose.yaml` es relativo (`./resources/uploads`), así que un worktree con su
# propio stack tiene su propio directorio; y si el worktree NO tiene stack propio, la comprobación
# de `db` de más arriba ya ha parado el script antes de llegar aquí.
uploads_dir="$repo_root/resources/uploads"
removed_images=0
if [[ -d $uploads_dir ]]; then
  referenced=$(psql_app -At -c "SELECT image_key FROM product WHERE image_key IS NOT NULL")
  while IFS= read -r file; do
    name=$(basename "$file")
    [[ $name =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.[A-Za-z0-9]+$ ]] || continue
    grep -qxF "$name" <<<"$referenced" && continue
    rm -f -- "$file"
    removed_images=$((removed_images + 1))
  done < <(find "$uploads_dir" -maxdepth 1 -type f)
fi

echo
echo "Hecho. Imágenes huérfanas borradas: $removed_images"
echo "Lo que queda del smoke (debería ser todo 0):"
report

# El stock que el recorrido consumió NO se repone: sus cantidades son datos demo modificados, no
# filas nuevas, y adivinar cuál era el valor original sería inventárselo. Para volver al catálogo
# recién sembrado, `db-reset`.
