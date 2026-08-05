# ADR-0011: Dirección, medio de pago y nombre de producto se embeben e inmutan en `pedido`

## Estado
Aceptado

## Contexto
RF-04 (`02-requisitos.md`) establece que "el pedido copia la dirección y la tarjeta actuales del cliente en el momento de confirmar". El código actual **no cumple ese requisito**: `Pedido(Cliente)` en `ukun-clientes/.../models/entity/Pedido.java` asigna `this.direccion = cliente.getDireccion()` y `this.tarjeta = cliente.getTarjeta()` — copia la referencia, no el valor. Como `Cliente` declara `@OneToOne(cascade = CascadeType.ALL)` sobre ambas y `PerfilController.editar` opera sobre la instancia de `@SessionAttributes("cliente")`, editar el perfil ejecuta un `UPDATE` sobre la misma fila de `direccion`/`tarjeta` que ya referencian todos los pedidos anteriores del cliente. Un cliente que se muda de casa reescribe silenciosamente la dirección de envío de su historial completo de pedidos; un cambio de tarjeta hace lo mismo con los datos de pago. El mismo problema, sin verificación en código pero por el mismo patrón de referencia compartida, afectaría al nombre del producto si `producto_pedido` solo referenciara `producto.id`: renombrar un producto (que RF-10 permite) cambiaría el nombre mostrado en pedidos ya hechos.

## Decisión
Los tres datos que RF-04 exige "congelados en el momento del pedido" se convierten en **columnas embebidas e inmutables dentro de `pedido`/`producto_pedido`**, no en filas referenciadas:

- `pedido.envio_calle`, `envio_ciudad`, `envio_provincia`, `envio_codigo_postal` (`@Embeddable DireccionEnvio`).
- `pedido.pago_last4`, `pago_marca`, `pago_exp_mes`, `pago_exp_anio` (`@Embeddable MedioDePago`).
- `producto_pedido.nombre_producto VARCHAR(255) NOT NULL`.

Las FK `pedido.direccion_id`/`pedido.tarjeta_id` desaparecen. El campo `precio_unitario` de `producto_pedido`, ya congelado hoy, se acompaña del nombre por la misma razón.

## Consecuencias
- La inmutabilidad es estructural: no existe ninguna fila en `direccion`/`tarjeta` que otra feature (edición de perfil) pueda tocar y que afecte a un pedido ya creado.
- Desaparece el acoplamiento inverso: hoy, en teoría, no se podría borrar una tarjeta de perfil que un pedido referenciara; con columnas embebidas esa restricción no existe.
- Los datos de dirección y pago se duplican por pedido (unos cientos de bytes) — irrelevante al volumen de este dominio.
- `customer_order` nace con las columnas embebidas en el baseline (`10-modelo-de-datos.md` §8); el agregado `Order` que las rellena llega en v0.6.

## Alternativas descartadas
- **Copiar una fila nueva dentro de la misma tabla `direccion`/`tarjeta`**: la fila copiada seguiría siendo tan mutable como cualquier otra — nada en el esquema impediría que un `UPDATE` futuro la tocara. El invariante de inmutabilidad viviría solo en la disciplina del código, que es exactamente cómo se produjo este bug.
- **Tabla `direccion_pedido` aparte, referenciada 1:1**: añade un join a una relación que se lee siempre junto al pedido y nunca por sí sola, sin ganar nada que las columnas embebidas no den ya.
