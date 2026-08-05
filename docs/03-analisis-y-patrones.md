# Análisis de código, clean code y patrones

> **Fuente de verdad de:** el catálogo numerado de defectos del legacy (#1–#23), los patrones que los resuelven, las reglas ArchUnit y lo que se descarta por sobreingeniería.
> **Léelo si:** vas a diseñar una clase, un agregado, un repositorio, o a decidir si un patrón aplica aquí.
> **No decide:** cómo se escribe un test (`13-testing.md`), ni el registro de errores (`12-error-contract.md`), ni el esquema (`10-modelo-de-datos.md`).
> **Los números de defecto de §1 son estables** y se citan desde el resto de documentos y desde las skills — no los renumeres.

Cada patrón de esta lista está aquí porque resuelve un defecto **real y localizado** del código legacy, no porque sea buena práctica en abstracto. Las rutas son relativas a la raíz del repo. El legacy no está en marcha: esta lista es la especificación de qué **no** reproducir, no un plan de arreglo in situ.

## 1. Defectos concretos encontrados (referencia)

| # | Defecto | Dónde |
|---|---|---|
| 1 | `Pedido` y `ProductoPedido` ambos con Lombok `@Data` y referencia mutua → `StackOverflowError` en `toString()`/`equals()`/`hashCode()` | `ukun-clientes/.../entity/Pedido.java`, `.../entity/ProductoPedido.java` |
| 2 | Race condition de sobreventa: lectura-modificación-escritura del stock sin bloqueo ni `@Version` | `ukun-clientes/.../services/PedidoServiceImpl.java` (`reducirStockOnline`) |
| 3 | Mutaciones de estado por `GET`: `/carrito/add`, `/carrito/delete`, `/pedidos/hacer`, `/stock/editar`, `/stock/eliminar`, `/admin/productos/eliminar/{id}` | controllers de ambos módulos |
| 4 | Contraseñas en texto plano, columna `VARCHAR(16)` (incompatible con un hash BCrypt de 60 chars sin migrar columna) | `database/init.sql`, `LoginService`/`LoginServiceImpl` |
| 5 | Dinero en `Float` sobre columnas `DECIMAL(10,2)` | `Producto.precio`, `Pedido.total`, `ProductoPedido.precioUnidad/precioTotal` en ambos módulos |
| 6 | Consulta a BD **en el constructor** del bean, buscando la tienda por el literal `"Ukun Online"` — si falta la fila, la app no arranca | `ProductoServiceImpl`, `PedidoServiceImpl` (ukun-clientes) |
| 7 | `StockServiceImpl.guardar` compara un `StockProducto` con un `Optional` → siempre `true`, rama de error muerta | `ukun-empleados/.../service/StockServiceImpl.java` |
| 8 | Entidades duplicadas y ya **divergidas** entre módulos sobre la misma tabla (fetch LAZY/EAGER distinto, `@NotBlank` vs `@NotEmpty`, `Direccion` sin `@GeneratedValue` en empleados) | entidades de ambos módulos, ver exploración de infraestructura |
| 9 | Open redirect: `AbsController.pathOrigenRedirect` construye `"redirect:" + Referer` sin validar | `ukun-clientes/.../controllers/AbsController.java` |
| 10 | Contraseña logueada en claro en cada intento fallido de login | `ukun-empleados/.../controllers/HomeController.java` |
| 11 | Servicios devuelven `null` en vez de `Optional`/excepción; sin `@ControllerAdvice`; mensajes de error genéricos idénticos para causas distintas | prácticamente todos los `*ServiceImpl` |
| 12 | N+1 en `/productos`, `/pedidos`, `/tiendas`, `/empleados/stock` | fetch EAGER por defecto en relaciones `@OneToOne`/`@ManyToOne` sin declarar |
| 13 | `@SequenceGenerator` sin `@GeneratedValue` en `Tienda` y `TipoProducto` (ambos módulos) — inserción manual de PK, trampa latente | entidades `Tienda`, `TipoProducto` |
| 14 | Cardinalidad mal modelada: `Producto.tipo` y `Empleado.tienda` como `@OneToOne` cuando son N:1 | entidades `Producto`, `Empleado` |
| 15 | Tabla `tarjeta` almacena PAN completo y CVV en claro — CVV nunca debe persistirse (PCI-DSS) | `database/init.sql`, entidad `Tarjeta` |
| 16 | `ENTRYPOINT`/`COPY` con `*.jar` en exec form: funciona solo porque el jar resultante se llama literalmente `*.jar` | `ukun-clientes/Dockerfile`, `ukun-empleados/Dockerfile` |
| 17 | nginx sin `client_max_body_size`: anula en la práctica el límite de subida de 10MB configurado en Spring | `nginx/nginx.conf` |
| 18 | Cero cobertura de test; los dos `contextLoads()` existentes no pasan sin una BD real con datos concretos | `src/test/` de ambos módulos |
| 19 | El pedido **comparte** fila de `direccion`/`tarjeta` con el perfil del cliente en vez de copiarla — pese a que RF-04 exige "copia la dirección y la tarjeta actuales" — así que editar el perfil reescribe silenciosamente la dirección de envío y el pago de todos los pedidos pasados | `Pedido(Cliente)` en `ukun-clientes/.../entity/Pedido.java` + `@SessionAttributes("cliente")` en `AbsController`/`PerfilController` (ver ADR-0011) |
| 20a | `PerfilController.editar` loguea la entidad `Cliente` completa (`@Data`) → contraseña, PAN y CVV en cada edición de perfil | `ukun-clientes/.../controllers/PerfilController.java` |
| 20b | `EmpleadosController` loguea el `Empleado` recién creado (`@Data`) → contraseña del alta en claro en el log | `ukun-empleados/.../controllers/EmpleadosController.java` |
| 21 | `findProductoNotInTienda` usa `NOT IN` sobre una subconsulta — semántica de tres valores, cualquier `NULL` en la subconsulta vacía el resultado entero; además fuerza un subplan materializado en vez de un anti-join | `ukun-empleados/.../models/dao/IStockProductoCrud.java` |
| 22 | Búsqueda de catálogo sin `unaccent`: `"sofa"` no encuentra `"Sofá de cuero"`, y el catálogo semilla está lleno de acentos | filtro por nombre de `ProductosController`/`ProductoServiceImpl`, ambos módulos |
| 23 | `ORDER BY precio` sin desempate por `id` — con `Page<T>` un producto puede salir en dos páginas o en ninguna; el catálogo semilla ya tiene un empate real (`Litera infantil` y `Sofá de cuero`, ambos a 399.99) | ordenación de `/productos` |

## 2. Patrones de alto retorno — aplicar sí o sí

### DTOs como `record` + prohibir serializar entidades
Resuelve de un golpe los defectos #1, #4 (fuga accidental de `contrasenna` en JSON) y permite fijar `spring.jpa.open-in-view: false` desde el primer día — con eso, cualquier acceso *lazy* fuera de transacción falla en tests en vez de degradarse en silencio a N+1 en producción, que es justo lo que hoy sostiene `pedidos.html` sin que nadie lo haya decidido.

### Mapeo entidad↔DTO con MapStruct (ver ADR-0009)
Un mapper por agregado (`ProductoMapper`, `PedidoMapper`, ...), `componentModel = "spring"` para que se inyecten como beans, `unmappedTargetPolicy = ERROR` para que un campo nuevo en el DTO sin mapeo explícito rompa la compilación en vez de descubrirse en producción. **Esto reemplaza la decisión anterior de fábricas estáticas** (`ProductoResponse.from(Producto p)`): la premisa de entonces —~10 agregados, ~25 DTOs, dominio pequeño— sigue siendo cierta, el cambio es por estandarizar sobre la herramienta que el ecosistema Spring usa por defecto para este problema, no porque el argumento original fuera erróneo. Ver ADR-0009 para el detalle completo de la reversión. Lo que sigue prohibido siempre: `ModelMapper`/`BeanUtils.copyProperties` — mapeo por reflexión es exactamente el tipo de error que solo aparece en runtime.

Trampa a vigilar: el procesador de anotaciones de MapStruct debe declararse **después** del de Lombok en `annotationProcessorPaths` del POM — en el orden contrario, Lombok no ha generado aún los getters/setters cuando MapStruct intenta leerlos.

### `ProblemDetail` (RFC 9457) + `@RestControllerAdvice` + excepciones tipadas
Sustituye los `flash.addFlashAttribute("error", "...")` genéricos (defecto #11) por un contrato estable, con `code` *machine-readable* y estable: React hace un lookup sobre él para decidir qué pintar (nunca sobre `title`/`detail`, que son texto de operador en inglés fijo). El registro **cerrado** de `code`s, su jerarquía de excepciones y la regla de proceso para añadir uno nuevo viven en `docs/12-error-contract.md` — no se repiten aquí. Los textos de usuario viven en el front, indexados por `code` (ver ADR-0019), no en un `messages.properties` del backend.

### `Optional`/excepciones en vez de `null`
`findById(id).orElseThrow(() -> new RecursoNoEncontrado("producto", id))` en el servicio, un único handler global. Elimina el `if (x == null)` repetido en cada controller (defecto #11).

### Agregado `Pedido` con la lógica dentro de sí mismo
Hoy el total del carrito se calcula **en el controller** (`CarritoController`) y el total del pedido se calcula **en la entidad** (`Pedido.setProductosYTotal`) — dos implementaciones de la misma regla que pueden divergir si cambia el precio entre pantalla y confirmación. Se unifica: el agregado recibe las líneas del carrito, calcula precio unitario congelado, subtotales y total, y valida sus propios invariantes (`total == Σ líneas`, `cantidad > 0`, sin líneas duplicadas). Nadie construye una línea de pedido desde fuera del agregado.

### Agregado "stock de tienda" con dos estrategias de concurrencia distintas {#agregado-stock}
No es el mismo problema disfrazado dos veces — son dos problemas distintos y merecen soluciones distintas:

- **Checkout → bloqueo pesimista ordenado** (resuelve el defecto #2):
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from StoreStock s where s.id in :ids order by s.id")
  List<StoreStock> lockForUpdate(@Param("ids") List<Long> ids);
  ```
  El `ORDER BY` determinista es lo que evita interbloqueos cuando dos pedidos concurrentes comparten productos en distinto orden. Con la PK subrogada de ADR-0015, el orden es simplemente `s.id` — antes de esa decisión habría sido `s.id.productoId` sobre una clave embebida. Optimista con reintento sería peor aquí: un pedido de 6 líneas reintentaría entero por una colisión en una sola.
- **Edición manual de stock por un empleado → optimista con `@Version`.** Aquí no queremos que "gane el último": si un empleado abrió la pantalla hace 10 minutos y otro ya cambió el dato, lo correcto es un `409 VERSION_CONFLICT` (`docs/12-error-contract.md`) pidiendo recargar, no sobreescribir en silencio.
- Red de seguridad final, independiente de que el dominio se equivoque: `CHECK (quantity >= 0)` en la propia tabla.

Detalle completo del esquema (SKU, PK subrogada) en ADR-0015; nombres en inglés según ADR-0018.

### Idempotencia en `POST /api/orders`
El intento de checkout (`POST /api/checkout/attempts`, ADR-0016) emite el identificador que el cliente reenvía como `Idempotency-Key` — no lo genera el navegador. Tabla `idempotency_key(key, customer_id, order_id, request_hash, response_body, created_at)` con único `(key, customer_id)`. Resuelve doble clic, reintento de red y "actualizar" del navegador tras confirmar, con semántica explícita de conflicto (`409 IDEMPOTENCY_IN_PROGRESS`, `422 IDEMPOTENCY_MISMATCH`) — detalle completo en ADR-0016. Un mecanismo análogo (clave emitida por el propio proceso, no por el cliente) se reutiliza en la saga de signup (ver `08-identidad-y-seguridad.md`).

### Semántica HTTP correcta
Todas las mutaciones por `GET` (defecto #3) pasan a `POST`/`PUT`/`DELETE`. Esto, por sí solo, cierra el CSRF-por-`<img src>` y hace que un prefetcher de navegador deje de poder borrar un producto. Y el open redirect (defecto #9) simplemente desaparece: una API REST no redirige, la navegación la decide el frontend.

### Configuración tipada + eliminar las queries en constructores de bean
Resuelve el defecto #6 en dos capas: columna `tienda.es_online BOOLEAN` (el concepto de "tienda online" pasa a ser del modelo, no un nombre mágico) + resolución perezosa **dentro de la transacción de negocio**, nunca en el constructor. `@ConfigurationProperties` para lo que sí es configuración (p. ej. el directorio de subida de imágenes, hoy una constante hardcodeada `"/opt/resources/uploads/"`).

### Prohibir `@Data` en entidades JPA
`@Data` genera `equals`/`hashCode`/`toString` sobre *todos* los campos, incluidas las asociaciones — origen directo del defecto #1 y de comparaciones incorrectas como `clienteActual.equals(clienteModificado)` usada hoy para detectar "¿cambió algo?" en la edición de perfil. Regla: `@Getter @Setter` en entidades, `equals`/`hashCode` por id (o clave de negocio) escritos a mano, `toString` solo con escalares. Se hace cumplir con ArchUnit, no con disciplina (ver más abajo).

### `BigDecimal` para dinero
Resuelve el defecto #5: `precio`/`total` pasan a `BigDecimal` + `@Column(precision = 10, scale = 2)` + `setScale(2, RoundingMode.HALF_UP)` centralizado en el agregado que calcula el total. Un value object `Dinero` completo (con moneda) es opcional mientras solo haya EUR — se deja para v1.x si aparece una segunda moneda.

### N+1 y paginación
`@EntityGraph` en las cuatro consultas culpables del defecto #12 (`/api/products`+tipo, `/api/orders`+líneas+producto, `/api/stores`+dirección, `/api/stores/{id}/stock`+producto), `hibernate.default_batch_fetch_size` como red global, y `Page<T>` en cualquier listado que hoy es un `findAll()` sin límite. `Sort` siempre con `id` como último criterio (defecto #23) para que la paginación sea estable. La cuarta consulta cruza la frontera de módulo `inventory`→`catalog` (ADR-0017, `docs/14-modules.md`) y se resuelve con una llamada en lote, no con un fetch join — el detalle completo, incluida la comprobación de que no reintroduce N+1, está en esos dos documentos.

Índices de FK: **no** los cuatro citados antes tal cual — `cart_item.customer_id` y `order_line.order_id` ya son la columna líder de su PK compuesta, así que un índice adicional ahí es puro coste de escritura sin beneficio. Los que sí hacen falta y el resto del detalle (con la consulta que justifica cada uno) están en `10-modelo-de-datos.md` §4.

### Repositorios y servicios: cómo se escriben {#repositorios-y-servicios}

El diagnóstico de arriba (defectos #8, #12, #14, #21, #22, #23) dice qué está mal en el legacy; esto fija cómo se escribe el código nuevo para que no reaparezca.

**Fetch: todo `LAZY`, siempre explícito.** Incluidos `@ManyToOne`/`@OneToOne`, que son EAGER por defecto en JPA — causa directa del defecto #12, y de que el defecto #14 (`@OneToOne` donde la cardinalidad real es N:1) duela en rendimiento además de en semántica. Se declara `fetch = FetchType.LAZY` aunque sea redundante en una colección `@OneToMany`: la ausencia de anotación no distingue "lazy a propósito" de "no lo pensé".

**Escalera de decisión para leer**, en orden — no se baja un escalón sin poder decir por qué el anterior no valía:
1. Proyección (`record` o interfaz de proyección) directamente en el repositorio, si la pantalla solo necesita escalares. No trae entidades, no puede generar N+1.
2. `@EntityGraph` **nombrado** cuando sí hace falta el agregado completo — las cuatro consultas de arriba.
3. Fetch join explícito solo cuando además hay que filtrar u ordenar por la relación unida.
4. `hibernate.default_batch_fetch_size` es la **red de seguridad**, nunca el plan A.

**Prohibiciones concretas:** derived query con más de tres propiedades o que mezcle `And`/`Or` → `@Query` nombrada o `Specification` · `NOT IN` sobre subconsulta → `NOT EXISTS` (defecto #21) · `findAll()` sin `Pageable` · `Sort` sin `id` como último criterio (defecto #23) · `LIKE` sobre nombre sin `unaccent(lower(...))` (defecto #22) · consulta dentro de un constructor de bean (defecto #6, ya prohibido arriba).

**Filtros dinámicos del catálogo** (nombre + tipo + rango de precio, Q1 de `10-modelo-de-datos.md`) → una `Specification` compuesta y **un solo** método paginado, nunca una explosión combinatoria de `findByNameAndProductTypeAndPriceBetween...`.

**Transacciones:** `@Transactional(readOnly = true)` a nivel de clase de servicio, `@Transactional` solo en los métodos que escriben, nunca en un `@RestController`. Con `open-in-view: false` (ya decidido más arriba), cualquier fuga de acceso lazy fuera de la transacción revienta en test — es el comportamiento buscado, no un fallo a silenciar.

**Servicios sin `null`:** `Optional` o una excepción tipada del registro de `docs/12-error-contract.md`. Ningún `if (x == null)` en un controller.

**Verificación mecánica del N+1:** un test de persistencia con contador de sentencias (Hibernate `Statistics` o `datasource-proxy`) que falla si un listado emite más sentencias SQL de las declaradas como esperadas. Convierte el defecto #12 en algo que CI detecta en vez de algo que alguien tiene que recordar en cada PR — detalle en `docs/13-testing.md`.

## 3. Patrones de retorno medio — aplicar cuando toque, no antes

### Puertos y adaptadores, pero solo donde hay una frontera externa real
No arquitectura hexagonal completa (ver más abajo). Dos puertos, y solo dos:
- `AccountsPort` → adaptador HTTP hacia `ukun-auth`. Permite testear la saga de signup con un doble en vez de levantar el servicio de auth entero.
- `FileStoragePort` → `LocalFileSystemAdapter` en v0.7, `S3Adapter`/`MinioAdapter` en v1.1. Sustituye la ruta hardcodeada de `UploadFileServiceImpl` (`"/opt/resources/uploads/"`).

Cada uno es además un módulo de Spring Modulith sin tabla (`identity`, `storage` — ADR-0017 §2), existiendo únicamente para encapsular su puerto.

### Casos de uso explícitos para orquestación que hoy vive en el controller
`ProductosController.guardar` (empleados) hoy valida, borra la foto vieja, copia la nueva, decide el mensaje y guarda, todo en el controller, y **no** como una unidad: si el `save` falla, la foto vieja ya está borrada. Se extrae a un caso de uso (`GuardarProductoUseCase`) que trata borrado+copia+guardado como una sola operación con compensación si algo falla a mitad de camino. (Bonus real encontrado en el camino: el log de esa función está invertido — registra "foto eliminada" precisamente cuando el borrado falla.)

### ArchUnit — siete reglas, más verificación de módulos con Spring Modulith

```
· ninguna clase de ..web.. importa jakarta.persistence
· ninguna @Entity está anotada con @Data
· los @RestController no dependen de repositorios directamente
· los métodos públicos de un servicio no devuelven tipos @Entity
· ninguna @Entity usa Float o Double
· toda clase Request/Command con un campo password|token|cvv|number|pan declara toString()
· ningún método de producción pasa un tipo @Entity como argumento a log.*(...)
```

La regla que faltaba en esta lista — "los paquetes de feature no se importan entre sí salvo a través de `..api..`" — no se hace cumplir con una regla ArchUnit escrita a mano: la sustituye `ApplicationModules.verify()` de **Spring Modulith** (ADR-0017, revierte el descarte de este mismo documento en §4), que además comprueba que ningún ciclo de dependencias se cuele entre módulos, algo que ArchUnit por sí solo no verificaba. Detalle de la descomposición en módulos, el grafo de dependencias y las convenciones de escritura: ADR-0017 y `docs/14-modules.md`.

Las dos últimas reglas ArchUnit hacen cumplir en CI lo que hoy son los defectos #10/#20a/#20b (secretos logueados vía entidad) — ver `11-observabilidad-y-logging.md` §4. El resto, junto con `verify()`, hace cumplir en CI las reglas de este documento sin depender de que alguien se acuerde en cada PR.

### Documentación del contrato: springdoc
`springdoc-openapi-starter-webmvc-ui` genera `/v3/api-docs` y sirve Swagger UI en `ukun-api` (perfil de desarrollo únicamente). No es la fuente de verdad del contrato — el YAML versionado en `docs/api/openapi/` lo es, escrito design-first — pero es lo que permite explorar y probar la API a mano sin una colección aparte, y es el productor del `/v3/api-docs` que el check de deriva de CI compara contra ese YAML. Detalle de uso en `06-entorno-local-tilt.md`.

### Estrategia de testing
- **Testcontainers + Postgres real** para todo lo de persistencia. Nunca H2: las claves compuestas, las `Specification` dinámicas y `SELECT ... FOR UPDATE` no se comportan igual en H2. Con Boot 4 y `@ServiceConnection` son dos líneas de configuración.
- **`@WebMvcTest` por controller** con `SecurityMockMvcRequestPostProcessors.jwt()` para cubrir autorización sin levantar el contexto completo.
- **WireMock como doble de `AccountsPort`** en los tests de la saga de signup — permite probar los caminos de fallo (`409 EMAIL_ALREADY_EXISTS`, timeout, 500) sin levantar `ukun-auth` entero.
- **El test más valioso del proyecto**: dos hilos comprando la última unidad de un producto; se comprueba que exactamente uno recibe 201 y el otro 409, y que `quantity == 0` al final. Es la prueba de que el defecto #2 murió de verdad. Awaitility para sincronizar el arranque de los hilos y esperar el resultado sin `Thread.sleep`.
- **`@SpringBootTest` end-to-end para 4 recorridos**: signup (con `AccountsPort` mockeado), checkout, alta de stock, alta de producto con imagen.
- **Contract check en CI**: se genera `/v3/api-docs` desde springdoc y se diffea contra el OpenAPI versionado en `docs/api/openapi/`. Esto es contract testing sin traer Pact — con un único consumidor (el frontend de este mismo repo) y un único productor, Pact es coste sin beneficio proporcional.
- **Playwright, tres recorridos**, no más: cliente completo (signup → compra), admin crea producto, empleado edita stock. Corren contra el stack levantado por Tilt.
- **Sin umbral global de cobertura** (invita a tests basura rellenando líneas). Cobertura informativa vía Jacoco, con umbral solo en `orders` e `inventory` — que es donde está el dinero y la concurrencia.

**Cómo se escribe cada uno de estos tests** (nomenclatura, AssertJ, object mothers, Testcontainers singleton, mocks solo en fronteras, y el reparto completo por fase de CI incluyendo Vitest y React Testing Library): `docs/13-testing.md` y skill `testing` — no se duplica aquí.

**Nota explícita sobre el legacy:** no se escriben tests de caracterización sobre `ukun-clientes`/`ukun-empleados`. No están en marcha y se borran (`04-roadmap.md`); el inventario de endpoints (`docs/02-requisitos.md`) ya actúa como especificación de comportamiento, y la paridad funcional la validan los smokes de Playwright contra el sistema nuevo.

## 4. Sobreingeniería para este tamaño — explícitamente descartado

**Spring Modulith ya no está en esta tabla.** Este documento lo descartaba en su primera versión ("ArchUnit ya cubre la necesidad real"); ADR-0017 revierte esa decisión con el mismo criterio que ADR-0009 usó para MapStruct — la premisa original no era errónea, se adopta por estandarizar sobre la herramienta del ecosistema y por dejar preparada una extracción a microservicios que hoy no se hace. Detalle completo, incluida la descomposición en módulos y el grafo de dependencias: ADR-0017 y `docs/14-modules.md`.

| Patrón/herramienta | Por qué no (todavía) |
|---|---|
| Arquitectura hexagonal completa (entidades de dominio puras + entidades JPA separadas + mappers entre ambas) | Triplicaría el código de persistencia a cambio de una portabilidad de base de datos que nunca se va a ejercer. La versión selectiva de la sección anterior (dos puertos) da el 90% del beneficio por el 10% del coste |
| CQRS / event sourcing | Un catálogo de ~24 productos y ~12 tablas no lo justifica |
| Domain events + patrón outbox | Se justificaría el día que haya notificaciones por email o integración con un sistema externo, que hoy no existe |
| Microservicios de dominio (catálogo, pedidos, inventario como servicios separados) | El monolito modular (Spring Modulith, ver ADR-0017) + ArchUnit da las fronteras internas sin el coste operativo de N despliegues. La única separación justificada es `ukun-auth`, y es por seguridad, no por escala |
| Caché distribuida (Redis) para catálogo | `@Cacheable` en memoria para `product_type`/`store` si el profiling lo pide algún día — y puede que ni eso |
| Pact / contract testing bidireccional | El diff de OpenAPI en CI resuelve el problema real con un único consumidor |
| `@Aspect` genérico de auditoría (`@Auditable`) | El argumento del método es el DTO de entrada, no el estado anterior del recurso — no puede decir "de 5 a 3" sin pasar el "antes" por un `ThreadLocal`, y a eso ya no se le llama aspecto. Ver `11-observabilidad-y-logging.md` §6 |
| Hibernate Envers para auditoría | Versiona entidades completas, no acciones de negocio — no distingue "editó el stock" de "el checkout descontó stock". Ver ADR-0014 |
| `Dinero` como value object completo (con moneda) | Aplazado a v1.x, mientras solo exista EUR |
| Kubernetes | No hasta que exista una razón de despliegue real, no de curiosidad técnica |
