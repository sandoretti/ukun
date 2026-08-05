# ADR-0012: `pedido.estado` como `VARCHAR` + `CHECK`, no `ENUM` nativo de Postgres

## Estado
Aceptado

## Contexto
`04-roadmap.md` sitúa la máquina de estados completa del pedido (`PENDIENTE → PAGADO → ENVIADO → ENTREGADO/CANCELADO`) en v1.1+, cuando exista logística y pasarela de pago reales. Antes de eso, en v0.6, ya conviene reservar la columna con al menos dos valores (`CONFIRMADO`, `CANCELADO`) para no añadirla como cambio de esquema aislado más adelante. Quedaba por decidir la representación en Postgres.

## Decisión
`pedido.estado VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADO'` con `CONSTRAINT ck_pedido_estado CHECK (estado IN ('CONFIRMADO', 'CANCELADO'))`, mapeado en Java con `@Enumerated(EnumType.STRING)`. Añadir un valor nuevo es un changeset que hace `dropCheckConstraint` + `addCheckConstraint` con la lista ampliada.

## Consecuencias
- Añadir un estado nuevo (v1.1+: `PAGADO`, `ENVIADO`, `ENTREGADO`) es un único changeset de Liquibase, reversible.
- El mapeo en Hibernate es soporte de primera clase (`EnumType.STRING`), sin `@JdbcTypeCode` ni casts explícitos en consultas nativas.
- El valor se lee tal cual desde `psql` o cualquier herramienta de inspección, sin resolver un tipo `enum` de Postgres aparte.
- Coste de almacenamiento marginal (`VARCHAR(20)` frente a los ~4 bytes de un `ENUM` nativo) irrelevante al volumen de `pedido` en este dominio.

## Alternativas descartadas
- **`CREATE TYPE estado_pedido AS ENUM (...)`**: en Postgres 17, `ALTER TYPE ... ADD VALUE` corre dentro de una transacción, pero el valor añadido **no se puede usar en esa misma transacción** — como Liquibase envuelve cada changeset en una, añadir un estado y consumirlo exigiría dos changesets como mínimo. Además, un valor de `ENUM` nunca se puede eliminar. En Hibernate exige `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` y el driver lo transmite como `unknown`, obligando a castear (`?::estado_pedido`) en cualquier query nativa.
- **Tabla de referencia `estado_pedido(codigo PK)` + FK**: solo se justificaría si el estado llevara datos asociados (etiqueta traducible, orden de presentación, transiciones permitidas) — no es el caso aquí, las transiciones viven en el agregado `Pedido`, no en la base. Añadiría un join en cada lectura de pedido sin beneficio.
