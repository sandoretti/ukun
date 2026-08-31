# Requisitos

> **Fuente de verdad de:** qué debe hacer el sistema (RF-01…RF-13), la matriz de permisos y los requisitos no funcionales.
> **Léelo si:** vas a implementar un endpoint o una pantalla y necesitas saber qué comportamiento exacto se espera.
> **No decide:** cómo se implementa (skills, `03-analisis-y-patrones.md`), ni la forma del contrato REST (`docs/api/openapi/`).
> **Es el documento que sobrevive al legacy:** el código de `ukun-clientes`/`ukun-empleados` ya se ha borrado; estos requisitos no.

Extraídos leyendo los controllers, servicios y plantillas de `ukun-clientes` y `ukun-empleados` — no son aspiracionales, son lo que la aplicación legacy realmente hace. Sirven de base para el inventario de endpoints y el OpenAPI de v0.1. Donde el comportamiento actual es un bug y no una regla de negocio, se marca explícitamente para que **no** se traslade tal cual.

## 1. Requisitos funcionales — storefront (legacy `ukun-clientes`, borrado en v0.6)

### RF-01 Catálogo
- Listar productos con filtro opcional por nombre (`LIKE` case-insensitive), por tipo, y orden por precio (asc/desc).
- Ver ficha de un producto: nombre, precio, descripción, foto, tipo, SKU (identificador de negocio, ver ADR-0015).
- El catálogo público muestra **solo productos activos**: un producto descatalogado (RF-10) desaparece de aquí y de la búsqueda, aunque siga existiendo en el historial de pedidos. Verlos es cosa del listado de back-office de RF-10.
- Un producto se considera **disponible** en una tienda si y solo si `stock > 0`; un producto sin fila de stock en esa tienda se trata como no disponible (no como "desconocido").
- Mostrar disponibilidad en la tienda online y, si el cliente está autenticado y tiene tienda favorita, también en esa tienda.
- *Nota de contrato:* el listado de catálogo (`GET /api/products`) y la disponibilidad (`GET /api/availability`) son dos endpoints separados, no uno combinado — consecuencia de la descomposición en módulos `catalog`/`inventory` de Spring Modulith (ADR-0017 §3, ciclo de dependencias resuelto separando ambas lecturas). El frontend los pide en paralelo y compone en pantalla; el requisito funcional no cambia, solo la forma del contrato.

### RF-02 Tiendas
- Listado público de tiendas con dirección y teléfono.

### RF-03 Carrito (requiere sesión de cliente)
- Una línea de carrito por `(cliente, producto)` — no hay múltiples líneas del mismo producto.
- Añadir/editar cantidad de un producto (mismo endpoint hace ambas cosas).
- Eliminar una línea.
- Vaciar el carrito entero en una operación (`DELETE /api/cart`). No existe en el legacy: allí la única forma de vaciarlo es borrar las líneas una a una o completar un pedido. Es la operación que faltaba frente a las otras tres.
- El carrito se vacía automáticamente al completar un pedido.
- *No trasladar*: hoy no se valida contra stock disponible al añadir al carrito (se puede meter cantidad 999 de un producto con stock 3); la única validación es al confirmar el pedido. Esto es una carencia, no una regla — en el rediseño el carrito sí debe informar de disponibilidad.

### RF-04 Pedido / checkout
- El pedido se hace **solo contra el stock de la tienda "Ukun Online"**, nunca contra la tienda favorita del cliente ni ninguna otra. Esta es una regla de negocio real, no un descuido — documentarla así evita "arreglarla" sin querer.
- El precio de cada línea se congela en el momento del pedido (cambios posteriores de precio del catálogo no afectan a pedidos ya hechos).
- El pedido copia la dirección y la tarjeta **actuales** del cliente en el momento de confirmar.
- Si no hay stock suficiente de alguna línea, el pedido completo se rechaza (no hay pedidos parciales ni backorder).
- *No trasladar*: hoy un carrito vacío puede generar un pedido con total 0 y sin líneas — es un bug, no un requisito; el pedido debe rechazar carritos vacíos.
- Historial de pedidos del cliente, con sus líneas.
- Consultar **un** pedido concreto por su identificador, con sus líneas y sus instantáneas de envío y pago. La pantalla de confirmación posterior al checkout es su consumidor natural, y es a donde apunta la cabecera `Location` del pedido recién creado. La pertenencia se resuelve server-side desde `account_id`: el identificador de la ruta identifica el pedido, no autoriza a verlo.

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

## 2. Requisitos funcionales — back-office (el legacy `ukun-empleados`, borrado en v0.7)

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
- CRUD de productos: SKU, nombre, descripción, tipo, precio, foto. El catálogo de productos es **global**, no por tienda (a diferencia del stock).
- **Listado de back-office**, distinto del público de RF-01: ve los productos **activos y los descatalogados** (filtro `ACTIVE`/`DISCONTINUED`/`ALL`), porque un producto descatalogado hay que poder encontrarlo para reactivarlo. El listado público solo devuelve activos.
- **El SKU se fija en el alta y no se edita después** (ADR-0015). Es el identificador de negocio con el que el empleado busca stock y el que va impreso en la etiqueta física: reescribirlo cambia en silencio a qué producto apunta una etiqueta ya existente. Cambiar de SKU es dar de alta otro producto.
- **La foto es opcional y se gestiona como una operación aparte del alta y de la edición**, no como un campo más del formulario. Al subir una nueva se reemplaza la anterior y se borra del almacenamiento, y las dos cosas son **una sola unidad con compensación**: si falla el guardado, la foto vieja no puede haber desaparecido ya (hoy sí desaparece — `03-analisis-y-patrones.md` §3). Se valida por el contenido real del fichero, no por su extensión, y el nombre lo genera el servidor (`08-identidad-y-seguridad.md` §9).
- **Descatalogar y borrar son dos cosas distintas** (ADR-0013):
  - *Borrar* es físico y solo es posible si el producto **nunca** se ha referenciado. Si tiene referencias se rechaza **diciendo dónde están** — en cuántos pedidos, en cuántas tiendas con stock y en cuántos carritos —, no con un error genérico.
  - *Descatalogar* es la salida operativa real para el caso común (un producto ya vendido, que por integridad no se podrá borrar nunca): desaparece del catálogo público (RF-01) y de las pantallas de stock (RF-09) sin tocar el historial de pedidos. Es reversible.
- Renombrar o cambiar el precio de un producto **no altera ningún pedido pasado**: la línea de pedido guarda su propia instantánea de nombre y precio (ADR-0011).
- *Concurrencia*: la edición de producto es «último que escribe gana», decidido explícitamente y no por omisión — ver `10-modelo-de-datos.md` §2. Es la excepción justificada al requisito no funcional de concurrencia de §4, no un olvido.

### RF-13 Gestión de tipos de producto (solo `admin`)

> *La numeración de estos requisitos es por orden de creación, no por orden de lectura: RF-13 va aquí porque es el hermano de RF-10, no al final del documento.*

**Este requisito no existe en el legacy, y esa es exactamente la razón de que exista aquí.** `tipo_producto` es una tabla sembrada una vez por `database/init.sql` con seis filas, `producto.tipo` es `NOT NULL`, y ninguna de las dos aplicaciones ofrece manera de crear una categoría: dar de alta "Estanterías" hoy exige un `INSERT` a mano contra la base de producción. No es paridad funcional lo que se persigue aquí, es cerrar un hueco real.

- CRUD completo de tipos: listar, ver, crear, renombrar y borrar.
- El **nombre es único** y obligatorio. Intentar crear o renombrar a un nombre existente se rechaza indicando el campo, no con un error genérico.
- **No se puede borrar un tipo que tenga productos**, ni siquiera si todos están descatalogados — se informa de **cuántos** hay, igual que RF-10 informa de dónde está referenciado un producto. La salida es reasignar esos productos a otro tipo y volver a intentarlo. No hay descatalogación lógica de tipos: un tipo sin productos es simplemente borrable, y uno con productos es un dato en uso (ADR-0013).
- **Renombrar un tipo no afecta a ningún pedido histórico.** La instantánea que congela la línea de pedido es el *nombre del producto*, no su tipo (ADR-0011, `10-modelo-de-datos.md` §5), así que un tipo es dato de catálogo vivo y puede renombrarse sin consecuencias sobre el historial. Sí afecta, inmediatamente, al filtro por tipo del catálogo público (RF-01).
- El listado público de tipos (el que alimenta el filtro de RF-01) y el de back-office son la misma información con distinta forma: el de back-office añade cuántos productos cuelgan de cada tipo, que es justo el dato que decide si el borrado va a poder hacerse.

### RF-11 Gestión de empleados (solo `admin`, acotado a su propia tienda)
- Listar empleados de la tienda del admin.
- Dar de alta un empleado: nombre, apellido, correo, contraseña, puesto (`empleo`), y si es admin. La tienda del nuevo empleado es siempre la del admin que lo crea.
- *Decidido: editar y eliminar empleado quedan **fuera de alcance de v1.0**.* Hoy no existen en la UI del legacy aunque el servicio los soporte, y el contrato se congela en SINC-1, así que la decisión se toma ahora en vez de en v0.7. El motivo no es de esfuerzo de pantalla: con la identidad en un servicio aparte (ADR-0004), *borrar* un empleado significa desactivar su cuenta en `ukun-auth` y borrar su perfil en `ukun-api` — una saga inversa a la del alta, con su propia compensación, que no cabe en un `DELETE` de una tabla. Entra en v1.1+ con su propio diseño, o no entra. Mientras tanto, la salida operativa es la misma que para cualquier cuenta: deshabilitarla en `ukun-auth`.

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
| Descatalogar/reactivar un producto | ❌ | ❌ | ❌ | ✅ |
| Subir o borrar la foto de un producto | ❌ | ❌ | ❌ | ✅ |
| CRUD de tipos de producto | ❌ | ❌ | ❌ | ✅ |
| Listar/crear empleados de su tienda | ❌ | ❌ | ❌ | ✅ |

Las cuatro últimas filas —toda la columna que solo tiene ✅ en `admin`— cuelgan de `/api/admin/**` en el contrato REST, y son exactamente esa superficie: ni una ruta admin fuera del prefijo, ni una ruta bajo el prefijo que no sea admin (**ADR-0020**). La gestión de stock de RF-09 **no** está ahí: es de cualquier empleado, no de admin.

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
