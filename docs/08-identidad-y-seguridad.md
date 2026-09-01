# Identidad y seguridad

> **Fuente de verdad de:** la frontera de datos entre `ukun-api` y `ukun-auth`, la saga de signup, los claims del token, el reparto de autorización en tres capas y el endurecimiento de auth/gateway/subidas.
> **Léelo si:** vas a tocar login, roles, permisos, signup, o cualquier dato sensible.
> **No decide:** los tipos y restricciones del esquema (`10-modelo-de-datos.md`), ni los `code`s de error (`12-error-contract.md`), ni qué se loguea (`11-observabilidad-y-logging.md`).
> **Idioma:** identificadores, rutas y roles en inglés (ADR-0018).

## 1. Por qué `customer.id` no deriva de la cuenta

El legacy hace `cliente.id = cuenta.id` vía `@MapsId` (table-per-type). Ese mapeo es insostenible en cuanto la identidad vive en otra base: `customer`/`employee` no pueden tener una FK física a una tabla de `ukun_auth`.

**Regla de oro:** `customer.id` y `employee.id` son claves propias del dominio en `ukun_app`, independientes de la identidad. La cuenta se referencia como un valor opaco: `customer.account_id UUID`, **sin foreign key** (cruza base de datos, no se puede expresar como FK real). Su índice único (`ux_customer_account_id`, `10-modelo-de-datos.md` §4) es a la vez la consulta más caliente del sistema y la forma de hacer cumplir ADR-0006 en la base.

Como los datos actuales son desechables (ADR-0007) y el legacy no está en marcha, no hay migración de identidad: `ukun_auth` se siembra con las cuentas de demostración (contraseña hasheada, no las `'123456'` en claro del legacy) y `ukun_app` con `customer`/`employee` ya llevando `account_id`. El diseño de abajo rige el funcionamiento **en marcha**, no un procedimiento de corte de una sola vez.

## 2. Qué vive en cada servicio

`ukun-auth` es deliberadamente anémico:
```
account(id UUID PK, email CITEXT UNIQUE, password_hash, enabled, created_at, updated_at)
account_role(account_id, role)   -- CUSTOMER | EMPLOYEE | ADMIN
```

**`name` y `last_name` NO viven en auth — se quedan en `ukun-api`** (ADR-0004). Son datos de *perfil*, no credenciales. Si vivieran en auth, cada edición de perfil implicaría doble escritura entre dos bases (el formulario de perfil edita nombre + apellido + correo + dirección + tarjeta + tienda favorita a la vez). Dejándolos en el API, `PUT /api/me/profile` sigue siendo **una única transacción local** para todo salvo el correo y la contraseña.

El correo sí vive en `ukun-auth` (es la credencial de login y la restricción de unicidad). En `ukun-api` se guarda una **proyección de lectura** (`customer.email`/`employee.email`) con esta política de refresco, deliberadamente simple:

> En cada petición autenticada, un filtro compara el claim `email` del JWT con la copia local del usuario en curso; si difieren, la actualiza. Un cambio de correo se propaga a la copia local en el siguiente login del propio usuario.

Consecuencia asumida: un listado de empleados de una tienda (~5 personas) puede mostrar un correo desactualizado hasta que ese empleado vuelva a entrar. Aceptable para ese volumen; la alternativa (`GET /internal/accounts?ids=...` síncrono desde el API) queda documentada como *escape hatch* en `AccountsPort` si algún día hace falta.

## 3. La saga de signup

El alta cruza las dos bases. **No se introduce 2PC ni un motor de sagas** — es una saga de dos pasos con reserva + activación, orquestada por `ukun-api`:

```
POST /api/signup   (público, sin token)
  body: { email, password, name, lastName, address{...}, card{...}, favoriteStoreId }

ukun-api:
  0. valida formato + reglas locales (dirección, tarjeta, tienda existe)
     → 400 VALIDATION_FAILED. No valida unicidad de email (no la sabe).
  1. registrationId = UUID.randomUUID()                         [idempotencia]
  2. POST ukun-auth /internal/accounts   (client_credentials, scope accounts:write)
       Idempotency-Key: {registrationId}
       body: { email, password, roles: ["CUSTOMER"], enabled: false }   ← reserva, deshabilitada
     → 201 {accountId} | 409 EMAIL_ALREADY_EXISTS → se devuelve 409 al usuario, fin, sin basura
  3. TRANSACCIÓN LOCAL ÚNICA en ukun_app:
       insert address, insert card, insert customer(account_id, name, last_name, email)
     → si falla: POST .../accounts/{id}:release (best-effort) y 500
  4. POST ukun-auth /internal/accounts/{accountId}:activate   ← punto de commit real
     → si falla: reintento con backoff (3x); si sigue fallando, 202 y el mismo
       signup (mismo email + mismo Idempotency-Key) reactiva la cuenta al reintentar
  5. 201 Created
```

Por qué es correcto sin transacciones distribuidas:
- **El orden importa**: la cuenta se crea *antes* del insert local porque la unicidad de email es la única regla que no se puede validar localmente. Fallar ahí evita un cliente huérfano.
- **La cuenta nace `enabled=false`**: una cuenta deshabilitada sin cliente asociado es basura inerte — no permite login. Reintentar el mismo email con el mismo `Idempotency-Key` la reutiliza; con otro key, `409` (no se "roba" una cuenta reservada por otro intento).
- **Idempotencia real en `ukun-auth`**: `registration_request(idempotency_key PK, account_id, created_at)`. El reintento devuelve el mismo `accountId` con `200`.
- **Reconciliación**: un job diario en `ukun-auth` borra cuentas `enabled=false` con más de 24h. El sistema es correcto aunque la compensación del paso 3 falle — no depende de ella.

Esto sustituye a los `ClienteValidator`/`EmpleadoValidator` del legacy (que hacen `existsByCuenta_Correo` con su propia race condition entre el `exists` y el `save`): la unicidad pasa a ser el `409 EMAIL_ALREADY_EXISTS` de `ukun-auth`, mapeado por el API a un `ProblemDetail` con `errors: [{field: "email", code: "EMAIL_ALREADY_EXISTS"}]` que el frontend pinta bajo el campo.

El alta de empleado (`POST /api/admin/employees`, solo admin — ADR-0020) reutiliza exactamente la misma saga (`AccountRegistrationService`), cambiando `roles: ["EMPLOYEE"]` (+`"ADMIN"` si aplica) y tomando `storeId` del admin que la ejecuta, nunca del cuerpo de la petición.

## 4. Autenticación en el navegador: BFF en el gateway

Flujo Authorization Code + PKCE, pero el SPA **nunca ve el token**:

```
navegador ──cookie de sesión HttpOnly/SameSite=Lax──> ukun-gateway
ukun-gateway (oauth2Login + TokenRelay) ──Bearer JWT──> ukun-api
ukun-gateway <──code + PKCE, refresh rotation──> ukun-auth
```

Por qué BFF y no token en el SPA: elimina el token del alcance de cualquier XSS, elimina el refresh silencioso desde JavaScript, y como el navegador solo habla con el gateway, front y API quedan de facto en el mismo origen (sin CORS, sin preflights). Coste asumido: el gateway tiene estado (sesión). Desde v1.0 ese estado vive en Postgres, en la base propia `ukun_gateway`, y no en el heap — así sobrevive a un despliegue y deja de fijar el número de réplicas (ADR-0024, issue #118). No es Redis, que es lo que decía esta línea: el motivo del cambio es el presupuesto de despliegue de ADR-0023. Ver ADR-0003 y ADR-0024.

### Claims del access token

```json
{
  "iss": "https://auth.ukun.local",
  "sub": "8f2c…-uuid-de-account",
  "aud": ["ukun-api"],
  "email": "customer@ukun.dev",
  "roles": ["CUSTOMER"],
  "scope": "openid profile ukun.read ukun.write",
  "exp": "...", "iat": "...", "jti": "..."
}
```

**Lo que deliberadamente NO va en el token: `storeId`.** Ahorraría un lookup en cada operación de stock, pero: (a) obligaría a `ukun-auth` a conocer el concepto de tienda, rompiendo la frontera trazada arriba; (b) un traslado de empleado a otra tienda no surtiría efecto hasta que expire el token; (c) es un dato de autorización de negocio, y el negocio vive en `ukun-api`. El API resuelve `employee` por `account_id` (índice único, una query, cacheable con TTL corto si algún día hace falta).

### Reparto de autorización en tres capas

| Capa | Responsabilidad | Ejemplo |
|---|---|---|
| Gateway | Autenticación + filtro grueso de rutas. Defensa en profundidad, no la única barrera | `/api/admin/**` exige `ROLE_ADMIN`; `/api/me/**` exige sesión; `/api/products` y `/media/**` públicos (este último lo **enruta** hacia `ukun-api`, que es quien lo sirve — §9) |
| `ukun-api` | **Autoridad final.** Valida el JWT él mismo como resource server (JWKS de `ukun-auth`), nunca confía en cabeceras inyectadas por el gateway | `@PreAuthorize("hasRole('ADMIN')")` en el CRUD de productos |

Que la primera fila sea una sola regla y no una lista de rutas es lo que decide **ADR-0020**: toda operación solo-`admin` —y solo ella— cuelga de `/api/admin/**` (CRUD de productos RF-10, de tipos RF-13, y gestión de empleados RF-11). La gestión de stock de RF-09 se queda fuera del prefijo a propósito: es de cualquier empleado, no de admin, y su autorización es de recurso (la tienda), que es justo lo que la tercera capa resuelve y ninguna regla de ruta puede.
| Dominio | Autorización *a nivel de recurso*, imposible de expresar en una anotación | "esta línea de `store_stock` pertenece a mi tienda"; "este pedido es mío" |

La tercera capa es la que el legacy tiene mal repartida: `StockController` compara IDs de tienda a mano sobre un `tiendaId` que llega como parámetro de la petición. En el nuevo diseño la ruta **sí** lleva la tienda —`PUT /api/stores/{storeId}/stock/{lineId}` (RF-09, ADR-0015, ADR-0018)— y lo que cambia es quién decide si vale:

> El `storeId` de la ruta identifica el recurso; **no autoriza nada**. El servicio resuelve la tienda del empleado a partir de `account_id` y compara: si no coinciden, `403 FORBIDDEN` con evento `auth.authorization.denied` (`reason=FOREIGN_RESOURCE`). Un `storeId` forzado a mano no es un agujero, es un 403 registrado.

Se prefiere esta forma a la alternativa de omitir la tienda de la ruta (`PUT /api/stock/{productId}`, con la tienda siempre inferida): la línea de stock tiene identidad propia desde ADR-0015 y merece una URL estable y direccionable, y una ruta que oculta a qué recurso apunta es más difícil de auditar, no más segura.

## 5. El problema PCI-DSS de la tabla de tarjeta

El legacy almacena el número completo (PAN) **y el CVV** en claro. Almacenar el CVV está prohibido por PCI-DSS bajo cualquier circunstancia, incluso cifrado — no es un matiz de seguridad, es una regla sin excepciones del estándar.

Plan en dos pasos:
- **v0.6**: `card` nace sin CVV y sin PAN. Solo se persiste `last4`, marca y fecha de expiración (`10-modelo-de-datos.md` §2); el CVV se pide en cada compra y nunca se escribe a disco.
- **v1.x**: integración con una pasarela real (Stripe/Redsys u otra) en modo test, guardando únicamente el token que la pasarela devuelve — `ukun-api` deja de tocar datos de tarjeta en ningún formato.

**Dónde se teclea el número, mientras tanto.** La regla de arriba es sobre lo que se guarda y lo que se registra, no sobre lo que se pide: cambiar de tarjeta en el perfil (RF-05) pide el número completo, porque nadie conoce «sus cuatro últimos dígitos» sueltos —los lee del plástico entero— y la marca está escrita en las primeras cifras del propio número (el IIN), así que preguntarla es preguntar algo que ya se sabe. De ese número el frontend deriva `last4` y la marca, comprueba la suma de Luhn antes de derivar nada, y **el resto no sale del navegador**: `toRequest` lo descarta y devuelve el `UpdateProfileRequest` del contrato, que no tiene dónde ponerlo. El CVV no se pide en ningún punto de esta pantalla, porque no hay nada para lo que recogerlo.

Eso tiene un coste que conviene tener anotado: una página que **toca** un PAN cae en un alcance de PCI-DSS más pesado (SAQ A-EP) que una donde lo recoge el iframe de la pasarela (SAQ A). Es la razón de más para que en v1.x ese campo lo sustituya el componente de la pasarela, y no solo el token que devuelve.

## 6. Otras correcciones de seguridad heredadas del legacy

- Contraseñas: hash BCrypt/Argon2 desde el primer día de `ukun-auth`; no existe el `VARCHAR(16)` del legacy ni ningún límite máximo artificial.
- CSRF: eliminado por diseño al convertir toda mutación en `POST`/`PUT`/`DELETE` (nunca `GET`, `03-analisis-y-patrones.md`) + cookie `XSRF-TOKEN` emitida por el gateway y validada en cada mutación.
- Open redirect (`AbsController.pathOrigenRedirect` usando `Referer` sin validar): desaparece junto con las redirecciones de servidor — una API REST no redirige, la navegación la decide el frontend.
- Ningún log vuelca contraseña, token ni número de tarjeta. Los tres casos del legacy (defectos #10, #20a, #20b) vienen de la misma causa estructural, `@Data` en entidades; el diseño completo de cumplimiento está en `11-observabilidad-y-logging.md` §4.

## 7. Mapeo a OWASP Top 10 (2021) y ASVS

Checklist de revisión, no declaración de intenciones — cada fila es un mecanismo concreto de este diseño:

| Categoría OWASP | Mecanismo en este diseño | Release |
|---|---|:-:|
| A01 Broken Access Control | Autorización en tres capas (§4): gateway grueso, `ukun-api` como autoridad final con `@PreAuthorize`, y autorización de recurso resuelta por `account_id` — el `storeId` de la ruta identifica, nunca autoriza | v0.4–v0.7 |
| A02 Cryptographic Failures | BCrypt/Argon2 para contraseñas; PAN/CVV nunca persistidos (§5); JWT firmado y validado por JWKS; cookie de sesión `HttpOnly`/`SameSite`/`Secure` (§8) | v0.4–v0.6 |
| A03 Injection | JPA/Hibernate con parámetros vinculados en toda consulta (nunca concatenación); validación de tipo real en subida de ficheros, no por extensión (§9) | continuo |
| A04 Insecure Design | Saga de signup con reserva+activación en vez de una transacción distribuida frágil (§3); idempotencia explícita en checkout y signup (ADR-0016) | v0.4/v0.6 |
| A05 Security Misconfiguration | Cabeceras de seguridad centralizadas en el gateway (§8), `spring.jpa.open-in-view: false`, perfiles de log distintos en dev/prod (`11-observabilidad-y-logging.md`) | v0.5/v1.0 |
| A06 Vulnerable and Outdated Components | Escaneo de dependencias en CI (§10), política de actualización ya fijada en `05-stack-y-versiones.md` | v1.0 |
| A07 Identification and Authentication Failures | Límite de intentos de login y limitación de tasa del gateway sobre `/api/**` (§8), política de contraseña alineada con NIST (§8), respuesta genérica en login fallido (§8), token nunca expuesto al navegador (ADR-0003) | v0.4/v0.5/v1.0 |
| A08 Software and Data Integrity Failures | Contrato OpenAPI versionado con check de deriva en CI (`04-roadmap.md`); ningún DDL fuera de Liquibase (skill `liquibase`) | v0.3/v0.2 |
| A09 Security Logging and Monitoring Failures | `11-observabilidad-y-logging.md`: eventos de autenticación y autorización denegada, tabla de auditoría para cambios de recurso, niveles calibrados (un `4xx` nunca es ERROR, así que un `5xx` real no se pierde entre ruido) | v0.4+ |
| A10 Server-Side Request Forgery | Sin llamadas salientes a URLs suministradas por el usuario en ningún flujo de este dominio (no hay webhooks ni fetch de recursos externos por URL de cliente); si aparece uno en v1.x, requiere su propio ADR con lista de permitidos | — |

## 8. Endurecimiento de autenticación y del gateway

- **Política de contraseña alineada con NIST SP 800-63B**: longitud mínima real (12 caracteres) en vez de reglas de composición arbitrarias (mayúscula+número+símbolo, que empujan a patrones predecibles), sin límite máximo artificial, y comprobación contra una lista de contraseñas comprometidas conocidas (p. ej. el dataset de Have I Been Pwned vía k-anonimato) en el signup y en el cambio de contraseña.
- **Límite de intentos de login** con Bucket4j en `ukun-auth`: bucket por combinación IP+email, backoff progresivo. Corta fuerza bruta sin bloquear una cuenta legítima por un ataque dirigido a otra.
- **Limitación de tasa en el gateway**, también con Bucket4j y por dirección remota, sobre `/api/**` y solo ahí: es la superficie que gobierna el contrato de errores, así que superarla responde `429 RATE_LIMITED` (`12-error-contract.md`) con `Retry-After` y la extensión `retryAfterSeconds`. Dos presupuestos: uno general, dimensionado para que quien navega la tienda no lo vea nunca, y uno estrecho para `POST /api/signup`. **No hay tramo de login ahí, y no es un olvido**: el formulario de login se envía al origen de `ukun-auth` y no cruza el gateway, de modo que su límite es el de la línea anterior. Quedan fuera del limitador `/media/**` (decenas de fotos por página), `/actuator` (lo sondea el contenedor), el bundle del SPA y `/oauth2/**` —una navegación de primer nivel a la que un `problem+json` se le vería en pantalla—.
- **Respuesta genérica en login fallido**: el mismo mensaje tanto si el correo no existe como si la contraseña es incorrecta — evita que el endpoint de login sirva como oráculo de enumeración de cuentas. El detalle real (`reason: CREDENTIALS|DISABLED`) solo llega al log (`11-observabilidad-y-logging.md` §3), nunca a la respuesta HTTP. Desde que la página de login es una plantilla propia y no la que genera Spring Security, esto **también es una decisión del HTML**: `templates/login.html` pinta una sola clave de mensaje para los dos motivos, y `LoginPageIT` compara las dos páginas de fallo enteras —enmascarando el token CSRF, que es lo único que legítimamente cambia— para que añadirle una pista servicial a una de las dos ramas rompa el build.
- **La página de login lleva la marca de la tienda** y sigue sirviéndose desde el origen de `ukun-auth`. Que sea otro origen no es un defecto que corregir: es el patrón habitual (`accounts.google.com`) y lo que mantiene la contraseña fuera del alcance del SPA. Su hoja de estilo se sirve sin sesión, y el idioma se negocia del `Accept-Language` con un conmutador ES|EN propio — el idioma elegido dentro del SPA no viaja hasta aquí mientras no se resuelva `ui_locales` en la petición de autorización del gateway.
- **Cabeceras de seguridad emitidas por el gateway** (único punto de entrada, skill `spring-gateway`): `Content-Security-Policy` (restringiendo `script-src`/`style-src` al propio origen, sin `unsafe-inline` salvo lo que react-bootstrap exija documentadamente), `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`. Es el reemplazo directo de que el nginx legacy no emita ninguna.
  - **`form-action` nombra al servidor de autorización, no solo a `'self'`.** El logout sale de un `<form>` que apunta a `/logout` del propio gateway, pero termina en el *end session endpoint* de `ukun-auth`, y el navegador aplica la directiva también a los redirects que sigue el envío de un formulario. Con solo `'self'` bloquea ese salto **sin que ningún log del servidor se entere**: el gateway registra su `auth.logout` y responde `302`, la sesión de `ukun-auth` nunca termina y volver a entrar devuelve al usuario anterior sin pedir contraseña. Es la directiva que sostiene el RP-initiated logout, no una lista de permitidos decorativa.
- **Atributos de la cookie de sesión**: `HttpOnly` (ADR-0003), `SameSite=Lax`, `Secure` en cualquier entorno servido por HTTPS (todos salvo `tilt up` en local).

## 9. Endurecimiento de subida de ficheros (RF-10, imágenes de producto)

**Una sola operación multipart en todo el sistema**: `PUT /api/admin/products/{id}/image`, con `DELETE` en la misma ruta para quitarla. La imagen es un sub-recurso propio, no un campo del alta ni de la edición del producto (RF-10) — así el resto del contrato es JSON puro y esta es la única superficie que hay que endurecer.

El legacy tiene un límite de tamaño en Spring (10MB) que nginx anula en la práctica al no declarar `client_max_body_size` (defecto #17) — no repetir eso es condición necesaria pero no suficiente:

- **Validación de tipo por contenido real, no por extensión ni `Content-Type` declarado**: un `.jpg` con cabecera de fichero ejecutable no se detecta mirando el nombre. Se verifican los primeros bytes (magic number) contra los formatos permitidos (JPEG, PNG, WebP) antes de aceptar el fichero. Si no coincide → `415 UNSUPPORTED_MEDIA_TYPE` con la extensión `allowed` (`12-error-contract.md`).
- **Límite de tamaño coherente en las dos capas** que lo tocan: **5 MB**, el mismo valor en el gateway y en `spring.servlet.multipart.max-file-size` — el defecto #17 es exactamente que uno decía 10MB y el otro no decía nada. Superarlo → `413 PAYLOAD_TOO_LARGE` con `maxBytes`, y es el único `code` del registro que pueden emitir dos servicios distintos (el gateway corta antes de que la petición llegue al API), así que los dos deben producir la misma forma de `ProblemDetail`.
- **Nombre de fichero generado por el servidor** (UUID + extensión derivada del tipo real detectado), nunca el nombre que envía el cliente — cierra path traversal vía nombre de fichero (`../../etc/passwd.jpg`) sin necesidad de sanearlo a mano. La clave de almacenamiento (`product.image_key`) no se expone: el API compone y devuelve una `imageUrl` servible.
- **Servido desde una ruta que no ejecuta nada**: las imágenes se sirven bajo `/media/**`, un prefijo que no es raíz de ejecución de ninguna aplicación y que no sirve ningún fichero que no haya escrito el propio adaptador. Esa ruta **no** está en el OpenAPI: es contenido estático, no una operación del contrato.

**Quién sirve `/media/**` y quién lo enruta.** Son dos servicios y conviene no confundirlos: el **gateway** es dueño de la *ruta* —declara el prefijo público y lo reenvía— y **`ukun-api`** es dueño del *adaptador*, porque es lo que se convierte en un cliente de S3 en v1.1 y duplicar la configuración de almacenamiento en un segundo servicio para ahorrar un salto sería el intercambio equivocado. El módulo `storage` (ADR-0017 §1) responde `GET /media/products/{key}` desde el directorio configurado en `ukun.storage.local-root`.

La **mitad de lectura** de ese módulo se adelantó a v0.3: el catálogo entrega una `imageUrl` servible desde su primer release y no había nadie al otro lado —toda foto de producto era un 404—. La subida de RF-10 (multipart, límite de 5 MB, comprobación del magic number) sigue en v0.7, que es donde estaba planificada.

Como la `key` llega en la URL, el adaptador es la barrera y valida dos veces: la clave debe ser **un solo segmento de ruta** (sin separador, sin punto inicial), y el fichero resuelto debe seguir bajo la raíz real —lo que cubre también un symlink dentro del directorio que apunte fuera—. Una clave inexistente y una que intenta salirse devuelven exactamente lo mismo, un `404` sin cuerpo: desde fuera no se distingue un sondeo de una errata. El `404` de esta ruta es la única respuesta de error del sistema que **no** es un `ProblemDetail`, y es deliberado — quien la pide es una etiqueta `<img>`, no el cliente generado del contrato.

## 10. Escaneo de dependencias

En CI desde v1.0 (`04-roadmap.md`), junto al resto de la tubería de calidad: OWASP Dependency-Check o el equivalente que ofrezca la plataforma de CI elegida, sobre los tres módulos Java y sobre `apps/ukun-web` (`npm audit` o equivalente). No antes de v1.0 por el mismo motivo que Dependabot/Renovate se difiere ahí en `05-stack-y-versiones.md`: sin CI aún, una alerta de vulnerabilidad no se puede verificar de forma automática y es ruido.
