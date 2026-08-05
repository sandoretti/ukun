# ADR-0008: Dos estrategias de concurrencia distintas para el stock

## Estado
Aceptado

## Contexto
`stock_tienda` (`store_stock` en el esquema nuevo, ver ADR-0018) se escribe desde dos flujos con requisitos opuestos: el checkout de un cliente (`PedidoServiceImpl.reducirStockOnline` hoy hace una lectura-modificación-escritura sin ningún bloqueo, causando una race condition real de sobreventa con pedidos concurrentes del mismo producto) y la edición manual de stock por un empleado en su tienda (`StockController`/`StockServiceImpl`).

## Decisión
Se tratan como dos problemas distintos, con soluciones distintas (detalle en `docs/03-analisis-y-patrones.md#agregado-stock`):

- **Checkout → bloqueo pesimista ordenado.** `SELECT ... FOR UPDATE` sobre las filas de `store_stock` implicadas, ordenadas antes de bloquear, para que dos pedidos concurrentes que comparten productos en distinto orden no interbloqueen.
- **Edición manual por empleado → optimista con `@Version`.** Si dos empleados editan la misma fila casi a la vez, el segundo `save` recibe `409 VERSION_CONFLICT` (`docs/12-error-contract.md`) y debe recargar — no queremos que "gane el último" en silencio.
- **Red de seguridad final**: `CHECK (quantity >= 0)` en la propia tabla, independiente de que el dominio se equivoque en algún camino no previsto.

> **Nota (ADR-0015):** el criterio de orden del bloqueo pesimista cambia de `producto_id` a `id` cuando `store_stock` gana una PK subrogada propia — sigue siendo un orden determinista sobre las mismas filas, la propiedad que evita el interbloqueo no cambia. Nombres de tabla/columna en este documento actualizados a inglés por ADR-0018 (`stock_tienda`→`store_stock`, `stock`→`quantity`).

## Consecuencias
- El checkout nunca deja stock negativo ni pierde un decremento por condición de carrera, verificado con un test de concurrencia explícito (dos hilos comprando la última unidad; exactamente uno recibe `201`, el otro `409`) — ver `docs/03-analisis-y-patrones.md`.
- La edición manual de stock informa honestamente de conflictos en vez de sobreescribir silenciosamente.
- Dos mecanismos de concurrencia en el mismo agregado añaden algo de complejidad conceptual, pero cada uno resuelve una semántica de negocio distinta — unificarlos en uno solo (p. ej. optimista en los dos sitios) sería más simple de código pero peor de UX en el checkout: un pedido de varias líneas reintentaría entero por chocar en una sola.

## Alternativas descartadas
- **Optimista con reintento en ambos flujos**: más simple, pero mal ajuste para el checkout — un pedido con varias líneas reintentaría por completo ante cualquier colisión parcial, en vez de resolverse en un único paso con el bloqueo ordenado.
- **Sin ningún control de concurrencia** (el estado actual): es exactamente el bug que se está corrigiendo.
