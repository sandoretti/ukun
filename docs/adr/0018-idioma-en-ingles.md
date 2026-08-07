# ADR-0018: Inglés en código, esquema y contrato, revirtiendo la convención de vocabulario mixto

## Estado
Aceptado — revierte una decisión previamente aceptada de este mismo repositorio

## Contexto
`10-modelo-de-datos.md` fijaba en su versión anterior, con motivo explícito: *"vocabulario de dominio en español, vocabulario de infraestructura en inglés — `producto`, `es_online`, `descatalogado_en` porque son conceptos del negocio; `created_at`, `updated_at`, `version`, `ix_`/`ux_`/`fk_`/`ck_` en inglés porque son convenciones de persistencia"*.

Esa convención ya convivía con una incoherencia de origen: `ukun_auth` nace en inglés (`account`, `account_role`, ver ADR-0004) mientras `ukun_app` iba a conservar `producto`, `cliente`, `pedido`. El resultado sería un sistema con dos idiomas de dominio según a qué base se mire, y con el código Java (clases, paquetes, módulos, tests, logs) sin idioma decidido en ningún documento — la pregunta simplemente no se había hecho hasta ahora.

Igual que ADR-0009 con MapStruct: la premisa original —el dominio se piensa y se habla en español, con el negocio real (empleados, catálogo, clientes)— **no era errónea**. La reversión es una decisión de estandarización: un único idioma en todo artefacto ejecutable es más barato de mantener que una frontera de traducción permanente entre el vocabulario de negocio y el de la base de datos, y el coste de hacerlo nunca será menor que ahora, con cero líneas de `ukun-api`/`ukun-auth` escritas y los datos actuales declarados desechables (ADR-0007).

## Decisión
**Inglés** en todo identificador de código — Java (clases, paquetes, módulos de Spring Modulith, ver ADR-0017) **y** TypeScript/React de `apps/ukun-web` (componentes, props, hooks, variables) —, tests, logs, `code`s de error (ver `12-error-contract.md`), contrato REST (`docs/api/openapi/`) y esquema de las dos bases nuevas (`ukun_app`, `ukun_auth`). No tendría sentido fijar inglés en el backend y dejar `apps/ukun-web` con props como `nombre`/`tamaño` — sería la misma incoherencia de vocabulario mixto que este ADR revierte, solo trasladada al frontend.

**Lo que esto NO cubre — el texto de usuario en pantalla** sigue en los dos idiomas soportados (`es`/`en`), gestionado por react-i18next (ADR-0019): un componente se llama `ProductCard` con una prop `size`, pero el texto que renderiza sale de `t('catalog.addToCart')`, nunca hardcodeado en ningún idioma. Código e interfaz de usuario son capas distintas con reglas distintas.

**Se queda en español**: `docs/`, los ADRs y las skills de `.claude/skills/` — son documentación de proceso para quien trabaja en el repo, no forman parte del producto desplegado, y traducirlos no aporta nada a un usuario final que nunca los ve.

### Mapa de nombres (dominio → esquema e identificadores Java)

| Español (legacy) | Inglés (nuevo) | Notas |
|---|---|---|
| `cuenta` | `account` | ya lo era en `ukun_auth` (ADR-0004) |
| `cliente` | `customer` | |
| `empleado` | `employee` | |
| `producto` / `tipo_producto` | `product` / `product_type` | |
| `tienda` | `store` | |
| `stock_tienda` | `store_stock` | alternativa considerada: `inventory_item` (casaría con el módulo `inventory` de ADR-0017); se descarta porque pierde la lectura "stock de una tienda" que sí tiene la fila real |
| `carrito` | **`cart_item`**, no `cart` | RF-03: una fila por `(cliente, producto)`, no hay entidad carrito, hay líneas — traducir literalmente a `cart` conservaría el nombre equivocado |
| `pedido` | **`customer_order`**, no `order` | `ORDER` es palabra reservada en SQL estándar y en Postgres; `customer_order` conserva además la convención de tabla en singular (`10` §7), que `orders` rompería |
| `producto_pedido` | `order_line` | |
| `direccion` | `address` | |
| `tarjeta` | `card` | |
| `auditoria` | `audit_log` | ver ADR-0014 |
| `nombre` / `apellido` / `correo` | `name` / `last_name` / `email` | |
| roles `CLIENTE` / `EMPLEADO` / `ADMIN` | `CUSTOMER` / `EMPLOYEE` / `ADMIN` | son valores de un claim del JWT, o sea contrato: van en inglés como el resto (ADR-0006 decide *qué* roles hay, no cómo se escriben) |
| `precio` / `cantidad` / `total` / `estado` | `price` / `quantity` / `total` / `status` | |
| `es_online` / `admin` / `activo` | `is_online` / `is_admin` / `is_active` | |
| `empleo` | `job_title` | |
| `dir_foto` | `image_key` | va con `FileStoragePort` (ADR-0017); el nombre legacy `dir_foto` no describe que lo que se guarda es una clave de almacenamiento, no una ruta |
| `tienda_fav` | `favorite_store_id` | |
| `fecha` (de pedido) | `placed_at` | la regla "timestamps de dominio en `<participio>_en`" se conserva, traducida a `<participle>_at` |
| `envio_*` / `pago_*` | `shipping_*` / `payment_*` | instantáneas de ADR-0011 |
| `nombre_producto` | `product_name` | |
| `codigo_postal` / `telefono` / `calle` / `ciudad` / `provincia` | `postal_code` / `phone` / `street` / `city` / `province` | |
| `marca` / `exp_mes` / `exp_anio` | `brand` / `exp_month` / `exp_year` | |

Prefijos de objetos de esquema (`ix_`, `ux_`, `ck_`, `fk_`), `created_at`/`updated_at` y `version` ya estaban en inglés (`10` §1 y §7): no cambian.

### No hay renombrado, hay esquema nuevo

Los monolitos legacy no están en marcha y sus datos son desechables (ADR-0007), así que **no existe una fase de renombrado escalonado**: `ukun_app` y `ukun_auth` se crean desde cero, ya en inglés, en el baseline de su release (`10-modelo-de-datos.md` §8). No hay changesets de `renameTable`/`renameColumn` en ningún punto del plan, ni entidades JPA con `@Table(name = "producto")` mapeando nombres españoles, ni coste transitorio que pagar.

Esto es lo que abarata la decisión hasta hacerla obvia: el único coste real que tenía —cuatro meses de mapeo explícito columna a columna mientras el legacy conservaba sus nombres— desaparece con el legacy.

### El contrato REST tampoco tiene fase transitoria

`docs/api/openapi/ukun-api.yaml` nace en inglés desde v0.1 (`/api/products`, `/api/product-types`, `/api/stores`, `/api/availability`, `/api/cart`, `/api/checkout/attempts`, `/api/orders`, `/api/me/profile`, `/api/stores/{storeId}/stock/{lineId}`, y toda la superficie de back-office bajo `/api/admin/**` — ADR-0020), con campos JSON en `camelCase` (`name`, `price`, `unitPrice`, `productName`, `placedAt`). No hay ningún consumidor legacy del contrato nuevo que obligue a una transición.

**En los dos YAML el inglés incluye la prosa, no solo los identificadores**: `info`, `summary`, `description`, las descripciones de `tags`, de parámetros, de respuestas y de schemas. La primera versión del contrato se escribió con rutas y campos en inglés pero descripciones en español, tratándolo como si fuera documentación; no lo es. Un fichero OpenAPI es artefacto de producto —lo renderiza Swagger UI, de él salen los tipos de TypeScript y la colección Bruno, y CI lo diffea contra lo que genera springdoc a partir de anotaciones Java que ya están en inglés—, así que le aplica la misma regla que al resto de lo ejecutable. La frontera sigue siendo la de este ADR: `docs/`, ADRs y skills en español; todo lo que se despliega o se consume, en inglés.

## Consecuencias
- Un único idioma de dominio en todo lo ejecutable: Java, esquema de las bases nuevas y contrato REST. Cero ambigüedad de "¿esto se llama como en la tabla o como en el negocio?".
- Coste de transición: ninguno. Sin legacy en marcha, el esquema nace en inglés y las entidades JPA no necesitan ni un `@Column(name = ...)` de traducción.
- `docs/`, ADRs y skills siguen en español: quien lee este repo para trabajar en él sigue haciéndolo en su idioma; quien lee el código o el contrato, en inglés.
- Todo ejemplo de código en los documentos de `docs/` y en las skills debe usar ya los nombres en inglés de este mapa, aunque describan un release futuro — evita que un documento quede desincronizado con el ADR que cita.

## Alternativas descartadas
- **Mantener español en el dominio, inglés solo en infraestructura** (decisión previa, ahora revertida): sigue siendo un argumento válido en abstracto, pero deja sin resolver la incoherencia entre `ukun_auth.account` y `ukun_app.producto`, y no dice nada sobre el idioma del código Java.
- **Inglés también en `docs/`/ADRs/skills**: se descarta por alcance — tres veces el volumen de traducción para documentación que no forma parte del producto ni la ve un usuario final.
- **Descripciones del OpenAPI en español** (era el estado del primer borrador): deja el único artefacto que se renderiza a un consumidor —Swagger UI— hablando dos idiomas a la vez, y obliga a traducir mentalmente cada `description` al escribir el `@Operation` de Java que CI va a diffear contra ella.
- **Renombrado escalonado con vistas de compatibilidad**: era la única alternativa mientras el legacy siguiera leyendo el esquema. Con el legacy fuera (ADR-0007) el problema no existe: no hay lector al que mantener contento.
