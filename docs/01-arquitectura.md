# Arquitectura: sistema legacy y sistema objetivo

> **Fuente de verdad de:** la forma del sistema — diagramas de contenedores, ER del legacy, y las secuencias de login y de checkout.
> **Léelo si:** quieres entender la forma del sistema de un vistazo, antes de entrar en detalle.
> **No decide:** el grafo de módulos (ADR-0017 / `14-modules.md`, se genera solo), ni el esquema objetivo columna a columna (`10-modelo-de-datos.md`).

## 1. Sistema legacy (referencia, no está en marcha)

Dos monolitos Spring Boot 3.2 / Java 17 independientes (sin código ni build compartido), cada uno con su propio Thymeleaf, atacando directamente la misma base Postgres. nginx solo enruta por prefijo de path, sin lógica. La autenticación es un atributo de `HttpSession` por app, sin Spring Security.

Este apartado está aquí porque `ukun-clientes`/`ukun-empleados` son la **especificación del comportamiento** que hay que conservar (`02-requisitos.md`) y el catálogo de lo que no hay que repetir (`03-analisis-y-patrones.md`), no porque haya que convivir con ellos: no corren, y no hay estrategia de coexistencia (`04-roadmap.md`).

```mermaid
flowchart TB
    browser["Navegador"]

    subgraph edge["nginx :80"]
        nginx["reverse proxy\n/ → clientes\n/empleados → empleados"]
    end

    subgraph clientes["ukun-clientes :8081 (Thymeleaf)"]
        cController["Controllers\nHome/Productos/Carrito/Pedido/Perfil/SignUp/Tienda"]
        cSession["HttpSession\nattr: cliente"]
        cService["Services"]
    end

    subgraph empleados["ukun-empleados :8082/empleados (Thymeleaf)"]
        eController["Controllers\nHome/Empleados/Productos/Stock"]
        eSession["HttpSession\nattr: empleado"]
        eService["Services"]
    end

    db[("Postgres\nun único esquema\ncuenta · cliente · empleado · producto\ntienda · stock_tienda · carrito · pedido...")]

    vol["/opt/resources\nbind mount\nimágenes de producto"]

    browser --> nginx
    nginx --> cController
    nginx --> eController
    cController --> cSession
    cController --> cService --> db
    eController --> eSession
    eController --> eService --> db
    cService -. sirve .-> vol
    eService -. escribe, no sirve .-> vol

    style db fill:#4a5568,color:#fff
    style vol fill:#744210,color:#fff
```

**Lo que este diagrama ya explica sobre los defectos encontrados:** no hay ninguna frontera entre `cService` y `eService` — ambos escriben `producto`/`stock_tienda`/`cuenta` con entidades JPA distintas y ya divergentes (ver [`03-analisis-y-patrones.md`](./03-analisis-y-patrones.md)). `ukun-empleados` escribe en el volumen de imágenes pero no lo sirve (`WebConfig` sin `addResourceHandlers`); depende de que `ukun-clientes` esté vivo para que las fotos de producto se vean.

### Modelo de datos actual (entidad-relación)

```mermaid
erDiagram
    CUENTA ||--o| CLIENTE : "MapsId"
    CUENTA ||--o| EMPLEADO : "MapsId"
    CLIENTE ||--o| DIRECCION : tiene
    CLIENTE ||--o| TARJETA : tiene
    CLIENTE }o--o| TIENDA : "tienda_fav"
    CLIENTE ||--o{ CARRITO : tiene
    CLIENTE ||--o{ PEDIDO : hace
    EMPLEADO }o--|| TIENDA : "trabaja en"
    TIENDA ||--|| DIRECCION : tiene
    TIENDA ||--o{ STOCK_TIENDA : ofrece
    PRODUCTO }o--|| TIPO_PRODUCTO : es
    PRODUCTO ||--o{ STOCK_TIENDA : "stock por tienda"
    PRODUCTO ||--o{ CARRITO : "línea de"
    PEDIDO ||--o{ PRODUCTO_PEDIDO : contiene
    PRODUCTO ||--o{ PRODUCTO_PEDIDO : "línea congelada"
    PEDIDO }o--|| DIRECCION : "de envío"
    PEDIDO }o--|| TARJETA : "de pago"

    CUENTA {
        int id PK
        varchar correo UK
        varchar contrasenna "texto plano, VARCHAR(16)"
        varchar nombre
        varchar apellido
    }
    TARJETA {
        int id PK
        varchar numero "PAN completo, texto plano"
        varchar cvv "prohibido guardarlo, PCI-DSS"
        date fecha_expiracion
    }
    STOCK_TIENDA {
        int producto_id PK, FK
        int tienda_id PK, FK
        int stock "nullable, sin @Version"
    }
    PRODUCTO {
        int id PK
        varchar nombre
        decimal precio "mapeado a Float en Java"
        int tipo FK
    }
```

`cuenta` es la tabla de identidad base; `cliente` y `empleado` heredan su PK vía `@MapsId` (table-per-type). Es exactamente el acoplamiento que el diseño nuevo no reproduce: con la identidad en otra base, `customer.id` es clave propia y la cuenta se referencia como `account_id UUID` sin FK — ver [`08-identidad-y-seguridad.md`](./08-identidad-y-seguridad.md) §1.

---

## 2. Sistema objetivo

Un monolito API de dominio, un servicio de autenticación separado, un gateway que actúa de BFF (Backend For Frontend) y un SPA en React. El navegador nunca ve un token JWT: solo una cookie de sesión HttpOnly hacia el gateway.

```mermaid
flowchart TB
    browser["Navegador\nReact SPA (react-bootstrap)"]

    subgraph gw["ukun-gateway (Spring Cloud Gateway)"]
        bff["BFF\noauth2Login + sesión HttpOnly\nTokenRelay Bearer JWT\nCSRF: cookie XSRF-TOKEN"]
    end

    subgraph auth["ukun-auth (Spring Authorization Server, en Spring Security 7, por capas — ADR-0022)"]
        authSrv["capas: domain · infra · application\nconfig · security · web\nAuthorization Code + PKCE\nemite JWT (JWKS)\nendpoints internos /internal/accounts"]
        authDb[("ukun_auth\naccount · account_role\noauth2_registered_client\noauth2_authorization")]
    end

    subgraph api["ukun-api (Spring Boot, resource server, modularizado con Spring Modulith — ADR-0017)"]
        apiSrv["REST /api/**\nmódulos: catalog · stores · inventory\ncustomers · staff · cart · orders\nidentity · storage · shared\nvalida JWT por JWKS\n@PreAuthorize por rol\nautorización de recurso (tienda propia, pedido propio)"]
        apiDb[("ukun_app\nproduct · store · store_stock\ncustomer · employee · cart_item\ncustomer_order · order_line")]
    end

    web["ukun-web\nestático, servido por el gateway en prod"]
    files["FileStoragePort\nadaptador: filesystem (v0.3) → S3/MinIO (v1.1)\nbind mount /opt/resources/uploads"]

    browser -- "cookie de sesión" --> bff
    bff -- "Bearer JWT" --> apiSrv
    bff <-- "code + PKCE, JWKS" --> authSrv
    apiSrv -- "cuenta creada/activada\n(saga de signup)" --> authSrv
    apiSrv --> apiDb
    authSrv --> authDb
    apiSrv --> files
    bff -. "enruta /media/**" .-> apiSrv
    bff -. sirve estático .-> web

    style authDb fill:#4a5568,color:#fff
    style apiDb fill:#4a5568,color:#fff
```

**Módulos de `ukun-api`:** el diagrama de arriba solo nombra los módulos; el grafo completo de dependencias entre ellos (`allowedDependencies`, incluido el ciclo `catalog`↔`inventory` y cómo se rompe) vive en ADR-0017 y `docs/14-modules.md`, y se regenera automáticamente en cada build de desarrollo vía `Documenter` de Spring Modulith — no se mantiene un segundo diagrama a mano aquí.

`ukun-auth` **no** está modularizado con Modulith: es un servicio anémico de quince clases con estructura por capas (`domain`/`infra`/`application`/`config`/`security`), y no hay grafo que dibujar. ADR-0022 explica por qué se retiró.

**Por qué BFF y no token en el SPA:** el token nunca entra en el alcance de un XSS del navegador, no hay que resolver refresh silencioso desde JavaScript, y al ser el gateway el único origen que ve el navegador, desaparece CORS. El coste es que el gateway pasa a tener estado (sesión) — asumible con un solo gateway en dev y con sesión en memoria; en producción con más de una réplica se resuelve con Redis, no antes.

**Fronteras de datos:** `ukun-auth` es deliberadamente anémico — solo `account` y `account_role`. `name`/`last_name`/`email` (proyección) viven en `ukun-api` junto al resto del perfil, para que editar el perfil sea una única transacción local salvo cuando cambia el email o la contraseña. El detalle completo de esta frontera está en [`08-identidad-y-seguridad.md`](./08-identidad-y-seguridad.md).

### Secuencia: login con BFF

```mermaid
sequenceDiagram
    actor U as Usuario
    participant W as ukun-web (SPA)
    participant GW as ukun-gateway
    participant AU as ukun-auth
    participant API as ukun-api

    U->>W: clic "Iniciar sesión"
    W->>GW: GET /login (redirige)
    GW->>AU: Authorization Code + PKCE
    AU-->>U: formulario de login
    U->>AU: correo + contraseña
    AU->>AU: valida hash (BCrypt), emite code
    AU-->>GW: redirect con code
    GW->>AU: POST /token (code + code_verifier)
    AU-->>GW: access_token (JWT) + refresh_token
    GW->>GW: crea sesión HttpOnly, guarda tokens server-side
    GW-->>W: Set-Cookie sesión + Set-Cookie XSRF-TOKEN
    W->>GW: GET /api/me (con cookie)
    GW->>API: GET /api/me (Authorization: Bearer <JWT>)
    API->>API: valida firma JWT contra JWKS de ukun-auth
    API-->>GW: 200 {roles, nombre, ...}
    GW-->>W: 200
```

### Secuencia: checkout con concurrencia controlada e intento emitido por el servidor

Resuelve la race condition real de `PedidoServiceImpl.reducirStockOnline` (lectura-modificación-escritura sin bloqueo, hoy puede dejar stock negativo con dos pedidos simultáneos del mismo producto). Incorpora ADR-0016: el identificador de idempotencia lo emite el servidor al abrir el checkout, no el navegador.

```mermaid
sequenceDiagram
    actor U as Cliente
    participant W as ukun-web
    participant API as ukun-api

    U->>W: abre la pantalla de checkout
    W->>API: POST /api/checkout/attempts
    API->>API: congela precios, calcula total,\nlee disponibilidad informativa por línea
    API-->>W: 201 { attemptId, lines[], total, expiresAt }
    W-->>U: pinta confirmación con el attemptId

    U->>W: "Confirmar pedido"
    W->>API: POST /api/orders  Idempotency-Key: {attemptId}
    API->>API: ¿existe ya esa Idempotency-Key para este cliente?
    alt clave ya COMPLETED, mismo request_hash
        API-->>W: 200 (respuesta original, sin repetir el efecto)
    else clave IN_PROGRESS (petición concurrente)
        API-->>W: 409 ProblemDetail code=IDEMPOTENCY_IN_PROGRESS
    else clave caducada (expiresAt pasado)
        API-->>W: 410 ProblemDetail code=CHECKOUT_EXPIRED
    else primera vez
        API->>API: SELECT ... FOR UPDATE sobre store_stock\nde las líneas del carrito, ORDER BY id
        API->>API: por cada línea: quantity - requested >= 0 ?
        alt stock insuficiente en alguna línea
            API-->>W: 409 ProblemDetail code=INSUFFICIENT_STOCK
        else stock suficiente en todas
            API->>API: crea Order (agregado): congela precios,\ncalcula total, descuenta stock, vacía carrito
            API-->>W: 201 Created {order}
        end
    end
```

El `ORDER BY id` antes del bloqueo (columna `id` propia de `store_stock` desde ADR-0015) es lo que evita interbloqueos cuando dos pedidos concurrentes comparten productos en distinto orden. Detalle completo del bloqueo en [`03-analisis-y-patrones.md`](./03-analisis-y-patrones.md#agregado-stock) y del intento de checkout en ADR-0016.
