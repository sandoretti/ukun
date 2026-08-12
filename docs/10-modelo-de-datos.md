# Modelo de datos: tipos, restricciones, índices y relaciones

> **Fuente de verdad de:** tipos, `CHECK`, índices (justificados uno a uno por su consulta), política `ON DELETE`, instantáneas del pedido y convención de nomenclatura del esquema.
> **Léelo si:** vas a escribir un changelog de Liquibase o a diseñar una consulta nueva.
> **No decide:** el idioma del esquema (ADR-0018), ni cómo se escribe un changeset (skill `liquibase`), ni la frontera entre las dos bases (`08-identidad-y-seguridad.md`).
> **Idioma:** todos los nombres de tabla y columna de este documento están en inglés (ADR-0018) — el esquema nace así, no hay fase de renombrado.

Este documento completa `01-arquitectura.md` (que da la forma del ER objetivo) con el detalle que entra en los changelogs de Liquibase. Cada propuesta está aquí porque resuelve una de las siete consultas reales que exigen los requisitos funcionales — no por completitud teórica. La sobreingeniería descartada en `03-analisis-y-patrones.md` §4 (Redis, CQRS, arquitectura hexagonal completa) sigue aplicando aquí igual.

**Nota sobre el legacy:** `ukun-clientes`/`ukun-empleados` no están en marcha y sus datos son desechables (ADR-0007), así que **ninguna restricción de este documento se escalona por compatibilidad**. Las dos bases nuevas nacen completas y en inglés; el esquema legacy (`database/init.sql`) solo se cita como origen de un defecto o como referencia del comportamiento que hay que conservar. `ukun-clientes/` se borró del repo en v0.6 (issue #40): las rutas suyas que se citan en §5 se leen en el tag `v0.0-legacy`.

## 0. De dónde salen estas propuestas

| # | Consulta real | RF | Frecuencia |
|---|---|---|---|
| Q1 | catálogo filtrado por nombre (`LIKE` con comodín por delante) + tipo, ordenado por precio, paginado | RF-01 | alta, pública |
| Q2 | disponibilidad de los productos de la página en la tienda online (+ favorita) | RF-01 | alta, junto a Q1 |
| Q3 | historial de pedidos de un cliente, más reciente primero, con sus líneas | RF-04 | media |
| Q4 | bloqueo ordenado de las filas de stock de un carrito, en checkout | RF-04 | baja, crítica |
| Q5 | stock de mi tienda / productos **sin** fila de stock en mi tienda | RF-09 | media |
| Q6 | resolver `customer`/`employee` por `account_id` | `08-identidad-y-seguridad.md` §4 | **en cada petición autenticada** |
| Q7 | "¿este producto está referenciado?" antes de borrarlo | RF-10 | baja |
| Q8 | "¿este tipo tiene productos?" antes de borrarlo | RF-13 | baja |

Todo lo que sigue está justificado por una de estas ocho o por un invariante que el legacy ya rompe. Lo que no encaja en ninguna se descarta explícitamente, con su motivo.

## 1. Convenciones transversales

- **Todo timestamp es `TIMESTAMPTZ`, nunca `TIMESTAMP`.** Los contenedores corren en UTC y el desarrollador en `Europe/Madrid`; `LocalDateTime.now()` toma la zona de la JVM y se escribe sin desplazamiento, así que el mismo instante se lee distinto según quién consulte. Y en el cambio de hora de octubre la franja 02:00–03:00 es ambigua de forma **irrecuperable**: no hay manera posterior de saber si un pedido se hizo antes o después del salto.
- **`created_at`/`updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`** en las tablas maestras mutables (`product`, `product_type`, `store`, `customer`, `employee`, `store_stock`). No en `order_line` (su marca temporal es la del pedido), ni en `cart_item` (efímero), ni en `address`/`card` de perfil. En Java, `@CreationTimestamp`/`@UpdateTimestamp`, **sin triggers** de base de datos — un trigger es una segunda implementación de la misma regla que hay que depurar por separado.
- **Todo texto obligatorio lleva `CHECK (length(btrim(col)) > 0)`.** El legacy promete `@NotBlank` en la entidad y su base acepta `''`: es la misma divergencia entidad↔esquema que la regla 5 de la skill `liquibase` prohíbe.

## 2. Tipos y restricciones, tabla a tabla

| Tabla.columna | Queda | Por qué |
|---|---|---|
| `store.phone` | `VARCHAR(20) NOT NULL` + `CHECK` de formato E.164 estricto | el `VARCHAR(9)` del legacy solo cabe si el número va pelado, sin prefijo ni separadores |
| `address.postal_code` | `CHAR(5)` + `CHECK (postal_code ~ '^[0-9]{5}$')` | un CP español son 5 dígitos exactos |
| `store_stock.quantity` | `INTEGER NOT NULL` + `CHECK (quantity >= 0)` | la entidad legacy declara `@NotNull @Min(0)` sin que su base garantice ninguna de las dos. Es también la red de seguridad final del checkout (`03-analisis-y-patrones.md` §2) |
| `store_stock.version` | `BIGINT NOT NULL DEFAULT 0` | ADR-0008: concurrencia optimista de la edición manual |
| `store_stock` (PK) | `id BIGSERIAL PRIMARY KEY` + `UNIQUE (product_id, store_id)` | ADR-0015: identidad propia de línea de stock, sin perder el invariante de unicidad que en el legacy da la PK compuesta |
| `product.sku` | `VARCHAR(32) NOT NULL UNIQUE CHECK (sku ~ '^[A-Z0-9-]{3,32}$')` | ADR-0015: identificador de negocio que el legacy no tiene. RF-09 lo usa como clave de búsqueda del empleado |
| `cart_item.quantity`, `order_line.quantity` | `INTEGER NOT NULL` + `CHECK (quantity > 0)` | RF-03/RF-04 no contemplan cantidad 0 o negativa |
| `product.price`, `order_line.unit_price`/`total_price` | `DECIMAL(10,2) NOT NULL` + `CHECK (… >= 0)` | el tipo SQL del legacy ya era correcto — su defecto (#5) es puramente del lado Java (`Float`). El `CHECK` es la red |
| `order_line` | `CHECK (total_price = unit_price * quantity)` | invariante que el agregado promete, garantizado gratis por una línea de DDL |
| `customer_order.total` | `DECIMAL(10,2) NOT NULL CHECK (total > 0)` | RF-04: un carrito vacío debe rechazarse. El legacy **crea** pedidos con total 0; es el bug documentado en `02-requisitos.md` RF-04, y el `CHECK` lo hace imposible de reintroducir |
| `customer_order.placed_at` | `TIMESTAMPTZ NOT NULL` | ver §1 |
| `product_type.name`, `store.name` | `VARCHAR(255) NOT NULL UNIQUE` | el `init.sql` legacy siembra con `(SELECT id FROM tipo_producto WHERE nombre = 'Mesas')` y su código resuelve `findByNombre("Ukun Online")`: ambos asumen unicidad sin que la base la exija |
| `store.is_online` | `BOOLEAN NOT NULL DEFAULT false` **+ `CREATE UNIQUE INDEX ux_store_online ON store (is_online) WHERE is_online`** | el índice único parcial convierte "hay exactamente una tienda online" en un invariante de la base, no en una convención — es justo el invariante del que dependía el defecto #6 (query en constructor buscando `"Ukun Online"` por nombre) |
| `employee.job_title` | `VARCHAR(50) NOT NULL` + no-vacío | es una etiqueta; ningún comportamiento depende de ella (RF-12: el único rol real es el flag `is_admin`) |
| `account.email` (en `ukun_auth`) | `CITEXT NOT NULL UNIQUE` | el `UNIQUE` sin `NOT NULL` del legacy permite N cuentas sin credencial de login. RF-06 lo da por único y normalizado a minúsculas |
| `account.password_hash` | `VARCHAR(100) NOT NULL` | 60 para BCrypt, holgura para Argon2. Nunca un `VARCHAR(16)` de texto plano |
| `customer.email`/`employee.email` | `CITEXT NOT NULL`, **sin `UNIQUE`** | proyección de lectura, `08-identidad-y-seguridad.md` §2. Un `UNIQUE` aquí crearía una segunda fuente de verdad de la unicidad que puede divergir del refresco perezoso |
| `customer.address_id`, `customer.card_id` | `NOT NULL` | RF-06 exige ambas en el alta y RF-04 las necesita en checkout |
| `customer.name`/`last_name`, `employee.*` | `VARCHAR(100) NOT NULL` + no-vacío | `08-identidad-y-seguridad.md` §2: el perfil se queda en `ukun_app` |
| `customer.locale` | `CHAR(2) NOT NULL DEFAULT 'es' CHECK (locale IN ('es','en'))` | ADR-0019: preferencia de idioma persistida en el perfil, con navegador como respaldo solo para anónimos |
| `product.is_active` | `BOOLEAN NOT NULL DEFAULT true` | descatalogación lógica, ver §6 y ADR-0013 |
| `product_type.name` | además del `UNIQUE` de arriba, es **editable** | RF-13: renombrar un tipo es una operación de negocio, y no arrastra nada — la instantánea del pedido es `order_line.product_name`, no el tipo (§5) |
| `product.image_key` | `VARCHAR(255)` nullable | RF-10: la foto es opcional. Va con `FileStoragePort` (ADR-0017) |
| `employee.is_admin` | `BOOLEAN NOT NULL DEFAULT false` | RF-12: el único rol real dentro de empleado |

### `CITEXT` frente a `TEXT` + índice funcional

Se elige **`CITEXT`** para `account.email` y las proyecciones. La alternativa (`TEXT` + `CREATE UNIQUE INDEX ON account (lower(email))`) es más portable, pero obliga a recordar `lower()` en cada comparación — y Hibernate genera `where email = ?` a partir de un `findByEmail` derivado, así que la primera query derivada que alguien escriba se salta la normalización en silencio. `CITEXT` mueve esa garantía al tipo (`CREATE EXTENSION citext` en el baseline de `ukun_auth`). El coste (atadura al motor) es aceptable: la portabilidad de base de datos ya se descartó en `03-analisis-y-patrones.md` §4.

### La tabla `card`

```sql
CREATE TABLE card (
  id        BIGSERIAL PRIMARY KEY,
  last4     CHAR(4)     NOT NULL CHECK (last4 ~ '^[0-9]{4}$'),
  brand     VARCHAR(20) NOT NULL CHECK (brand IN ('VISA','MASTERCARD','AMEX','OTHER')),
  exp_month SMALLINT    NOT NULL CHECK (exp_month BETWEEN 1 AND 12),
  exp_year  SMALLINT    NOT NULL CHECK (exp_year BETWEEN 2024 AND 2100)
);
```

`number` y `cvv` no existen (`08-identidad-y-seguridad.md` §5, PCI-DSS). Y el `fecha_expiracion DATE` del legacy es también un error de modelado, no solo un residuo: una tarjeta caduca en un **mes**, no en un día, y se guarda como el día 1 de ese mes (`'2025-12-01'`), lo que significa literalmente "caducó el 1 de diciembre" cuando el negocio quiere decir "válida durante todo diciembre". Con dos `SMALLINT` mapeados a un `YearMonth` en Java, la ambigüedad desaparece.

### Descartado explícitamente

| Propuesta | Por qué no |
|---|---|
| `address.country` | un solo país hoy; se añade el día que haya envío internacional |
| `CHECK` de coherencia provincia↔código postal | los propios datos semilla del legacy ya son incoherentes (`'Madrid'` con CP `29990`); validarlo exige una tabla de prefijos que mantener, sin requisito de negocio detrás |
| Tabla de referencia para `employee.job_title` | cero comportamiento depende del valor |
| Cifrado a nivel de columna (`pgcrypto`) para datos de tarjeta | no hay nada sensible que cifrar — resuelve el problema equivocado |
| `product.version` (concurrencia optimista en la edición de producto, RF-10) | ver abajo |
| `product_type.is_active` (descatalogación lógica de tipos) | RF-13 no la pide y `product` es el único caso donde el borrado duro es imposible en la práctica (un producto vendido no se borra nunca). Un tipo sin productos sí se borra de verdad, y uno con productos es dato en uso: dos estados, no tres |

#### Por qué `product` no lleva `version` y `store_stock` sí

El requisito no funcional de `02-requisitos.md` §4 pide una estrategia de concurrencia **explícita**, no que siempre sea optimista. Para la edición de producto la estrategia es *último que escribe gana*, decidida y anotada aquí:

| | `store_stock` | `product` |
|---|---|---|
| Quién escribe | cualquier empleado, uno por tienda, sobre el mismo dato a la vez | un `admin`, sobre un catálogo global |
| Qué se pierde en una escritura perdida | unidades de inventario: el número queda mal y **nadie lo nota** hasta que el checkout vende de más | un nombre o un precio: se ve en pantalla al recargar y se vuelve a escribir |
| Estrategia | optimista (`version`) + pesimista en el descuento de checkout (ADR-0008) | último que escribe gana |

Añadir `version` a `product` costaría una columna y un campo en cada formulario de edición para protegerse de una carrera que exige dos admins editando el mismo producto en la misma ventana de segundos, con un daño visible y reversible. Si algún día hay más de un admin trabajando a la vez de forma habitual, es un changeset de una línea (`addColumn` + `@Version`) — pero entonces habrá un motivo, que hoy no existe.

## 3. Estado del pedido

**Decisión: la columna sí, la máquina de estados completa no.**

```sql
status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED'
  CONSTRAINT ck_customer_order_status CHECK (status IN ('CONFIRMED', 'CANCELLED'));
```

Dos valores, no seis: `CANCELLED` porque es el único que puede necesitarse antes de que exista logística real, y para que la columna nazca con más de un valor posible. `PAID`/`SHIPPED`/`DELIVERED` llegan en v1.1+ con la pasarela real (`04-roadmap.md`), cada uno en su propio changeset. Una tabla `order_status_history` es v1.1+, y solo si hay más de una transición que registrar.

En el contrato, el campo entra desde v0.1: `OrderResponse.status: enum [CONFIRMED]` en `ukun-api.yaml`. El OpenAPI se congela en SINC-1 y el frontend genera tipos de él; reservar el campo cuesta tres líneas de YAML y evita un cambio de contrato posterior.

### Representación: `VARCHAR` + `CHECK`, no enum nativo (ver ADR-0012)

| Opción | Añadir un valor nuevo | Mapeo en Hibernate | Veredicto |
|---|---|---|:-:|
| `CREATE TYPE order_status AS ENUM (…)` | `ALTER TYPE … ADD VALUE` corre en transacción en PG 17, pero **el valor nuevo no se puede usar en esa misma transacción**. Como Liquibase envuelve cada changeset en una, añadir un estado y usarlo en un `UPDATE` exige dos changesets obligatoriamente. Y **quitar** un valor no se puede, nunca | necesita `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`; el driver lo envía como `unknown` y toda query nativa exige `?::order_status` | ❌ |
| **`VARCHAR(20)` + `CHECK (status IN (…))`** | un `dropCheckConstraint` + `addCheckConstraint` en un único changeset, reversible y legible en el diff | `@Enumerated(EnumType.STRING)`: soporte de primera clase, cero configuración | ✅ |
| tabla `order_status(code PK)` + FK | un `INSERT` | o un `@ManyToOne` (join extra en cada lectura de pedido) o un `enum` Java **más** una tabla a sincronizar a mano | ❌ |

El criterio decisivo no es el almacenamiento (la diferencia de bytes es ruido a este volumen), es cuánta fricción cuesta añadir un valor en un changeset. La tabla de referencia solo se justificaría si el estado llevara datos asociados (etiqueta traducible, orden de presentación, transiciones permitidas) — y aquí no lleva ninguno: las transiciones viven en el agregado `Order.cancel()`, no en la base.

## 4. Índices, uno a uno con su consulta

### Qué FKs necesitan índice de verdad

`03-analisis-y-patrones.md` citaba cuatro FKs sin índice; **dos son redundantes**:

- `cart_item` → `PRIMARY KEY (customer_id, product_id)`: `customer_id` **ya es la columna líder** del índice de la PK.
- `order_line` → `PRIMARY KEY (order_id, product_id)`: `order_id` **ya es líder**.
- `store_stock` → con la PK subrogada de ADR-0015, `store_id` **no** es líder de la `UNIQUE (product_id, store_id)` → el índice sí hace falta.
- `customer_order` → `PRIMARY KEY (id)` solo → `customer_id` sin índice, sí hace falta.

No cambia la intención (cubrir toda FK consultada), solo la lista. Y revela cuáles son las FKs realmente desnudas: la **segunda** columna de cada PK compuesta (`cart_item.product_id`, `order_line.product_id`) — que es justo lo que Q7 (RF-10) y la comprobación de integridad referencial de Postgres en cada `DELETE FROM product` necesitan escanear.

### Los índices que entran

| Índice | Consulta que lo motiva | Por qué esa forma |
|---|---|---|
| `ux_customer_account_id UNIQUE ON customer (account_id)`, `ux_employee_account_id UNIQUE ON employee (account_id)` | **Q6** — `08-identidad-y-seguridad.md` §4 decidió no meter `storeId` en el token, así que el API resuelve el empleado por `account_id` en cada operación | el índice más caliente del sistema: una vez por petición autenticada. `UNIQUE` además de por rendimiento, porque es la única forma de hacer cumplir ADR-0006 (cliente y empleado mutuamente excluyentes) en la base y no solo en un servicio |
| `ix_customer_order_customer_placed ON customer_order (customer_id, placed_at DESC)` | **Q3** — `WHERE customer_id = ? ORDER BY placed_at DESC LIMIT n` | igualdad primero, orden después: el índice entrega las filas ya ordenadas y el planificador elimina el nodo de ordenación, que es lo que abarata la paginación. Sustituye al índice simple de FK: la columna líder ya cubre también la comprobación referencial |
| `ix_store_stock_store ON store_stock (store_id, product_id)` | **Q5** — `WHERE store_id = ?` (stock de mi tienda) | con la PK subrogada de ADR-0015, la `UNIQUE (product_id, store_id)` es inservible aquí porque `store_id` no es líder. Dos columnas para que sirva también al join hacia `product` y a la comprobación de FK al borrar una tienda |
| `ix_cart_item_product ON cart_item (product_id)`, `ix_order_line_product ON order_line (product_id)` | **Q7** — RF-10 | son las FKs realmente sin índice (la segunda columna de cada PK compuesta) |
| `ix_employee_store ON employee (store_id)` | RF-11 — listar empleados de mi tienda | FK sin índice + comprobación al borrar una tienda |
| `ix_product_type ON product (product_type_id)` | Q1 (filtro por tipo), **Q8** (RF-13) y FK hacia `product_type` | con 24 filas el planificador lo ignorará para Q1 (seq scan), pero Q8 es un `countByProductTypeId` de igualdad exacta y la comprobación referencial de cada `DELETE FROM product_type` lo recorre igual: el índice ya está y sirve las tres cosas. **RF-13 no añade ningún índice nuevo** |
| `ux_store_online`, `ux_product_type_name`, `ux_store_name`, `ux_store_stock`, `ux_product_sku` | ver §2 | son constraints, no optimizaciones |
| `ux_idempotency_customer_key UNIQUE ON idempotency_key (customer_id, key)` | ADR-0016 | igualdad en ambas columnas, es la única consulta de la tabla |

### El anti-join de RF-09: sin índice nuevo, con la consulta bien escrita

"Productos sin fila de stock en mi tienda" ya está servido por la **`UNIQUE (product_id, store_id)`** de `store_stock`: como `NOT EXISTS` correlacionado, cada sonda es una búsqueda exacta sobre ese índice. Cero índices nuevos.

Lo que no se traslada es la forma de la consulta. El legacy hace:

```java
@Query("SELECT p FROM Producto p WHERE p.id NOT IN (SELECT sp.id.productoId FROM StockProducto sp WHERE sp.tienda = ?1)")
```

`NOT IN` sobre una subconsulta tiene semántica de tres valores: un solo `NULL` en la subconsulta vacía el resultado entero. Ahí no puede ocurrir (`producto_id` es parte de la clave), pero es una trampa que solo se sostiene por accidente, y además fuerza al planificador a un subplan materializado en vez de un anti-join real. Se escribe como `NOT EXISTS` (o `LEFT JOIN … WHERE s.product_id IS NULL`) — defecto #21.

### Búsqueda por nombre de RF-01: comparativa y recomendación

Q1 es `lower(name) LIKE '%texto%'` — **con comodín por delante**.

| Opción | ¿Sirve para `%texto%`? | Coste | Veredicto |
|---|:-:|---|---|
| btree funcional `lower(name) text_pattern_ops` | No, solo acelera prefijos `texto%` | 1 índice | ❌ no cubre la consulta real |
| GIN con `pg_trgm` sobre `lower(name)` | Sí | extensión + índice varias veces mayor que la tabla a este tamaño + escrituras más caras | ✅ el día que crezca |
| `tsvector` + GIN | cambia la semántica: busca palabras, no subcadenas (`"mes"` no encuentra `"Mesa"` sin `to_tsquery('mes:*')`); exige fijar configuración `spanish` | extensión conceptual real | ❌ resolvería otro problema |
| **Ninguno** | sí, por seq scan | 0 | ✅ **hoy** |

**Recomendación: ninguno.** Con 24 filas en una sola página de disco, Postgres hace seq scan aunque el índice exista. Se documenta el umbral y el changeset exacto que lo activa:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX ix_product_name_trgm ON product USING gin (lower(name) gin_trgm_ops);
```

**Umbral de activación:** el catálogo supera ~5.000 filas **o** el p95 de `http.server.requests{uri=/api/products}` (Micrometer, v1.0) se sale del objetivo. No "por si acaso".

### Dos bugs de RF-01 que ningún índice arregla (defectos #22 y #23)

1. **Acentos.** Buscar `"sofa"` no encuentra `"Sofá de cuero"`, ni `"lampara"` encuentra `"Lámpara de pie"` — y el catálogo semilla está lleno de acentos. Se arregla en la consulta, no en el índice: `unaccent(lower(p.name)) LIKE unaccent(lower(:q))` con `CREATE EXTENSION unaccent`. `unaccent()` es `STABLE`, no `IMMUTABLE`, así que no se puede indexar directamente sin envolverla en una función propia — un motivo más para no indexar nada hoy.
2. **`ORDER BY price` sin desempate es paginación inestable**, y el catálogo semilla ya tiene un empate real: `Litera infantil` y `Sofá de cuero`, ambos a `399.99`. Con `Page<T>`, el mismo producto puede salir en la página 1 y en la 2, o en ninguna. **Regla: todo `Sort` del API lleva `id` como último criterio.** Coste cero.

Ambos entran en **v0.3**, con el endpoint de catálogo.

### Índices que NO merecen la pena todavía

| Descartado | Motivo |
|---|---|
| Cualquier índice de búsqueda por nombre | 24 filas — ver comparativa arriba |
| `idempotency_key(created_at)` para la purga diaria | tabla de decenas/centenas de filas, un `DELETE` diario es un seq scan de una página. Reconsiderar por encima de ~100k filas, y entonces probablemente BRIN, no btree |
| `account(created_at) WHERE NOT enabled` (reconciliación de la saga) | mismo argumento, con menos filas todavía |
| `cart_item(customer_id)`, `order_line(order_id)` | ya son columna líder de su PK compuesta |
| `customer_order(placed_at)` a secas | ningún RF pide un listado global de pedidos por fecha |
| `product(price)` | solo serviría a un `ORDER BY` sin filtro previo, y con 24 filas ordenar es gratis |
| Índices sobre `address`/`card` de perfil | solo se acceden por PK |
| GIN sobre `product.description` | ningún RF busca en la descripción |
| Índices de cobertura (`INCLUDE`) | optimización de tercer orden sobre tablas que caben enteras en caché |

## 5. Modelado de relaciones

### Cardinalidad (defecto #14): se declara bien desde el principio

`product.product_type_id` y `employee.store_id` son N:1, no 1:1 como los declara el legacy. Se escriben `@ManyToOne(fetch = FetchType.LAZY)`. El daño que causa el error del legacy no es solo semántico: `@OneToOne` en el lado propietario es EAGER por defecto, una de las causas del N+1 (defecto #12).

Además, con la descomposición en módulos de Spring Modulith (ADR-0017), estas dos asociaciones y las otras cuatro que cruzan frontera de módulo (`StoreStock.product`/`.store`, `OrderLine.product`, `CartItem.product`, `CustomerOrder.customer`, `Customer.favoriteStore`) no se navegan como `@ManyToOne`: son un `Long` desnudo resuelto vía la API pública del módulo dueño — detalle completo en `docs/14-modules.md` §3.

### El bug de integridad histórica (defecto #19, ver ADR-0011)

RF-04 dice, literalmente: *"El pedido copia la dirección y la tarjeta actuales del cliente en el momento de confirmar."* La implementación legacy **no copia: comparte**. Cadena de evidencia:

1. `ukun-clientes/.../entity/Pedido.java`, constructor `Pedido(Cliente)`: `this.direccion = cliente.getDireccion();` — copia la **referencia**, así que `pedido.direccion_id` acaba apuntando a la misma fila que `cliente.id_direccion`.
2. `Cliente` declara `@OneToOne(cascade = CascadeType.ALL)` sobre `direccion` y sobre `tarjeta`.
3. `AbsController` está anotado `@SessionAttributes("cliente")`, así que el `@ModelAttribute("cliente")` de `PerfilController.editar` enlaza el formulario **sobre la instancia guardada en sesión**, que ya lleva `direccion.id`/`tarjeta.id`.
4. `clienteService.save(...)` cascadea → `UPDATE direccion SET calle = … WHERE id = <el mismo id de siempre>`.

**Consecuencia:** un cliente que se muda de casa y edita su perfil **reescribe la dirección de envío de todos los pedidos que ha hecho en su vida**. El historial de pedidos no es un registro de lo que ocurrió, es una vista del estado actual del perfil disfrazada de historial. Es exactamente el comportamiento que **no** hay que conservar.

#### La solución: partir por ciclo de vida, no por forma

| Dato | Dueño | ¿Mutable? | Dónde vive |
|---|---|:-:|---|
| dirección de perfil | el cliente | sí | `address`, referenciada por `customer.address_id` |
| dirección de tienda | la tienda | sí | `address`, referenciada por `store.address_id` |
| **dirección de envío del pedido** | el pedido | **no, jamás** | columnas embebidas en `customer_order`: `shipping_street`, `shipping_city`, `shipping_province`, `shipping_postal_code` |
| tarjeta de perfil | el cliente | sí | `card`, referenciada por `customer.card_id` |
| **medio de pago del pedido** | el pedido | **no** | `customer_order.payment_last4`, `payment_brand`, `payment_exp_month`, `payment_exp_year` |
| **nombre del producto en la línea** | la línea | **no** | `order_line.product_name VARCHAR(255) NOT NULL` |

Por qué columnas embebidas en `customer_order` y no las otras dos opciones obvias:

- *Copiar una fila nueva dentro de la misma tabla `address`*: esa copia sigue siendo tan mutable como cualquier otra fila — nada en el esquema dice "esta no se toca". El invariante viviría solo en la disciplina del código, que es exactamente cómo se produjo el bug del legacy.
- *Una tabla `order_address` aparte*: añade un join a una relación 1:1 que se lee siempre junto al pedido y nunca por sí sola.
- Con columnas embebidas la inmutabilidad es **estructural**: no existe ninguna fila que otra feature pueda actualizar. En Java es un `@Embeddable ShippingAddress` + `@AttributeOverride`, sin entidad nueva. Coste: la dirección se duplica por pedido (~120 bytes) — irrelevante a este volumen.

Efecto colateral valioso: no existen FK `customer_order.address_id`/`card_id`, y con ellas desaparece el problema de "no puedo cambiar mi tarjeta porque hay pedidos que la referencian".

**El tercer campo con el mismo problema:** RF-04 congela el *precio* de la línea pero no el *nombre* del producto. Si un admin renombra "Sofá de cuero" a "Sofá de piel" (RF-10 lo permite), todos los pedidos históricos cambiarían el nombre que muestran. `order_line.product_name` como instantánea lo cierra, y de paso hace legible un pedido aunque el producto se haya descatalogado.

## 6. Política de borrado (ver ADR-0013)

El `init.sql` legacy no tiene **ni una sola** cláusula `ON DELETE` → todas sus FK son `NO ACTION`, que por casualidad es la política correcta para la mayoría. Aquí se declara explícitamente en cada una, porque una política por omisión es una política que nadie ha decidido.

| FK | Acción | Por qué |
|---|:-:|---|
| `cart_item.customer_id → customer` | `CASCADE` | el carrito es estado efímero, no historial: sin cliente no significa nada |
| `cart_item.product_id → product` | `RESTRICT` | RF-10 |
| `order_line.order_id → customer_order` | `CASCADE` | las líneas son parte del agregado `Order`; coincide con `orphanRemoval = true` en Java |
| `order_line.product_id → product` | `RESTRICT` | RF-10 |
| `customer_order.customer_id → customer` | `RESTRICT` | un pedido es un registro contable: borrar al cliente no puede borrar la venta |
| `store_stock.product_id → product` | `RESTRICT` | RF-10 |
| `store_stock.store_id → store` | `CASCADE` | el stock es un atributo de la tienda; si la tienda desaparece, su stock también |
| `employee.store_id → store` | `RESTRICT` | no se cierra una tienda con gente dentro |
| `customer.favorite_store_id → store` | `SET NULL` | la columna ya es nullable y RF-05/RF-06 la tratan como opcional |
| `customer.address_id`, `customer.card_id`, `store.address_id` | `RESTRICT` | el borrado siempre va en la dirección contraria |
| `product.product_type_id → product_type` | `RESTRICT` | **RF-13**: ahora sí hay UI para borrar tipos, y esta es la garantía de que un tipo con productos no desaparece. La versión anterior de esta tabla decía "si algún día la hay" — ese día es RF-13 |

### Dónde se hacen cumplir RF-10 y RF-13: en dos sitios, con papeles distintos

- La **FK `RESTRICT` es la garantía**: no depende de que el servicio se acuerde, sobrevive a un `DELETE` manual desde `psql` y cierra la carrera entre comprobación y borrado.
- La **comprobación en el caso de uso es la experiencia**. RF-10 pide "un mensaje claro" — claro significa decir cuál es la referencia, y una violación de FK solo da un `SQLState 23503`. `DeleteProductUseCase` cuenta primero (`countByProductId` sobre `order_line`, `store_stock`, `cart_item`, todos con índice — ver §4) y lanza `ProductInUseException` → `409 PRODUCT_IN_USE` (`docs/12-error-contract.md`) con `{inOrders, inStock, inCarts}`.
- **Lo mismo, un nivel más arriba, para los tipos (RF-13)**: `DeleteProductTypeUseCase` hace `countByProductTypeId` (un solo conteo, servido por `ix_product_type`) y lanza `ProductTypeInUseException` → `409 PRODUCT_TYPE_IN_USE` con `{inProducts}`. El conteo **no** filtra por `is_active`: un tipo con solo productos descatalogados sigue siendo un tipo en uso, porque la FK también lo es.
- Un `@ExceptionHandler(DataIntegrityViolationException.class)` traduce a `409 REFERENTIAL_INTEGRITY` cualquier `23503` que se cuele — red de seguridad, no ruta principal.
- La unicidad de `product.sku` y de `product_type.name` sigue el mismo reparto: `ux_product_sku`/`ux_product_type_name` son la garantía, el caso de uso comprueba antes para poder decir **qué campo** choca (`409 DUPLICATE_VALUE` con `{resource, field}`), y el `23505` que se cuele cae en el mismo handler genérico.

### Borrado lógico: uno solo, y no por auditoría

- **`product`: sí.** `is_active BOOLEAN NOT NULL DEFAULT true`. Con `RESTRICT`, un producto que se ha vendido alguna vez no se puede borrar nunca, así que RF-10 en la práctica significa "casi nada es borrable". La salida real de negocio es *descatalogar*: desaparece del catálogo (RF-01 filtra `WHERE is_active`) y de las pantallas de stock (RF-09), y el historial queda íntegro. El `DELETE` duro se reserva para productos nunca referenciados.
- **`customer`/`employee`: no.** "Borrar" una cuenta es `account.enabled = false` en `ukun-auth`, columna que ya existe por diseño; un segundo flag en `ukun_app` sería una segunda fuente de verdad para lo mismo.
- **`customer_order`: nunca se borra**, ni lógica ni físicamente. Cancelar es un `status` (§3), no un borrado.
- **`product_type`: no.** RF-13 le da borrado real, y es viable justo porque un tipo *sin* productos no está referenciado por nada: no hay ningún caso en que el borrado sea imposible para siempre, que es lo único que justificaba el borrado lógico en `product`. Con productos, se rechaza y se reasignan.
- **`store`**: sin requisito de borrado, sin decisión hasta que lo haya.

## 7. Convenciones de nomenclatura

El idioma del esquema (inglés, sin fase de renombrado) lo decide **ADR-0018**, que además lleva el mapa completo de nombres ES→EN e incluye por qué `pedido` es `customer_order` y no `order`, y por qué `carrito` es `cart_item` y no `cart`. Aquí solo la forma:

| Regla | Ejemplo correcto | Contraejemplo del legacy |
|---|---|---|
| `snake_case`, minúsculas | `password_hash` | `cuenta.contrasenna` |
| tablas en singular | `product`, `store_stock` | (ya se cumple) |
| PK siempre `id` | `product.id` | (ya se cumple) |
| FK = `<tabla_destino>_id`, sufijo, nunca prefijo | `address_id` | `cliente.id_direccion` **vs** `tienda.direccion_id` (las dos formas en el mismo esquema), `producto.tipo` (FK sin sufijo) |
| booleanos con prefijo `is_`/`has_` | `is_online`, `is_admin` | `empleado.admin` |
| timestamps: par estándar `created_at`/`updated_at`; los de dominio, `<participle>_at` | `created_at`, `discontinued_at` | `pedido.fecha` (¿fecha de qué?) |
| objetos de esquema con prefijo de tipo | `ix_customer_order_customer_placed`, `ux_store_online`, `ck_customer_order_status` | anónimo (Postgres autogenera `pedido_cliente_id_fkey`, imposible de referenciar en un `dropConstraint` sin adivinarlo) |

## 8. Cuándo entra cada cosa

**Un baseline completo por base, en el release en que nace su servicio.** Sin datos que preservar y sin legacy que romper, escalonar el DDL solo añadiría changesets sin ganar nada:

| Base | Baseline | Contenido |
|---|:-:|---|
| `ukun_app` | **v0.2** | todas las tablas de este documento con sus tipos, `CHECK`, `UNIQUE`, índices y `ON DELETE` definitivos: `product`, `product_type`, `store`, `address`, `store_stock`, `customer`, `employee`, `cart_item`, `customer_order`, `order_line`, `card`, `idempotency_key`, `audit_log` |
| `ukun_auth` | **v0.4** | `CREATE EXTENSION citext` · `account`, `account_role` · `registration_request` · las tablas de `oauth2_registered_client`/`oauth2_authorization` que aporta el Authorization Server |

Los datos demo van en changesets aparte con `context: demo` (skill `liquibase`), no en el baseline.

Después del baseline, un changeset nuevo solo entra si el diseño **descubre** algo al implementar la feature — no por calendario. Lo único ya previsto:

| Release | Cambio | Motivo |
|:-:|---|---|
| **v0.6** | `checkout_attempt` + `checkout_attempt_line` (`releases/v0.6/003-checkout-attempt.yaml`) | **descubierto al implementar el checkout**, que es exactamente el caso que esta tabla contempla: ADR-0016 exige que el intento congele el precio de cada línea y lleve un `expiresAt`, y que confirmar con un `attemptId` inexistente o caducado sea `410 CHECKOUT_EXPIRED`. La única tabla que el baseline creó para ese endpoint es `idempotency_key`, cuya fila —según la tabla del propio ADR— se inserta **al confirmar**: no tiene dónde guardar las líneas congeladas ni qué buscar para decidir si el intento existe. Sin índice propio (única consulta, por PK) y con `ON DELETE CASCADE` en ambas FK: un intento es un borrador de 30 minutos, no historia |
| v1.1+ | valores nuevos de `status` (`PAID`/`SHIPPED`/`DELIVERED`) + `order_status_history` | llegan con la pasarela de pago real (`04-roadmap.md`) |
| v1.1+ | `pg_trgm` + `ix_product_name_trgm` | solo si se cruza el umbral de §4 |
| v1.1+ | token de pasarela de pago en `card` | sustituye a `last4`/`brand` como dato de negocio |

Ver ADR-0011 (instantáneas en el pedido), ADR-0012 (estado como `VARCHAR`+`CHECK`), ADR-0013 (política de borrado), ADR-0015 (SKU e identidad de stock), ADR-0018 (idioma) y ADR-0019 (i18n, columna `locale`) para el detalle de las decisiones con su alternativa descartada.
