# ADR-0020: Toda la superficie solo-`admin` cuelga de `/api/admin/**`

## Estado
Aceptado

## Contexto
`08-identidad-y-seguridad.md` §4 reparte la autorización en tres capas y pone como ejemplo del filtro grueso del gateway *«`/api/admin/**` exige `ROLE_ADMIN`»*; la skill `spring-gateway` repite esa regla como una de las cinco que definen el BFF. Pero al escribir el contrato (v0.1, issue #3) **ninguna ruta empezaba por `/api/admin`**: el único endpoint solo-admin escrito era `/api/employees`. La regla del gateway estaba, por tanto, muerta — un patrón que no casa con nada, que no protege nada, y que el día que alguien añada una ruta admin fuera del prefijo seguirá sin protegerla y sin fallar.

El disparador para decidirlo ahora es RF-10 (CRUD de productos) y RF-13 (CRUD de tipos de producto), que faltaban por completo en el contrato. Son once operaciones nuevas, todas solo-admin, y traen además un problema de forma que hay que resolver en el mismo movimiento: el listado público de catálogo devuelve **solo productos activos** y no expone `is_active`, mientras que el listado de back-office necesita ver también los descatalogados para poder reactivarlos (ADR-0013). Son dos lecturas con distinta audiencia, distinta cacheabilidad y distinta forma de respuesta.

## Decisión
**Toda operación que exija rol `ADMIN`, y solo ella, vive bajo `/api/admin/**`.** El prefijo es una bicondición, no una convención de nombres: ni una ruta admin fuera, ni una ruta no-admin dentro.

```
público / cliente / empleado          admin
────────────────────────────          ─────────────────────────────────────
GET  /api/products                    GET    /api/admin/products
GET  /api/products/{id}               POST   /api/admin/products
GET  /api/products/suggest            GET    /api/admin/products/{id}
GET  /api/products/by-sku/{sku}       PUT    /api/admin/products/{id}
GET  /api/product-types               DELETE /api/admin/products/{id}
GET  /api/stores                      POST   /api/admin/products/{id}:discontinue
GET  /api/availability                POST   /api/admin/products/{id}:reactivate
GET  /api/cart                        PUT    /api/admin/products/{id}/image
GET  /api/orders                      DELETE /api/admin/products/{id}/image
GET  /api/me/profile                  GET    /api/admin/product-types
GET  /api/stores/{id}/stock  (RF-09)  POST   /api/admin/product-types
PUT  /api/stores/{id}/stock/{lineId}  GET    /api/admin/product-types/{id}
                                      PUT    /api/admin/product-types/{id}
                                      DELETE /api/admin/product-types/{id}
                                      GET    /api/admin/employees
                                      POST   /api/admin/employees
```

Dos consecuencias inmediatas de aplicar la regla:

- **`/api/employees` se mueve a `/api/admin/employees`** (RF-11 es solo-admin). Se decide antes de SINC-1, así que no rompe a ningún consumidor: no hay implementación ni tipos generados todavía.
- **RF-09 (stock) se queda fuera del prefijo**, aunque sea back-office. No es solo-admin: cualquier empleado gestiona el stock de su tienda. Lo que decide el prefijo es el rol exigido, no si la pantalla es de cara al público.

### La autorización de verdad sigue estando en `ukun-api`

El prefijo **no** es un mecanismo de seguridad. `ukun-api` valida el JWT como resource server y anota cada operación con `@PreAuthorize("hasRole('ADMIN')")` exactamente igual que si la ruta no llevara prefijo; el filtro del gateway es defensa en profundidad y nunca la única barrera (`08-identidad-y-seguridad.md` §4). Lo que el prefijo aporta es que esa defensa en profundidad **se pueda expresar en una sola regla** en vez de en una lista de rutas y métodos que hay que mantener sincronizada a mano y que envejece con cada endpoint nuevo.

### Por qué dos URLs para el mismo producto no es duplicación

`GET /api/products/{id}` y `GET /api/admin/products/{id}` devuelven **schemas distintos**, no el mismo recurso dos veces:

| | público | admin |
|---|---|---|
| Qué productos | solo activos | activos y descatalogados, filtrables |
| Campos | `id`, `sku`, `name`, `description`, `price`, `typeId`, `typeName`, `imageUrl` | los mismos **más** `isActive`, `createdAt`, `updatedAt` |
| Cacheabilidad | alta, TTL largo, sin token | ninguna |
| Un producto descatalogado | `404` | `200` |

Exponer `isActive` y las marcas de tiempo en la respuesta pública sería filtrar estado de gestión a un consumidor anónimo, y añadir un parámetro para verlos convertiría un endpoint público en uno cuya seguridad depende del **valor** de un parámetro.

## Consecuencias
- La regla `/api/admin/** → ROLE_ADMIN` del gateway pasa a proteger algo real, y cualquier endpoint admin futuro queda cubierto por ella el día que se escribe, sin tocar la configuración del gateway.
- El SPA puede enrutar el back-office por prefijo (`/admin/*` en el router, `/api/admin/*` en el cliente HTTP) y decidir con `GET /api/me` si pinta la entrada al back-office, sin una lista de rutas privilegiadas en el frontend.
- Auditar «¿qué puede hacer un admin y nadie más?» es leer una sección del OpenAPI, no cruzar cada operación con su bloque `security` y su descripción.
- Coste: dos URLs y dos schemas para producto y para tipo de producto. Es lo que ya ocurre con `/api/products` y `/api/availability` desde ADR-0017 §3 — el contrato no está obligado a tener una ruta por tabla.
- `/api/employees` cambia de sitio antes de existir. Hay que actualizar el issue #45 y `04-roadmap.md`, no código.
- El `Location` de `POST /api/admin/products` apunta a `/api/admin/products/{id}`, no a la ficha pública: la respuesta a una creación es el recurso que acaba de crear quien la creó, en su propia vista.

## Alternativas descartadas
- **Mismo recurso, rol por operación** (`POST`/`PUT`/`DELETE /api/products` con `@PreAuthorize`, y un parámetro `status=ACTIVE|DISCONTINUED|ALL` en el listado público cuyo valor no-por-defecto exigiría token). Es la opción más ortodoxa en REST y la que menos URLs genera, pero: el listado tendría que declararse con seguridad opcional (`security: [{}, {bearerAuth: []}]`) y su respuesta cambiaría de forma según quién pregunte; el filtro del gateway habría que reescribirlo como una lista de pares método+ruta que envejece con cada endpoint; y `@PreAuthorize` pasaría a depender del valor de un parámetro de consulta, que es exactamente el tipo de autorización que `08-identidad-y-seguridad.md` §4 saca del alcance del cliente.
- **Un servicio o módulo de back-office aparte**: ADR-0017 §4 ya descartó trocear `ukun-api` por audiencia; el CRUD de productos es del módulo `catalog`, el de empleados de `staff`, y eso no cambia porque la ruta lleve prefijo. El prefijo es de la capa web, no de la descomposición en módulos.
- **Prefijo por audiencia completa** (`/api/public/**`, `/api/customer/**`, `/api/staff/**`, `/api/admin/**`): coherente, pero reescribe todo el contrato para resolver un problema que solo tenía la superficie admin, y obliga a decidir a qué prefijo pertenece un endpoint que sirve a dos audiencias (`GET /api/products` lo consumen anónimos, clientes y empleados).
