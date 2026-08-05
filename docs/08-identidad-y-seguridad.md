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

El alta de empleado (`POST /api/employees`, solo admin) reutiliza exactamente la misma saga (`AccountRegistrationService`), cambiando `roles: ["EMPLOYEE"]` (+`"ADMIN"` si aplica) y tomando `storeId` del admin que la ejecuta, nunca del cuerpo de la petición.

## 4. Autenticación en el navegador: BFF en el gateway

Flujo Authorization Code + PKCE, pero el SPA **nunca ve el token**:

```
navegador ──cookie de sesión HttpOnly/SameSite=Lax──> ukun-gateway
ukun-gateway (oauth2Login + TokenRelay) ──Bearer JWT──> ukun-api
ukun-gateway <──code + PKCE, refresh rotation──> ukun-auth
```

Por qué BFF y no token en el SPA: elimina el token del alcance de cualquier XSS, elimina el refresh silencioso desde JavaScript, y como el navegador solo habla con el gateway, front y API quedan de facto en el mismo origen (sin CORS, sin preflights). Coste asumido: el gateway tiene estado (sesión) — en memoria mientras haya una sola réplica; Redis solo si algún día hay más de una. Ver ADR-0003.

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
| Gateway | Autenticación + filtro grueso de rutas. Defensa en profundidad, no la única barrera | `/api/admin/**` exige `ROLE_ADMIN`; `/api/me/**` exige sesión; `/api/products` público |
| `ukun-api` | **Autoridad final.** Valida el JWT él mismo como resource server (JWKS de `ukun-auth`), nunca confía en cabeceras inyectadas por el gateway | `@PreAuthorize("hasRole('ADMIN')")` en el CRUD de productos |
| Dominio | Autorización *a nivel de recurso*, imposible de expresar en una anotación | "esta línea de `store_stock` pertenece a mi tienda"; "este pedido es mío" |

La tercera capa es la que el legacy tiene mal repartida: `StockController` compara IDs de tienda a mano sobre un `tiendaId` que llega como parámetro de la petición. En el nuevo diseño la ruta **sí** lleva la tienda —`PUT /api/stores/{storeId}/stock/{lineId}` (RF-09, ADR-0015, ADR-0018)— y lo que cambia es quién decide si vale:

> El `storeId` de la ruta identifica el recurso; **no autoriza nada**. El servicio resuelve la tienda del empleado a partir de `account_id` y compara: si no coinciden, `403 FORBIDDEN` con evento `auth.authorization.denied` (`reason=FOREIGN_RESOURCE`). Un `storeId` forzado a mano no es un agujero, es un 403 registrado.

Se prefiere esta forma a la alternativa de omitir la tienda de la ruta (`PUT /api/stock/{productId}`, con la tienda siempre inferida): la línea de stock tiene identidad propia desde ADR-0015 y merece una URL estable y direccionable, y una ruta que oculta a qué recurso apunta es más difícil de auditar, no más segura.

## 5. El problema PCI-DSS de la tabla de tarjeta

El legacy almacena el número completo (PAN) **y el CVV** en claro. Almacenar el CVV está prohibido por PCI-DSS bajo cualquier circunstancia, incluso cifrado — no es un matiz de seguridad, es una regla sin excepciones del estándar.

Plan en dos pasos:
- **v0.6**: `card` nace sin CVV y sin PAN. Solo se persiste `last4`, marca y fecha de expiración (`10-modelo-de-datos.md` §2); el CVV se pide en cada compra y nunca se escribe a disco.
- **v1.x**: integración con una pasarela real (Stripe/Redsys u otra) en modo test, guardando únicamente el token que la pasarela devuelve — `ukun-api` deja de tocar datos de tarjeta en ningún formato.

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
| A07 Identification and Authentication Failures | Límite de intentos de login (§8), política de contraseña alineada con NIST (§8), respuesta genérica en login fallido (§8), token nunca expuesto al navegador (ADR-0003) | v0.4/v0.5 |
| A08 Software and Data Integrity Failures | Contrato OpenAPI versionado con check de deriva en CI (`04-roadmap.md`); ningún DDL fuera de Liquibase (skill `liquibase`) | v0.3/v0.2 |
| A09 Security Logging and Monitoring Failures | `11-observabilidad-y-logging.md`: eventos de autenticación y autorización denegada, tabla de auditoría para cambios de recurso, niveles calibrados (un `4xx` nunca es ERROR, así que un `5xx` real no se pierde entre ruido) | v0.4+ |
| A10 Server-Side Request Forgery | Sin llamadas salientes a URLs suministradas por el usuario en ningún flujo de este dominio (no hay webhooks ni fetch de recursos externos por URL de cliente); si aparece uno en v1.x, requiere su propio ADR con lista de permitidos | — |

## 8. Endurecimiento de autenticación y del gateway

- **Política de contraseña alineada con NIST SP 800-63B**: longitud mínima real (12 caracteres) en vez de reglas de composición arbitrarias (mayúscula+número+símbolo, que empujan a patrones predecibles), sin límite máximo artificial, y comprobación contra una lista de contraseñas comprometidas conocidas (p. ej. el dataset de Have I Been Pwned vía k-anonimato) en el signup y en el cambio de contraseña.
- **Límite de intentos de login** con Bucket4j en `ukun-auth`: bucket por combinación IP+email, backoff progresivo. Corta fuerza bruta sin bloquear una cuenta legítima por un ataque dirigido a otra.
- **Respuesta genérica en login fallido**: el mismo mensaje tanto si el correo no existe como si la contraseña es incorrecta — evita que el endpoint de login sirva como oráculo de enumeración de cuentas. El detalle real (`reason: CREDENTIALS|DISABLED`) solo llega al log (`11-observabilidad-y-logging.md` §3), nunca a la respuesta HTTP.
- **Cabeceras de seguridad emitidas por el gateway** (único punto de entrada, skill `spring-gateway`): `Content-Security-Policy` (restringiendo `script-src`/`style-src` al propio origen, sin `unsafe-inline` salvo lo que react-bootstrap exija documentadamente), `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`. Es el reemplazo directo de que el nginx legacy no emita ninguna.
- **Atributos de la cookie de sesión**: `HttpOnly` (ADR-0003), `SameSite=Lax`, `Secure` en cualquier entorno servido por HTTPS (todos salvo `tilt up` en local).

## 9. Endurecimiento de subida de ficheros (RF-10, imágenes de producto)

El legacy tiene un límite de tamaño en Spring (10MB) que nginx anula en la práctica al no declarar `client_max_body_size` (defecto #17) — no repetir eso es condición necesaria pero no suficiente:

- **Validación de tipo por contenido real, no por extensión ni `Content-Type` declarado**: un `.jpg` con cabecera de fichero ejecutable no se detecta mirando el nombre. Se verifican los primeros bytes (magic number) contra los formatos permitidos (JPEG, PNG, WebP) antes de aceptar el fichero.
- **Límite de tamaño coherente en las dos capas** que lo tocan: el del gateway y `spring.servlet.multipart.max-file-size` llevan el mismo valor — el defecto #17 es exactamente que uno decía 10MB y el otro no decía nada.
- **Nombre de fichero generado por el servidor** (UUID + extensión derivada del tipo real detectado), nunca el nombre que envía el cliente — cierra path traversal vía nombre de fichero (`../../etc/passwd.jpg`) sin necesidad de sanearlo a mano.
- **Servido desde una ruta que no ejecuta nada**: el directorio de subida no debe ser alcanzable como raíz de ejecución de la aplicación ni permitir subir un fichero con extensión ejecutable por el servidor web que lo sirve.

## 10. Escaneo de dependencias

En CI desde v1.0 (`04-roadmap.md`), junto al resto de la tubería de calidad: OWASP Dependency-Check o el equivalente que ofrezca la plataforma de CI elegida, sobre los tres módulos Java y sobre `apps/ukun-web` (`npm audit` o equivalente). No antes de v1.0 por el mismo motivo que Dependabot/Renovate se difiere ahí en `05-stack-y-versiones.md`: sin CI aún, una alerta de vulnerabilidad no se puede verificar de forma automática y es ruido.
