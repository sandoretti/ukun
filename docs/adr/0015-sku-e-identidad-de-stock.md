# ADR-0015: SKU en `product` y PK subrogada en `store_stock`

## Estado
Aceptado

## Contexto
En el legacy, `producto` no tiene ningún código de negocio: se identifica y se busca por su `id` numérico autogenerado, tanto en el catálogo público como en la pantalla de alta de stock del empleado (RF-09). Y `stock_tienda` tiene clave compuesta `(producto_id, tienda_id)` — `StockProducto` se mapea en JPA con `@EmbeddedId`/`StockProductoId` en ambos monolitos —, lo que significa que una línea de stock no tiene identidad propia: no se puede dar una URL a "el stock de este producto en esta tienda", ni referenciarla desde un error, sin pasar el par de ids completo.

Al diseñar la mejora de gestión de stock pedida para el rediseño (empleado gestiona por producto, con mejor control), la ausencia de ambos identificadores fuerza a la UI y a la API a trabajar con ids numéricos internos como si fueran claves de negocio.

## Decisión
Dos cambios de esquema independientes, ambos en `product`/`store_stock` (nombres ya en inglés, ver ADR-0018):

Ambos nacen así en el baseline de `ukun_app` (v0.2, `10-modelo-de-datos.md` §8) — no hay legacy en marcha, así que no hay nada que endurecer en dos pasos:

```sql
-- product
sku VARCHAR(32) NOT NULL UNIQUE
  CONSTRAINT ck_product_sku CHECK (sku ~ '^[A-Z0-9-]{3,32}$')

-- store_stock
id BIGSERIAL PRIMARY KEY,
CONSTRAINT ux_store_stock UNIQUE (product_id, store_id)
```

### Por qué `sku` es `NOT NULL` desde el principio

El SKU es el identificador con el que el empleado busca y da de alta stock (RF-09): un producto sin SKU es un producto que esa pantalla no puede manejar. La única razón para haberlo dejado nullable habría sido que un `INSERT` ajeno sin esa columna siguiera funcionando — y ese `INSERT` ajeno era `ukun-empleados`, que no corre. Sin ese constraint externo, `NOT NULL` + `CHECK` de formato desde el baseline es estrictamente mejor: no hay ninguna ventana en la que una consulta tenga que tratar el `NULL` como caso especial.

Los datos demo (`context: demo`) siembran un SKU por producto; el formato lo fija el propio `CHECK`.

### Consecuencia en el mapeo JPA de `ukun-api`

Desaparecen `@EmbeddedId` y la clase `StockProductoId`. La entidad pasa a:

```java
@Entity
@Table(name = "store_stock")
public class StoreStock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "store_id", nullable = false)
    private Long storeId;
    private Integer quantity;
    @Version
    private Long version;
}
```

El bloqueo pesimista ordenado de ADR-0008 se simplifica: en vez de `order by s.id.productId` (navegando una clave embebida) es `order by s.id` — igual de determinista para evitar interbloqueos, sin exponer una clave compuesta en JPQL. Ver nota añadida en ADR-0008.

### Qué gana cada requisito

- **RF-09 (gestión de stock por el empleado):** busca y da de alta por SKU, no por id numérico interno. Cada línea de stock tiene URL propia — `PUT /api/stores/{storeId}/stock/{lineId}` — en vez de un par de query params, lo que además elimina estructuralmente el antipatrón descrito en el propio RF-09 de comparar `tiendaId` recibido por parámetro a mano.
- **Errores de checkout:** `INSUFFICIENT_STOCK` (`docs/12-error-contract.md`) puede señalar la línea de stock concreta (`stockLineId` + `sku`), no solo el id del producto.
- **Índices: cero netos nuevos.** La `UNIQUE (product_id, store_id)` ocupa exactamente el lugar del índice que hoy da la PK compuesta; `ix_store_stock_store` (justificado por Q5 en `10` §4) no cambia.

## Consecuencias
- El empleado y el catálogo ganan un identificador de negocio estable y con formato validado.
- Cada línea de stock es direccionable por sí misma, lo que simplifica tanto el contrato REST como los mensajes de error.
- Coste de transición: ninguno. Ambas restricciones entran completas en el baseline.
- El `ORDER BY` del bloqueo pesimista es más simple que con clave embebida (ver ADR-0008), sin cambiar de propiedad garantizada.

## Alternativas descartadas
- **Mantener la clave compuesta `(product_id, store_id)`**: obliga a `@EmbeddedId` en JPA, direcciona cada línea por un par de ids en vez de un identificador propio, y complica innecesariamente el `ORDER BY` del bloqueo pesimista.
- **SKU sin PK subrogada en `store_stock`**: resuelve la incomodidad de la UI de empleados pero no la del modelo JPA — la línea de stock seguiría sin identidad propia.
- **SKU generado automáticamente por la base (secuencia formateada, p. ej. `PRD-000123`)**: quita al negocio el control de un código que es explícitamente suyo (RF-09 lo trata como dato editable por el empleado/admin). Se mantiene como código introducido por quien da de alta el producto, validado por el `CHECK` de formato.
