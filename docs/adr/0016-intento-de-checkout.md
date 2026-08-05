# ADR-0016: El intento de checkout, no el cliente, emite la clave de idempotencia

## Estado
Aceptado — corrige un detalle de `01-arquitectura.md` §2 aceptado previamente en este repositorio

## Contexto
`01-arquitectura.md` §2 dibuja hoy la secuencia de checkout con el navegador generando la `Idempotency-Key` ("Idempotency-Key generada en el cliente") antes de llamar a `POST /api/pedidos`. Eso resuelve el reintento de red y el doble clic, pero deja dos huecos: (a) React tiene que garantizar que esa clave sobreviva a un re-render sin regenerarse, sin que el servidor pueda verificarlo, y (b) no hay ningún punto en el servidor donde revalidar disponibilidad y precio **antes** de que el cliente vea el botón de confirmar — la primera vez que el backend mira el carrito es ya dentro de la transacción de checkout.

`03-analisis-y-patrones.md` ya había fijado la tabla `idempotency_key(key, cliente_id, pedido_id, response_body, created_at)` para v0.6, pero sin definir la semántica completa de conflicto ni quién emite la clave.

## Decisión
El servidor emite un identificador de intento al abrir el checkout; el cliente lo reenvía como `Idempotency-Key` al confirmar.

```
POST /api/checkout/attempts
  -> 201 { attemptId, lines: [{ sku, productName, unitPrice, quantity, available }], total, expiresAt }

POST /api/orders
  Idempotency-Key: {attemptId}
  -> 201 la primera vez ; 200 con la MISMA respuesta en cualquier reintento posterior
```

### Tabla `idempotency_key`, concretada

```sql
CREATE TABLE idempotency_key (
    id              BIGSERIAL PRIMARY KEY,
    key             UUID NOT NULL,
    customer_id     BIGINT NOT NULL REFERENCES customer(id),
    order_id        BIGINT REFERENCES customer_order(id),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    request_hash    VARCHAR(64) NOT NULL,
    response_status SMALLINT,
    response_body   JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_idempotency_customer_key UNIQUE (customer_id, key)
);
```

### Semántica completa de `POST /api/orders Idempotency-Key: {attemptId}`

| Situación | Respuesta |
|---|---|
| Clave nunca vista | ejecuta el checkout, inserta la fila en `IN_PROGRESS`, la actualiza a `COMPLETED` con el `response_body` al terminar, `201` |
| Clave ya `COMPLETED`, mismo `request_hash` | `response_status`/`response_body` guardados, sin repetir el efecto — normalmente `200` |
| Clave `IN_PROGRESS` (petición concurrente con la misma clave) | `409 IDEMPOTENCY_IN_PROGRESS` |
| Clave existente, `request_hash` distinto del guardado | `422 IDEMPOTENCY_MISMATCH` — el cliente está reutilizando un `attemptId` para un pedido distinto, lo cual no debería ocurrir por flujo normal de UI, pero se rechaza explícitamente en vez de servir una respuesta ambigua |
| `attemptId` no encontrado o `expiresAt` ya pasado | `410 CHECKOUT_EXPIRED` — el front debe reabrir con un nuevo `POST /api/checkout/attempts` |

`code`s definidos en `docs/12-error-contract.md`.

### El intento resuelve también "asegurar que el cliente quiere el producto", sin reservar stock

`POST /api/checkout/attempts` congela precio unitario y calcula el total en el momento de abrir el checkout, y devuelve disponibilidad informativa por línea (lectura simple sobre `store_stock`, sin bloqueo). El checkout revalida contra esos datos antes de dejar confirmar. Esto no impide que el `409 INSUFFICIENT_STOCK` siga siendo posible en `POST /api/orders` (el bloqueo pesimista real sigue viviendo ahí, ADR-0008 sin cambios) — lo que cambia es que, con la disponibilidad ya mostrada al abrir el intento, ese `409` pasa a ser el caso raro de una colisión real entre el momento de abrir el checkout y el de confirmar, no la primera vez que el cliente se entera de que no hay stock.

Se decide explícitamente **no** introducir una reserva de stock con TTL (`stock_reserved` + tabla de reservas + job de expiración + reconciliación): el coste de ese mecanismo —un invariante nuevo `stock - reserved >= 0`, un job que puede fallar, un camino de reconciliación que probar— no está justificado frente a la ganancia marginal sobre "informar de la disponibilidad antes de confirmar", que el intento ya da gratis.

### El botón deshabilitado en el front es defensa en profundidad, no la garantía

React deshabilita el botón de confirmar mientras la petición está en vuelo (mejora de UX evidente), pero eso **no** es lo que impide el pedido duplicado — no cubre el reintento de red tras un timeout, ni un F5 en la pantalla de confirmación tras un `201` ya recibido, ni el botón "atrás" del navegador. Lo que cubre esos tres casos es la clave de idempotencia validada en el servidor. Se deja escrito para que nadie confunda la mejora de UX con el mecanismo real.

### Release

**v0.6**, como ya fijaba `03-analisis-y-patrones.md` — es el release donde `ukun-api` toma posesión de `pedido`/`customer_order` (agregado, instantáneas, idempotencia).

Lo que se adelanta a **v0.1**: el contrato de `/api/checkout/attempts` y el `enum` de los cuatro `code`s nuevos (`CHECKOUT_EXPIRED`, `INSUFFICIENT_STOCK`, `IDEMPOTENCY_IN_PROGRESS`, `IDEMPOTENCY_MISMATCH`) entran en `docs/api/openapi/ukun-api.yaml` antes de SINC-1 (2026-08-11), aunque la implementación no llegue hasta v0.6. Congelar el OpenAPI sin este endpoint obligaría a romperlo después.

## Consecuencias
- El front nunca inventa un identificador: lo recibe del servidor al pintar la pantalla de confirmación, eliminando la clase de bug "la clave se regeneró en un re-render".
- Existe un punto natural, antes de la transacción de checkout, para revalidar precio y disponibilidad — mejora de UX medible (menos `409` sorpresa) sin tocar la estrategia de concurrencia de ADR-0008.
- Se añade un endpoint (`POST /api/checkout/attempts`) y una tabla (`idempotency_key`) que no existían en el diseño previo de esta pieza, con su propio ciclo de vida (TTL de 30 minutos, purga diaria sin índice dedicado — ya justificado en `10-modelo-de-datos.md` §4).
- El diagrama de secuencia de checkout en `01-arquitectura.md` §2 se actualiza para mostrar el intento antes de `POST /api/orders`.

## Alternativas descartadas
- **El cliente genera el UUID** (lo que dibujaba `01-arquitectura.md` hasta este ADR): más simple, un endpoint menos, pero sin punto de revalidación servidor y dependiente de que React no regenere la clave en un re-render.
- **Solo deshabilitar el botón en el front, sin idempotencia en el backend**: descartado de entrada — no cubre reintento de red ni F5 tras confirmar, que son los dos casos reales de pedido duplicado.
- **Reserva de stock con TTL al abrir el checkout**: mayor garantía teórica de "el cliente se queda con el producto", pero introduce estado nuevo (columna, tabla, job de expiración, reconciliación) para un beneficio que el intento sin reserva ya cubre en la práctica — ver razonamiento arriba.
