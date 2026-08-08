---
name: liquibase
description: Convenciones de este repo para los changelogs de Liquibase de cualquier servicio con base de datos propia (ukun-api, ukun-auth) — baseline completo por base, contexts demo/prod, inmutabilidad de changesets aplicados. Usar al añadir o modificar cualquier cambio de esquema.
---

# Convenciones de Liquibase en este repo

## Estructura

```
services/<servicio>/src/main/resources/db/changelog/
├── db.changelog-master.yaml       # incluye todo lo de abajo, en orden
├── baseline/001-esquema-inicial.yaml   # el esquema completo, de una vez
├── releases/vX.Y/*.yaml           # solo si un release posterior descubre un cambio
└── demo/001-datos-demo.yaml       # context: demo — nunca corre en prod
```

**El baseline es completo, no incremental.** No hay legacy en marcha ni datos que preservar (ADR-0007), así que el esquema de cada base entra entero en su baseline: `ukun_app` en v0.2, `ukun_auth` en v0.4 (`docs/10-modelo-de-datos.md` §8). No hay changesets de renombrado, ni restricciones que entren nullable para endurecerse después, ni marcas de compatibilidad.

Después del baseline, un release solo añade una carpeta bajo `releases/` si el diseño **descubre** un cambio al implementar la feature. No se reescribe un changelog ya aplicado — un cambio posterior es un changeset nuevo, aunque revierta el anterior.

## Reglas duras

1. **Un changeset aplicado nunca se edita.** Si tiene un error, se corrige con un changeset nuevo. Editar uno ya aplicado en cualquier entorno compartido rompe el checksum de Liquibase y desincroniza esa base respecto a las demás.
2. **`context` separa datos de esquema.** El esquema (tablas, columnas, índices, constraints) no lleva context — se aplica siempre. Los datos de ejemplo (equivalente a los `INSERT` de `database/init.sql` legacy) van en changesets con `context: demo`, que simplemente no se ejecutan cuando el perfil activo usa `context: prod`.
3. **Ningún DDL en scripts de arranque de Postgres.** El único script de inicialización de la base (`scripts/postgres-init-databases.sh`) crea bases vacías (`CREATE DATABASE`); el esquema lo crea Liquibase al arrancar cada servicio contra su propia base. Esto es justo lo que corrige el `init.sql` legacy, que solo se ejecuta en un volumen virgen y por tanto no sirve como historial de cambios.
4. **Probar todo changeset nuevo contra una base virgen** (`db-reset` en Tilt, ver `docs/06-entorno-local-tilt.md`) antes de darlo por bueno — no solo contra tu base local ya evolucionada, que puede ocultar un changeset que asume un estado previo que ya no es el real.
5. **Restricciones de integridad expresadas en el changelog, no solo en Bean Validation.** El legacy tiene columnas (`telefono VARCHAR(9)`, `stock` nullable pese a `@NotNull` en la entidad) donde la entidad promete más de lo que la base garantiza — no repitas esa divergencia.
6. **Cada servicio con base de datos propia gestiona su propio `db.changelog-master.yaml`.** No hay un changelog compartido entre `ukun-api` y `ukun-auth` — son bases distintas, historiales de cambio distintos.
7. **Nombres de tabla y columna en inglés, siempre** (ADR-0018) — `product`, `store_stock`, `customer_order`, etc. Ningún changeset introduce un nombre en español, y ninguno renombra nada: el esquema nace en inglés.
8. **La dependencia es `org.springframework.boot:spring-boot-liquibase`, no `org.liquibase:liquibase-core`.** Boot 4 partió la autoconfiguración en un módulo por tecnología: `liquibase-core` a secas trae el motor pero no `LiquibaseAutoConfiguration`, así que el changelog **no se aplica nunca** y el servicio arranca contra una base vacía sin dar ningún error. El artefacto de Boot arrastra `liquibase-core` de forma transitiva. Al lado, `spring.jpa.hibernate.ddl-auto: validate`: Liquibase crea el esquema, Hibernate solo lo comprueba, nunca lo escribe.

## YAML nativo por defecto, `sql` en tres casos contados

**Los change types nativos son la forma por defecto** (`createTable`, `addUniqueConstraint`, `addForeignKeyConstraint` con `onDelete`, `createIndex` con `descending`, `addColumn`, `dropColumn`): expresan la intención en el diff y Liquibase les genera el rollback solo. Se baja a `sql` únicamente donde el motor OSS no llega — verificado contra liquibase-core 5.0.3, no supuesto:

| Caso | Por qué no hay change type |
|---|---|
| **`CHECK`** | `AddCheckConstraintChange` es Liquibase **Pro**. Y el atributo `checkConstraint` de `constraints:` sí existe en OSS, pero **nunca llega a `CreateTableStatement`: el changeset valida, se aplica, informa de éxito — y la restricción no está en la base.** Es el modo de fallo más caro que hay, porque crea justo la divergencia entidad↔base que prohíbe la regla 5 sin dar ni un warning |
| **Índice parcial** (`CREATE UNIQUE INDEX … WHERE …`) | `CreateIndexChange` no tiene atributo `where`. Sin el `WHERE`, `ux_store_online` significaría "no puede haber dos tiendas con el mismo `is_online`", que prohíbe una segunda tienda *offline*: semántica equivocada, no una optimización que falta |
| **`CREATE EXTENSION`** | sin change type |

Efecto colateral que hay que conocer: por la vía nativa, `autoIncrement: true` (y también un `type: BIGSERIAL` literal) genera `BIGINT GENERATED BY DEFAULT AS IDENTITY`, no un `BIGSERIAL`. Es equivalente a efectos de `GenerationType.IDENTITY` en Hibernate y es la forma estándar moderna, pero **no hay manera de obtener un `BIGSERIAL` literal salvo con `sql`**.

Agrupa los changesets como está organizado `docs/10-modelo-de-datos.md`, no tabla a tabla: tablas (§2), `UNIQUE` (§2), `CHECK` (§1 y §2), FK con su `ON DELETE` (§6), índices (§4). Quien revisa lee los dos documentos en paralelo.

Y **prueba que los `CHECK` existen de verdad** (`SELECT … FROM information_schema.table_constraints WHERE constraint_type = 'CHECK'`): es la única red contra el fallo silencioso de la primera fila de la tabla.

## Cómo se escribe el baseline

**`docs/10-modelo-de-datos.md` es el origen de cada changeset**, no `database/init.sql`. Ese documento ya hizo el trabajo: tipos y `CHECK` columna a columna (§2), índices justificados uno a uno por la consulta real que los motiva (§4), política `ON DELETE` explícita por FK (§6), y las instantáneas embebidas de `customer_order` (§5). El `init.sql` legacy solo sirve como referencia de qué datos existían, y sus decisiones están *corregidas* en ese documento, no copiadas — p. ej. dos de los índices de FK citados en `docs/03-analisis-y-patrones.md` (`cart_item.customer_id`, `order_line.order_id`) son redundantes por ser ya columna líder de su PK compuesta.

Trampas concretas que el baseline debe recoger, todas justificadas en `10` §2:
- `store_stock`: `id BIGSERIAL PK` + `UNIQUE (product_id, store_id)` + `version BIGINT` + `CHECK (quantity >= 0)` (ADR-0008, ADR-0015).
- `product.sku`: `NOT NULL UNIQUE` + `CHECK` de formato (ADR-0015).
- `store.is_online` + **índice único parcial** `ux_store_online ... WHERE is_online` — es lo que convierte "hay exactamente una tienda online" en invariante de base.
- `customer_order`: `shipping_*`/`payment_*` como columnas embebidas, nunca FK a `address`/`card` (ADR-0011); `CHECK (total > 0)`.
- `order_line.product_name` como instantánea, y `CHECK (total_price = unit_price * quantity)`.
- `citext` en `ukun_auth` (`CREATE EXTENSION citext` antes de `account`).
