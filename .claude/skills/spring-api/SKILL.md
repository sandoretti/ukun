---
name: spring-api
description: Convenciones de este repo para el monolito API de dominio (services/ukun-api) — estructura por feature, DTOs record, ProblemDetail, EntityGraph, transacciones, prohibición de @Data en entidades. Usar al implementar o revisar cualquier endpoint REST de catálogo, tiendas, clientes, carrito, pedidos, empleados o stock.
---

# Convenciones de `services/ukun-api`

Este servicio absorbe la lógica que en el legacy está repartida entre `ukun-clientes` y `ukun-empleados` (que no corren y se borran — no hay convivencia). Antes de escribir código aquí, lee `docs/02-requisitos.md` (qué debe hacer) y `docs/03-analisis-y-patrones.md` (por qué se hace así — cada regla de abajo enlaza a un defecto real que corrige).

## Estructura: módulos de Spring Modulith (ver ADR-0017)

Por feature, no por capa técnica global — y cada paquete de nivel superior es un `@ApplicationModule` verificado en CI, no solo una convención de carpetas:

| Módulo | Tablas que posee | `allowedDependencies` |
|---|---|---|
| `shared` | — | módulo abierto (`type = OPEN`) |
| `catalog` | `product`, `product_type` | `storage` |
| `stores` | `store`, `address` de tienda | — |
| `storage` | — (`FileStoragePort`) | — |
| `identity` | — (`AccountsPort`) | — |
| `inventory` | `store_stock` | `catalog`, `stores` |
| `customers` | `customer`, `address`, `card` de perfil | `stores`, `identity` |
| `staff` | `employee` | `stores`, `identity` |
| `cart` | `cart_item` | `catalog`, `inventory`, `customers` |
| `orders` | `customer_order`, `order_line`, `idempotency_key` | `cart`, `inventory`, `customers`, `catalog` |

Cada módulo, por dentro:
```
inventory/
  InventoryApi.java   <- API pública: interfaz + DTOs record. Lo único importable desde fuera.
  web/                 (o directamente en la raíz del feature)
  application/         (casos de uso, cuando la orquestación lo justifique)
  domain/               (agregados: Order, StoreStock)
  infra/                (repositorios JPA, entidades)
```

Los paquetes de módulo **no se importan entre sí** salvo a través de la API pública (`XxxApi`) declarada en `allowedDependencies`. Se hace cumplir con `ApplicationModules.verify()` (Spring Modulith), no con disciplina ni con una regla ArchUnit escrita a mano. Ninguna `@Entity` se expone en la API pública de un módulo. Detalle completo del grafo, el ciclo `catalog`↔`inventory` y cómo se rompe, y ejemplos de código: `docs/14-modules.md`.

## Reglas duras (no negociables sin pasar por un ADR nuevo)

1. **Ninguna entidad `@Entity` lleva `@Data` de Lombok.** Usa `@Getter @Setter`; `equals`/`hashCode` por id, escritos a mano; `toString` solo con campos escalares. Razón: `@Data` sobre asociaciones bidireccionales causó un `StackOverflowError` real entre `Pedido` y `ProductoPedido` en el legacy.
2. **Dinero es `BigDecimal`**, nunca `Float`/`Double`. Columna `@Column(precision = 10, scale = 2)`.
3. **DTOs son `record`**, mapeados desde la entidad con **MapStruct** (`componentModel = "spring"`, `unmappedTargetPolicy = ERROR`; ver ADR-0009) — un mapper por agregado, nunca `ModelMapper`/`BeanUtils.copyProperties`. Nunca se serializa una `@Entity` directamente en una respuesta. El procesador de anotaciones de MapStruct va **después** del de Lombok en `annotationProcessorPaths`.
4. **`spring.jpa.open-in-view: false`** desde el primer commit. Si algo revienta por lazy-loading fuera de transacción, es una señal correcta de que falta un `@EntityGraph` o un DTO — no se reactiva open-in-view para taparlo.
5. **Errores como `ProblemDetail` (RFC 9457)** vía `@RestControllerAdvice`, con un campo `code` estable y machine-readable del registro cerrado en `docs/12-error-contract.md` (`INSUFFICIENT_STOCK`, `RESOURCE_NOT_FOUND`, ...) — no se inventa un `code` fuera de ese registro. Nunca `null` como valor de retorno de un servicio — usa `Optional` o lanza una excepción tipada de la jerarquía `UkunException`.
6. **Ninguna query en un constructor de bean.** El legacy tiene dos servicios que buscan `tienda.nombre = 'Ukun Online'` en su constructor y no arrancan si falta la fila. Resuelve dependencias de datos perezosamente, dentro de la transacción de negocio.
7. **Toda mutación es `POST`/`PUT`/`DELETE`**, nunca `GET`.
8. **Listados paginados** (`Page<T>`), nunca `findAll()` sin límite. `Sort` siempre con `id` como último criterio.
9. **`@EntityGraph` o fetch join explícito** en cualquier consulta que vaya a serializar una colección — no confíes en el fetch type por defecto de la relación. Todo `@ManyToOne`/`@OneToOne` se declara `fetch = LAZY` explícitamente. Escalera completa de decisión (proyección → `@EntityGraph` → fetch join → `default_batch_fetch_size` como red) en `docs/03-analisis-y-patrones.md` §2.
10. **Todo en inglés: identificadores de código, esquema y contrato** (ADR-0018). El esquema nace en inglés en el baseline, así que **no** hace falta ningún `@Table(name = "...")`/`@Column(name = "...")` de traducción — si te encuentras escribiendo uno, algo va mal.

## Identidad de producto y de línea de stock (ver ADR-0015)

`product.sku` (`NOT NULL UNIQUE` + `CHECK` de formato desde el baseline) es el identificador de negocio que usa el empleado, no el `id` numérico interno. `store_stock` tiene PK subrogada (`id BIGSERIAL`) + `UNIQUE (product_id, store_id)` — la entidad no lleva `@EmbeddedId`. Cada línea de stock se direcciona por su propia URL: `PUT /api/stores/{storeId}/stock/{lineId}`.

## Imagen de producto (RF-10)

`PUT /api/admin/products/{id}/image` es la **única operación multipart del servicio**; todo lo demás es JSON. La imagen es un sub-recurso propio, no un campo del `POST`/`PUT` del producto, para que la validación de campos y la de fichero no compartan handler. Reemplazar la imagen (borrar la vieja del almacenamiento + escribir la nueva + actualizar `product.image_key`) es **una unidad con compensación** en el caso de uso, nunca pasos sueltos en el controller — el legacy borra la foto vieja antes de guardar y la pierde si el guardado falla (`docs/03-analisis-y-patrones.md` §3). El fichero se valida por su contenido real, no por la extensión, y la clave la genera el servidor: reglas completas y los `code`s `415`/`413` en `docs/08-identidad-y-seguridad.md` §9.

## Concurrencia (ver ADR-0008, ADR-0015)

- Descuento de stock en checkout → `@Lock(PESSIMISTIC_WRITE)` con `ORDER BY id` antes de bloquear (columna `id` propia de `store_stock`).
- Edición manual de stock por un empleado → `@Version` (optimista), `409 VERSION_CONFLICT` si hay conflicto.
- Edición de producto o de tipo de producto por un admin → **último que escribe gana**, sin `@Version`. Es una decisión, no un olvido: el razonamiento y cuándo revisarla están en `docs/10-modelo-de-datos.md` §2. No añadas `@Version` a `Product` "por coherencia".
- `CHECK (quantity >= 0)` en el changelog de Liquibase como red de seguridad final.

## Idempotencia del checkout (ver ADR-0016)

`POST /api/checkout/attempts` emite el `attemptId` que el cliente reenvía como cabecera `Idempotency-Key` en `POST /api/orders` — el servidor emite la clave, nunca el navegador. Semántica completa de conflicto (`409 IDEMPOTENCY_IN_PROGRESS`, `422 IDEMPOTENCY_MISMATCH`, `410 CHECKOUT_EXPIRED`) en ADR-0016 y `docs/12-error-contract.md`.

## Autorización

- El servicio valida el JWT él mismo como resource server (JWKS de `ukun-auth`) — nunca confía en cabeceras inyectadas por el gateway.
- `@PreAuthorize` para autorización por rol.
- **Toda operación solo-`admin` va bajo `/api/admin/**`, y nada más va ahí** (ADR-0020): CRUD de productos (RF-10), CRUD de tipos de producto (RF-13) y gestión de empleados (RF-11). Un `@PreAuthorize("hasRole('ADMIN')")` en un `@RequestMapping` que no empiece por `/api/admin` es un error de sitio, no una variante de estilo. El stock (RF-09) **no** va ahí: es de cualquier empleado y su autorización es de recurso, no de rol.
- **Autorización de recurso**: el `{storeId}` de la ruta *identifica* el recurso; **no autoriza nada**. El servicio resuelve la tienda del empleado por `account_id` y compara — si no coinciden, `403`. Lo mismo con "este pedido es mío". Nunca se acepta un identificador de la petición como prueba de pertenencia (el legacy compara `tiendaId` recibido por query param a mano y da eso por bueno — ese es el antipatrón). Ver `docs/08-identidad-y-seguridad.md` §4.

## Documentación del contrato

`springdoc-openapi-starter-webmvc-ui`, activo solo en desarrollo, sirve Swagger UI y genera `/v3/api-docs` — que CI diffea contra `docs/api/openapi/ukun-api.yaml`. springdoc **no** es la fuente de verdad del contrato, el YAML sí.

## Logging y auditoría

Ninguna entidad se pasa como argumento a `log.*(...)` (regla ArchUnit, ver `docs/11-observabilidad-y-logging.md` §4). Los eventos de negocio (`order.created`, `stock.updated`, ...) se emiten desde el caso de uso, dentro de su transacción, vía el emisor compartido `BusinessEvents` — nunca desde un `@Aspect` genérico, que no puede conocer el estado anterior del recurso. Los que generan auditoría escriben también en la tabla `audit_log` (`AuditService.record(...)`, misma transacción). Lista cerrada de eventos, sus nombres exactos, niveles y qué nunca se loguea: `docs/11-observabilidad-y-logging.md` §3 — no inventes un nombre de evento fuera de esa lista.

## Testing

Testcontainers + Postgres real para todo lo de persistencia (nunca H2 — las claves compuestas, las `Specification` dinámicas y `SELECT FOR UPDATE` no se comportan igual). `@WebMvcTest` con `SecurityMockMvcRequestPostProcessors.jwt()` para slices de autorización. WireMock como doble de `AccountsPort` en los tests de la saga de signup. Awaitility para el test de concurrencia de sobreventa. `ApplicationModules.verify()` desde el skeleton (ADR-0017). Convenciones completas de nomenclatura, AssertJ, object mothers y Testcontainers singleton: skill `testing` y `docs/13-testing.md` — no se repiten aquí.

## Skills externas de apoyo

`java-springboot` y `springboot-patterns` (instaladas desde skills.sh, ver `.claude/skills/README.md`) sirven de repaso general de Spring Boot. **Esta skill manda sobre las dos**: son genéricas y se escribieron sin conocer Spring Boot 4.1, Spring Modulith ni el registro cerrado de `code`s de error. Dos choques que ya están identificados y no hay que redescubrir — `springboot-patterns` propone un `ApiError` propio donde aquí va `ProblemDetail` (regla 5), y factorías estáticas `Response.from(...)` donde aquí va MapStruct (regla 3, y ADR-0009 ya revirtió esa decisión una vez). Tabla completa de conflictos en `.claude/skills/README.md`.

## Sobreingeniería a evitar aquí

No arquitectura hexagonal completa, no CQRS, no microservicios de dominio dentro de este monolito modular, no un `@Aspect` genérico de auditoría, no Hibernate Envers, no `@ApplicationModuleListener`/eventos asíncronos entre módulos (Spring Modulith se adopta por sus fronteras verificadas, no por su mecanismo de eventos — ver ADR-0017). Ver la tabla completa de "qué no hacer" en `docs/03-analisis-y-patrones.md`.
