# Requisitos

> **Fuente de verdad de:** qué debe hacer el sistema (RF-01…RF-12), la matriz de permisos y los requisitos no funcionales.
> **Léelo si:** vas a implementar un endpoint o una pantalla y necesitas saber qué comportamiento exacto se espera.
> **No decide:** cómo se implementa (skills, `03-analisis-y-patrones.md`), ni la forma del contrato REST (`docs/api/openapi/`).
> **Es el documento que sobrevive al legacy:** el código de `ukun-clientes`/`ukun-empleados` se borra; estos requisitos no.

Extraídos leyendo los controllers, servicios y plantillas de `ukun-clientes` y `ukun-empleados` — no son aspiracionales, son lo que la aplicación legacy realmente hace. Sirven de base para el inventario de endpoints y el OpenAPI de v0.1. Donde el comportamiento actual es un bug y no una regla de negocio, se marca explícitamente para que **no** se traslade tal cual.

## 1. Requisitos funcionales — storefront (hoy `ukun-clientes`)

### RF-01 Catálogo
- Listar productos con filtro opcional por nombre (`LIKE` case-insensitive), por tipo, y orden por precio (asc/desc).
- Ver ficha de un producto: nombre, precio, descripción, foto, tipo, SKU (identificador de negocio, ver ADR-0015).
- Un producto se considera **disponible** en una tienda si y solo si `stock > 0`; un producto sin fila de stock en esa tienda se trata como no disponible (no como "desconocido").
- Mostrar disponibilidad en la tienda online y, si el cliente está autenticado y tiene tienda favorita, también en esa tienda.
- *Nota de contrato:* el listado de catálogo (`GET /api/products`) y la disponibilidad (`GET /api/availability`) son dos endpoints separados, no uno combinado — consecuencia de la descomposición en módulos `catalog`/`inventory` de Spring Modulith (ADR-0017 §3, ciclo de dependencias resuelto separando ambas lecturas). El frontend los pide en paralelo y compone en pantalla; el requisito funcional no cambia, solo la forma del contrato.

### RF-02 Tiendas
- Listado público de tiendas con dirección y teléfono.

### RF-03 Carrito (requiere sesión de cliente)
- Una línea de carrito por `(cliente, producto)` — no hay múltiples líneas del mismo producto.
- Añadir/editar cantidad de un producto (mismo endpoint hace ambas cosas).
- Eliminar una línea.
- El carrito se vacía automáticamente al completar un pedido.
- *No trasladar*: hoy no se valida contra stock disponible al añadir al carrito (se puede meter cantidad 999 de un producto con stock 3); la única validación es al confirmar el pedido. Esto es una carencia, no una regla — en el rediseño el carrito sí debe informar de disponibilidad.

### RF-04 Pedido / checkout
- El pedido se hace **solo contra el stock de la tienda "Ukun Online"**, nunca contra la tienda favorita del cliente ni ninguna otra. Esta es una regla de negocio real, no un descuido — documentarla así evita "arreglarla" sin querer.
- El precio de cada línea se congela en el momento del pedido (cambios posteriores de precio del catálogo no afectan a pedidos ya hechos).
- El pedido copia la dirección y la tarjeta **actuales** del cliente en el momento de confirmar.
- Si no hay stock suficiente de alguna línea, el pedido completo se rechaza (no hay pedidos parciales ni backorder).
- *No trasladar*: hoy un carrito vacío puede generar un pedido con total 0 y sin líneas — es un bug, no un requisito; el pedido debe rechazar carritos vacíos.
- Historial de pedidos del cliente, con sus líneas.

### RF-05 Perfil de cliente
- Ver perfil: datos de cuenta, dirección, tarjeta (enmascarada en la UI), tienda favorita, idioma preferido.
- Editar perfil: nombre, apellido, dirección, tarjeta, tienda favorita. Si no se modifica ningún campo, se informa sin persistir.
- Cambiar la tienda favorita.
- Cambiar el idioma preferido (`es`/`en`), persistido en el perfil y usado como fuente de verdad en cualquier dispositivo donde el cliente inicie sesión — ver ADR-0019.

### RF-06 Alta de cliente (signup)
- Formulario único: cuenta (correo, contraseña, nombre, apellido) + dirección + tarjeta + tienda favorita opcional.
- El correo es único en todo el sistema (compartido con empleados) y se normaliza a minúsculas.
- *No trasladar*: hoy la validación de correo duplicado no excluye al propio usuario al editar, así que editar el perfil sin cambiar el correo puede fallar por "correo ya existe" — bug a corregir, no a replicar.

### RF-07 Autenticación de cliente
- Login por correo + contraseña. Logout.

## 2. Requisitos funcionales — back-office (hoy `ukun-empleados`)

### RF-08 Autenticación de empleado
- Login por correo + contraseña. Logout.
- Todo el back-office requiere sesión de empleado excepto `/login`.

### RF-09 Gestión de stock (cualquier empleado autenticado, **acotado a su propia tienda**)
- Ver el stock de la tienda del empleado.
- Ver catálogo de productos que aún no tienen fila de stock en esa tienda ("alta de nuevo stock").
- Buscar y dar de alta por **SKU** (ADR-0015), no por id numérico interno — cada línea de stock tiene identidad propia y URL directa (`PUT /api/stores/{storeId}/stock/{lineId}`).
- Editar (upsert) el stock de un producto en su tienda: si no existía la fila, se crea; si existía, se actualiza. Debe validarse `stock >= 0`.
- Eliminar la fila de stock de un producto en su tienda (no es lo mismo que poner stock a 0: borra el registro).
- Un empleado **nunca** puede operar sobre el stock de otra tienda, aunque intente forzar el parámetro — hoy esto se comprueba a mano comparando IDs; en el rediseño la tienda se infiere del token, no de un parámetro de la petición, y la línea de stock direccionada por su propio id hace estructuralmente más difícil el intento.

### RF-10 Gestión de productos (solo `admin`)
- CRUD de productos: nombre, descripción, tipo, precio, foto.
- La foto es opcional; al editar, si se sube una nueva, se reemplaza y se borra la anterior.
- Al eliminar un producto que tiene referencias (p. ej. líneas de pedido o stock), debe rechazarse con un mensaje claro, no con un error genérico.
- El catálogo de productos es **global**, no por tienda (a diferencia del stock).

### RF-11 Gestión de empleados (solo `admin`, acotado a su propia tienda)
- Listar empleados de la tienda del admin.
- Dar de alta un empleado: nombre, apellido, correo, contraseña, puesto (`empleo`), y si es admin. La tienda del nuevo empleado es siempre la del admin que lo crea.
- *Gap conocido, no un requisito descartado*: hoy no existe editar ni eliminar empleado en la UI aunque el servicio lo soporta — decidir explícitamente en v0.7 si se implementa o se documenta como fuera de alcance.

### RF-12 Roles
- `admin` es un flag booleano sobre `empleado`, no un rol jerárquico con más niveles. Dos roles de negocio en total: **cliente** y **empleado** (con sub-flag `admin`).

## 3. Matriz de permisos

| Acción | Anónimo | Cliente | Empleado | Empleado admin |
|---|:-:|:-:|:-:|:-:|
| Ver catálogo, ficha de producto, tiendas | ✅ | ✅ | ✅ | ✅ |
| Signup, login cliente | ✅ | — | — | — |
| Carrito, checkout, historial de pedidos, perfil propio | ❌ | ✅ | ❌ | ❌ |
| Login empleado | ✅ | — | — | — |
| Ver/editar stock de **su** tienda | ❌ | ❌ | ✅ | ✅ |
| Ver/editar stock de otra tienda | ❌ | ❌ | ❌ | ❌ |
| CRUD de productos (catálogo global) | ❌ | ❌ | ❌ | ✅ |
| Listar/crear empleados de su tienda | ❌ | ❌ | ❌ | ✅ |

**Decisión abierta (ver `adr/0006-modelo-de-roles.md`):** hoy el esquema permite que una misma persona sea cliente y empleado a la vez (ambos cuelgan de `cuenta`), pero ninguna app lo contempla en la práctica. Se prohíbe explícitamente en el nuevo diseño salvo que aparezca un requisito de negocio real que lo pida.

## 4. Requisitos no funcionales

### Correctitud e integridad
- El dinero se representa con `BigDecimal`/tipo decimal exacto en toda la cadena — nunca `Float`/`Double` (hoy `precio`, `total`, `precio_unitario`, `precio_total` son `Float` sobre columnas `DECIMAL(10,2)`, con pérdida de precisión real).
- Las escrituras concurrentes sobre el mismo recurso deben tener una estrategia explícita de concurrencia (bloqueo pesimista para el descuento de stock en checkout, optimista con `@Version` para la edición manual de stock por un empleado). Ver `03-analisis-y-patrones.md`.
- Cualquier operación que muta estado usa el verbo HTTP correcto (`POST`/`PUT`/`DELETE`), nunca `GET`.

### Seguridad
- Contraseñas con hash (BCrypt/Argon2), nunca en claro ni con límite de longitud artificial.
- Ningún dato sensible (contraseña, número de tarjeta, CVV) se loguea ni se serializa en una respuesta.
- No se almacena CVV bajo ninguna circunstancia (incumplimiento PCI-DSS aunque esté cifrado). Detalle en `08-identidad-y-seguridad.md`.
- CSRF mitigado por diseño (mutaciones nunca en `GET`, cookie `XSRF-TOKEN` validada en el gateway).
- Sin redirecciones abiertas basadas en cabeceras controladas por el cliente (hoy `AbsController.pathOrigenRedirect` usa `Referer` sin validar).

### Rendimiento
- Sin N+1: toda colección que se serializa completa (catálogo, pedidos con líneas, stock) usa `@EntityGraph`/fetch join o batch fetching explícito.
- Listados paginados (`Page<T>`), no `findAll()` sin límite.

### Desarrollo y operación
- El entorno local se levanta con un solo comando (`tilt up`) y refleja cambios de código en segundos, no minutos.
- El esquema de cada base con BD propia está versionado con Liquibase; ningún cambio de esquema se hace editando SQL a mano contra una base viva.
- Todo endpoint público está documentado en el OpenAPI antes de implementarse (contrato como artefacto, no como documentación a posteriori).

### Observabilidad (desde v1.0)
- Cada servicio expone `/actuator/health` y métricas básicas.
- Los logs de negocio no contienen PII innecesaria ni secretos (corrige el caso real de `HomeController` de empleados logueando la contraseña en cada intento fallido de login).
