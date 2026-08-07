# ADR-0013: Política de borrado explícita por FK, y descatalogación lógica solo en `producto`

## Estado
Aceptado

## Contexto
`database/init.sql` no declara ninguna cláusula `ON DELETE` — todas las FK son `NO ACTION` por omisión de Postgres. RF-10 (`02-requisitos.md`) exige que borrar un producto referenciado (por líneas de pedido, stock o carrito) se rechace "con un mensaje claro, no un error genérico". Con `RESTRICT`/`NO ACTION` estricto en todas las FK de `producto`, cualquier producto que se haya vendido alguna vez deja de poder borrarse nunca — RF-10 en la práctica se convierte en "casi nada es borrable", y el admin necesita una salida de negocio real.

## Decisión
Cada FK del esquema declara su `ON DELETE` explícitamente (tabla completa en `10-modelo-de-datos.md` §6), con dos patrones dominantes:
- `RESTRICT` donde el recurso padre es un registro contable o catálogo que no debe desaparecer si tiene referencias (`producto`, `cliente` desde `pedido`, `tienda` desde `empleado`).
- `CASCADE` donde el hijo es estado efímero o parte del mismo agregado (`carrito` al borrar un cliente, `producto_pedido` al borrar su `pedido`, `stock_tienda` al borrar una tienda).

Además, `product` lleva borrado lógico: `is_active BOOLEAN NOT NULL DEFAULT true` (ADR-0018). El `DELETE` físico solo es posible si el producto nunca se referenció (lo sigue garantizando el `RESTRICT`); la salida operativa para un producto ya vendido es descatalogarlo (`is_active = false`), lo que lo saca del catálogo (RF-01) y de las pantallas de stock (RF-09) sin tocar el historial. Descatalogar y reactivar son operaciones propias del contrato (`POST /api/admin/products/{id}:discontinue` y `:reactivate`, ADR-0020), no un campo más del formulario de edición: son una salida de negocio, van a `audit_log` (ADR-0014) y tienen su propio evento (`11-observabilidad-y-logging.md` §3).

RF-10 se hace cumplir en dos capas: la FK `RESTRICT` es la garantía (sobrevive a un `DELETE` manual, cierra la carrera entre comprobación y borrado); el caso de uso (`DeleteProductUseCase`) cuenta referencias antes de intentar el borrado y devuelve `409 ProblemDetail code=PRODUCT_IN_USE` con el detalle de dónde está referenciado (`inOrders`, `inStock`, `inCarts`), en vez de dejar que el `23503` de Postgres llegue crudo al usuario.

### Los tipos de producto siguen el mismo patrón, sin borrado lógico (RF-13)

`product_type` gana CRUD en RF-13, y su borrado se hace cumplir igual: `RESTRICT` en `product.product_type_id → product_type` como garantía, y `DeleteProductTypeUseCase` contando antes (`countByProductTypeId`) para responder `409 PRODUCT_TYPE_IN_USE` con `{inProducts}`.

Lo que **no** hereda es el `is_active`. El borrado lógico de `product` existe porque con `RESTRICT` un producto vendido no se puede borrar **nunca**, y sin una salida lógica RF-10 sería un callejón sin salida. Un tipo no tiene ese problema: si tiene productos se rechaza y se reasignan; si no los tiene, se borra de verdad. No hay ningún estado del que no se pueda salir, así que un tercer estado sobraría. El conteo no filtra por `is_active`: un tipo cuyos productos están todos descatalogados sigue estando referenciado.

## Consecuencias
- Ninguna FK queda con la política por omisión sin decidir; cada una es una línea explícita en el changelog de Liquibase.
- Un tipo de producto es borrable de verdad cuando no tiene productos, y no acumula estados intermedios (RF-13).
- Un producto vendido puede desaparecer del catálogo (descatalogación) sin comprometer la integridad del historial de pedidos.
- `cliente`/`empleado` no ganan un flag de borrado lógico propio en `ukun_app`: "borrar" una cuenta es `account.enabled = false` en `ukun-auth`, que ya existe por diseño (`08-identidad-y-seguridad.md`); duplicarlo aquí crearía una segunda fuente de verdad para lo mismo.
- `pedido` no se borra nunca, ni lógica ni físicamente — cancelar es un cambio de `estado` (ADR-0012), no una eliminación.

## Alternativas descartadas
- **`RESTRICT` sin salida lógica en ningún sitio**: es el statu quo del legacy, y convierte RF-10 en un callejón sin salida operativa para el caso más común (un producto que ya se vendió).
- **Borrado lógico también en `cliente`/`empleado` dentro de `ukun_app`**: dos flags de "activo" para la misma cuenta (uno en cada servicio) que pueden divergir, contradiciendo la frontera de identidad de `08-identidad-y-seguridad.md`.
- **Borrado lógico también en `product_type`**: resolvería un problema que ese dato no tiene — ver arriba. Tres estados donde bastan dos.
