# ADR-0017: Spring Modulith en `ukun-api` y `ukun-auth`, revirtiendo el descarte previo

## Estado
Aceptado — revierte una decisión previamente aceptada de este mismo repositorio

## Contexto
`03-analisis-y-patrones.md` §4 descartaba Spring Modulith explícitamente: *"Tentador y barato, pero ArchUnit ya cubre la necesidad real de hacer cumplir fronteras entre paquetes. Reevaluar en v1.x si el monolito crece mucho"*. Esa regla ArchUnit («los paquetes de feature no se importan entre sí salvo a través de `..api..`») sigue siendo cierta hoy en descripción, pero no en verificación: nadie la había hecho ejecutable, y la estructura por feature que la skill `spring-api` ya prescribe (`catalogo/`, `inventario/`, `tiendas/`, ...) no tenía ni un dueño de tabla declarado por módulo ni un grafo de dependencias explícito.

Como con ADR-0009 (MapStruct sobre fábricas estáticas): la premisa original —el tamaño de este dominio no exige la ceremonia de Modulith— **sigue siendo cierta**. La reversión es una decisión de estandarización y de preparación: usar la herramienta que el ecosistema Spring da para esto en vez de una regla ArchUnit hecha a mano, y dejar la extracción a microservicios preparada el día que haga falta, sin pagarla ahora.

## Decisión
Se adopta **Spring Modulith** en los dos servicios backend nuevos, con alcance y peso distintos en cada uno.

### 1. Descomposición de `ukun-api`

Derivada de los 12 controllers y 11 entidades del legacy (`ukun-clientes`/`ukun-empleados`) y de la tabla de FK de `10-modelo-de-datos.md` §6 — no de una taxonomía abstracta. Nombres en inglés (ADR-0018).

| Módulo | Tablas que posee | RF / casos de uso | `allowedDependencies` |
|---|---|---|---|
| `shared` | — | `UkunException` + jerarquía, `@RestControllerAdvice`, `Clock`, tipos comunes | módulo abierto (`type = OPEN`) |
| `catalog` | `product`, `product_type` | RF-01 (listar, ficha), RF-10 (CRUD admin, imagen) | `storage`, `shared` |
| `stores` | `store`, `address` de tienda | RF-02, resolución de la tienda online (`is_online`) | — |
| `storage` | — | `FileStoragePort` + adaptador (filesystem hoy, S3/MinIO en v1.1) | — |
| `identity` | — | `AccountsPort` + adaptador HTTP a `ukun-auth`; principal del JWT | — |
| `inventory` | `store_stock` | RF-09 (upsert/borrado por empleado, optimista), descuento en checkout (pesimista) | `catalog`, `stores`, `shared` |
| `customers` | `customer`, `address`, `card` de perfil | RF-05, RF-06 (lado datos + saga) | `stores`, `identity`, `shared` |
| `staff` | `employee` | RF-11 (alta y listado por tienda) | `stores`, `identity`, `shared` |
| `cart` | `cart_item` | RF-03 | `catalog`, `inventory`, `customers`, `shared` |
| `orders` | `customer_order`, `order_line`, `idempotency_key`, intento de checkout | RF-04 + ADR-0016 | `cart`, `inventory`, `customers`, `catalog`, `shared` |

Grafo acíclico, orden topológico: `shared` → (`catalog`, `stores`, `storage`, `identity`) → (`inventory`, `customers`, `staff`) → `cart` → `orders`. `orders` es el único módulo con cuatro dependencias de dominio declaradas, y debe serlo: es el orquestador del checkout, el caso de uso que toca más agregados a la vez.

**Por qué `shared` aparece en la columna pese a ser abierto.** La primera redacción de este ADR daba por hecho que un módulo `type = OPEN` está fuera de la comprobación y que nadie tiene que declararlo. **No es así**, comprobado contra Modulith 2.1.0 al escribir el primer módulo con código (v0.3, `catalog`): `OPEN` levanta la *encapsulación* de los subpaquetes del módulo destino —se puede importar `shared.web.PageResponse` sin publicarlo como `NamedInterface`—, pero `allowedDependencies` es una lista **cerrada**, y cualquier destino fuera de ella es una violación, `shared` incluido. El primer `import` de `PageResponse` desde `catalog` rompía `verify()` con *«Module 'catalog' depends on module 'shared'… Allowed targets: storage»*.

Son dos mecanismos independientes, y conviene no confundirlos: uno decide **qué se ve** de un módulo, el otro **a quién se puede llamar**. `stores`, `storage` e `identity` no declaran lista —es decir, la tienen sin restringir— y por eso su columna sigue vacía: no necesitan nombrar a `shared` ni a nadie.

**Qué NO es módulo, y por qué:** `address` y `card` no son un módulo propio — son datos sin comportamiento propio que pertenecen a `customers` y a `stores` (y en el pedido dejan de ser tablas referenciadas para pasar a columnas embebidas por ADR-0011). Convertirlos en módulo `addresses` sería una tabla compartida disfrazada de frontera de dominio. `identity` y `storage` son, al contrario, módulos **sin ninguna tabla**: existen únicamente para encapsular un puerto (`AccountsPort`, `FileStoragePort`), que es exactamente la frontera externa real que `03-analisis-y-patrones.md` §3 ya había identificado como los "dos puertos, y solo dos" de este dominio.

### 2. El cambio real que impone: referencias por id, no por entidad, entre módulos

Un módulo Modulith no puede navegar directamente a una `@Entity` de otro módulo. Las asociaciones JPA que hoy cruzan lo que será una frontera de módulo pasan a ser un `Long` desnudo; la **FK sigue existiendo en la base** (la integridad referencial y las políticas `ON DELETE` de `10-modelo-de-datos.md` §6 no cambian), lo que desaparece es el `@ManyToOne` del lado Java.

Cruces verificados sobre las entidades reales del legacy — son **seis en total**, todos `@ManyToOne` del lado propietario, ninguna colección `@OneToMany` inversa que romper:

| Asociación hoy (legacy) | Queda en `ukun-api` | Coste de la conversión |
|---|---|---|
| `StockProducto.producto`, `StockProducto.tienda` | `Long productId`, `Long storeId` | **cero** — ya están mapeadas `insertable = false, updatable = false` como parte de la clave compuesta; es navegación redundante sobre un id que ya está en la fila |
| `ProductoPedido.producto` | `Long productId` | **cero** — mismo caso, y el nombre a mostrar ya se congela como instantánea (`order_line.product_name`, ADR-0011), así que ni siquiera hace falta resolver el producto para pintar la línea |
| `Carrito.producto` | `Long productId` | bajo — nombre y precio vigente se piden a `CatalogApi` en una llamada por lote al listar el carrito |
| `Pedido.cliente` | `Long customerId` | bajo — el historial de pedidos se consulta siempre acotado a un `customerId` ya conocido por el token |
| `Cliente.tiendaFavorita` | `Long favoriteStoreId` | bajo — una única lectura a `StoresApi` al pintar el perfil |
| `Empleado.tienda` | `Long storeId` | bajo — idéntico al anterior |

**Comprobación explícita de que esto no reintroduce el defecto #12 (N+1)**, sobre las cuatro consultas culpables que `03-analisis-y-patrones.md` ya identificó:

- catálogo + tipo → **intra-módulo** (`catalog` posee ambas tablas): el `@EntityGraph` nombrado sigue funcionando exactamente igual, no cruza ninguna frontera de Modulith.
- tiendas + dirección → **intra-módulo** (`stores`): igual.
- pedidos + líneas + producto → **intra-módulo** para pedido↔líneas; el nombre del producto ya no se resuelve con un join, es la instantánea `order_line.product_name` fijada por ADR-0011. Modulith no empeora esta consulta, la simplifica.
- stock + producto (la única que sí cruza, `inventory` → `catalog`) → se resuelve con **una** llamada en lote, `CatalogApi.summariesByIds(Set<Long> ids) → Map<Long, ProductSummary>`, nunca una consulta por fila. El resultado son dos consultas en total en vez de un único join — peor plan de ejecución en abstracto, pero N+1 estructuralmente imposible: no hay manera de escribir el bucle que lo cause, porque la API del módulo no expone una forma de pedir un producto a la vez dentro de un `forEach`.

Ninguna de las cuatro consultas se degrada. Se deja constancia explícita en este ADR porque es la objeción obvia a modularizar un backend con problemas de N+1 ya diagnosticados, y merece una respuesta verificada, no una promesa.

### 3. El ciclo que aparece, y la decisión de contrato que fuerza

`catalog` e `inventory` se necesitan mutuamente en la lectura: RF-01 quiere pintar disponibilidad junto al catálogo, RF-09 quiere pintar el nombre del producto junto al stock. Eso es un ciclo de dependencias, y `ApplicationModules.verify()` lo rechaza en build.

**Se rompe en el sentido `inventory → catalog`** (la tabla de dependencias de arriba ya lo refleja), dejando `catalog` sin ninguna dependencia hacia `inventory`. La consecuencia visible es que la disponibilidad deja de viajar en la misma respuesta que el catálogo:

```
GET /api/products?name=&type=&page=          -> catalog, sin disponibilidad
GET /api/availability?store={id}&products=   -> inventory, en lote
```

El frontend lanza ambas peticiones en paralelo (dos queries de TanStack Query) y compone en pantalla; RF-01 ("mostrar disponibilidad en la tienda online y, si aplica, en la tienda favorita") se sigue cumpliendo igual. Efecto colateral positivo, independiente del ciclo: el catálogo es cacheable con un TTL largo (cambia poco) y la disponibilidad no (cambia con cada venta) — unidos en una sola respuesta, el catálogo heredaría la volatilidad de la disponibilidad sin necesidad.

Esta separación **cambia la forma del contrato REST** respecto a lo que se hubiera diseñado sin Modulith, así que entra en `docs/api/openapi/ukun-api.yaml` **antes de SINC-1 (2026-08-11)** — es la consecuencia de mayor urgencia de todo este ADR.

### 4. Descomposición de `ukun-auth`

| Módulo | Contenido | `allowedDependencies` |
|---|---|---|
| `accounts` | `account`, `account_role`, hash de contraseña, endpoints `/internal/accounts` | — |
| `registration` | saga de signup, `registration_request`, idempotencia de alta | `accounts` |
| `oauth2` | `RegisteredClient`, JWKS, personalización de claims, rate limit de login (Bucket4j) | `accounts` |

Aquí el beneficio es honestamente menor, y se deja dicho: `ukun-auth` es deliberadamente anémico (ADR-0004) — dos tablas de dominio, tres endpoints internos —, así que declarar módulos aquí es coherencia de herramienta con `ukun-api` más que una frontera real que necesite hacerse cumplir. Se acepta como decisión explícita, no porque el argumento a favor pese lo mismo que en `ukun-api`.

### 5. Convenciones

- **Forma de un módulo**: `package-info.java` con `@ApplicationModule(allowedDependencies = {...})` por paquete de nivel superior. La API pública del módulo es su paquete raíz (una interfaz `XxxApi` + DTOs `record`); entidades, repositorios y casos de uso viven en subpaquetes internos que Modulith prohíbe importar desde fuera del módulo. Ningún módulo expone una `@Entity` en su API — refuerza la regla ArchUnit ya existente ("los métodos públicos de un servicio no devuelven tipos `@Entity`").
- **Qué reemplaza y qué no**, corrección necesaria sobre `03-analisis-y-patrones.md` §3, que hoy enumera "ocho reglas" de ArchUnit: `ApplicationModules.verify()` **sustituye únicamente** la regla "los paquetes de feature no se importan entre sí salvo a través de `..api..`". Las **otras siete permanecen intactas** — Modulith no sabe nada de `@Data` en entidades, de `Float` para dinero, de una entidad pasada como argumento a `log.*(...)`, ni de un `toString()` que enmascare secretos. La lista pasa a describirse como "siete reglas ArchUnit + verificación de módulos de Modulith".
- **Qué NO se adopta de Modulith**: el *Event Publication Registry* ni `@ApplicationModuleListener`. El descarte de "domain events + patrón outbox" de `03-analisis-y-patrones.md` §4 sigue vigente sin cambios — los eventos de negocio siguen siendo `BusinessEvents` (log estructurado + escritura en `audit_log` en la misma transacción del caso de uso, ver `11-observabilidad-y-logging.md`), síncronos. Adoptar Modulith **no** enciende por sí solo un mecanismo de eventos asíncronos entre módulos; el día que haga falta (p. ej. notificaciones por email, `03` §4), la infraestructura de módulos ya estará puesta, pero eso no ocurre en este ADR.
- **Transacciones**: una transacción de negocio por caso de uso, que atraviesa llamadas entre módulos como llamadas Java normales dentro del mismo proceso. El checkout descuenta stock (`inventory`) y crea el pedido (`orders`) en la **misma** transacción — es justo la propiedad que se perdería el día que estos dos módulos se extrajeran a servicios separados, y es la razón de fondo por la que esa extracción no se hace en este release ni está prevista salvo que el negocio lo justifique.
- **Verificación en CI**: `spring-modulith-starter-test`, con una clase `ModulithTest` por servicio que llama a `ApplicationModules.of(UkunApiApplication.class).verify()` (análogamente en `ukun-auth`), desde el mismo commit del skeleton — no se añade después. `spring-modulith-docs` genera el diagrama de módulos vía `Documenter`, que **sustituye** a mantener un diagrama de paquetes a mano en `01-arquitectura.md`. El endpoint de Actuator de módulos se activa solo en el perfil de desarrollo.
- **Límite explícito de esta decisión**: adoptar Modulith no es un compromiso de extraer microservicios de dominio. `03-analisis-y-patrones.md` §4 sigue descartando esa opción sin cambios. Lo único que cambia es que, el día que un módulo concreto justifique salir del monolito por una razón de negocio real (no de curiosidad técnica), su frontera ya estará declarada y verificada en CI en vez de tener que descubrirse a posteriori escarbando en el código.

## Consecuencias
- Siete módulos con dueño de tabla claro en `ukun-api`, tres en `ukun-auth`, cada uno con su `package-info.java` y su entrada en el grafo de dependencias — verificable en build, no solo descrito en un documento.
- Seis asociaciones JPA existentes en el legacy se convierten a referencias por id al escribir las entidades nuevas; ninguna de las cuatro consultas culpables del defecto #12 se degrada (comprobación §2).
- Aparece un ciclo real (`catalog`↔`inventory`) que se resuelve separando el endpoint de disponibilidad del catálogo — cambio de forma del contrato REST que debe entrar antes de SINC-1.
- El diagrama de módulos deja de mantenerse a mano; lo genera `Documenter` en cada build de desarrollo.
- Coste de mantenimiento: dos configuraciones de Modulith (una por servicio) en vez de una regla ArchUnit central; se acepta explícitamente que en `ukun-auth` el beneficio es menor que en `ukun-api`.

## Alternativas descartadas
- **Mantener solo la regla ArchUnit de fronteras entre paquetes** (decisión previa de `03-analisis-y-patrones.md` §4): sigue siendo un argumento válido para el tamaño actual del dominio. Se descarta aquí no porque fuera erróneo, sino por preferir la herramienta estándar del ecosistema Spring para este problema y dejar preparada una extracción futura sin coste añadido hoy.
- **Modulith solo en `ukun-api`, `ukun-auth` sin modularizar**: descartado porque `ukun-auth` ya tiene una separación interna clara (cuentas / saga de registro / OAuth2) que Modulith puede hacer cumplir con coste marginal, aunque el beneficio sea menor que en `ukun-api`.
- **Absorber `ukun-auth` como módulo de `ukun-api`**: revertiría ADR-0004 y parcialmente ADR-0003 (frontera de identidad y BFF en el gateway) sin que este ADR tenga mandato para tocar esas decisiones; la separación de `ukun-auth` es por seguridad (superficie de credenciales, base propia), no por escala, y ese argumento no cambia por adoptar Modulith.
