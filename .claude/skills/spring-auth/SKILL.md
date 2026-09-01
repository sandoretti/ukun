---
name: spring-auth
description: Convenciones de este repo para el servicio de identidad (services/ukun-auth) — Authorization Server dentro de Spring Security 7, modelo de datos anémico (account/account_role), claims del JWT, endpoints internos /internal/accounts y la saga de signup idempotente. Usar al implementar o revisar cualquier cosa relacionada con login, registro de cuentas o emisión de tokens.
---

# Convenciones de `services/ukun-auth`

Lee `docs/08-identidad-y-seguridad.md` antes de tocar este servicio — este skill resume sus reglas operativas, el documento explica el porqué completo.

## Estructura: capas, **sin** Spring Modulith (ver ADR-0022)

`ukun-auth` es deliberadamente anémico y no está modularizado con Modulith. Lo estuvo durante v0.4 (ADR-0017 §4) y **ADR-0022 lo revirtió**: la herramienta existe para preparar la extracción de un monolito, y este servicio ya *es* la extracción. Si escribes un `package-info.java` con `@ApplicationModule` aquí, has copiado de una fuente desactualizada — eso es `ukun-api`, y `docs/14-modules.md` es un documento solo suyo.

```
dev.sandoretti.ukun.auth/
  domain/       Account, Role
  infra/        AccountRepository
  application/  AccountsService, AccountCredentials
  config/       AuthorizationServerConfig, JwkSourceConfig, PasswordEncoderConfig,
                RegisteredClientProperties, RegisteredClientSeeder, SchedulerLockConfig
  security/     AccountUserDetailsService, TokenClaimsCustomizer, LoginEventListener,
                BusinessEvents, LoginAttemptBuckets, LoginRateLimiter
  web/          los @RestController de /internal/accounts
```

Los nombres de paquete son los mismos que `ukun-api` usa **dentro** de cada módulo, a propósito: moverse entre los dos servicios no debería exigir aprender dos nomenclaturas.

**La entidad no sale de `application`.** No hay Modulith que lo impida, pero sí la cuarta de las siete reglas ArchUnit («ningún método público de un `@Service` devuelve una `@Entity`»), y por eso `AccountsService` devuelve `AccountCredentials` y no `Account`. Esa regla es la razón real, no un detalle de estilo.

```
account(id UUID PK, email CITEXT UNIQUE, password_hash, enabled, created_at, updated_at)
account_role(account_id, role)   -- CUSTOMER | EMPLOYEE | ADMIN
registration_request(idempotency_key PK, account_id, created_at)   -- idempotencia de altas
+ tablas propias de spring-security-oauth2-authorization-server (oauth2_registered_client, oauth2_authorization, ...)
+ desde v1.0 (ADR-0024), tres piezas de estado que estaban en el heap y con N réplicas dejaban de funcionar:
  spring_session / spring_session_attributes   -- la sesión del login y del endpoint de autorización
  shedlock                                     -- un @Scheduled corre una vez por disparo, no una por réplica
  login_attempt_bucket                         -- el presupuesto del límite de intentos, uno y no N
```

**`name`, `last_name` NO viven aquí** — son datos de perfil, viven en `ukun-api` (ver ADR-0004). Si te piden añadir un campo de perfil a `account`, para y relee el ADR antes de hacerlo: probablemente pertenece a `ukun-api`. Nombres de tabla y de código en inglés desde el primer commit (ADR-0018) — este servicio nace en inglés, no tiene fase transitoria con esquema legacy.

## Reglas duras

1. **Contraseñas con hash** (BCrypt vía `PasswordEncoder` de Spring Security), nunca en claro. El legacy las guardaba en claro en `VARCHAR(16)` — no repetir ni el algoritmo ni el límite de longitud. Política alineada con NIST SP 800-63B: mínimo 12 caracteres, sin reglas de composición arbitrarias, sin máximo artificial, comprobación contra contraseñas comprometidas conocidas en signup y cambio de contraseña. Detalle en `docs/08-identidad-y-seguridad.md` §8.
2. **Nunca loguear contraseña, token ni ningún claim sensible.** El legacy lo hacía en cada intento fallido de login — es exactamente el error a no repetir. Los eventos de login/logout se emiten desde un `ApplicationListener` de los eventos que Spring Security ya publica (`AuthenticationSuccessEvent`/`AbstractAuthenticationFailureEvent`), no a mano desde un controller — ver `docs/11-observabilidad-y-logging.md` §6.
3. **El correo es la única credencial de identidad que sale de este servicio hacia el resto del sistema** (como claim `email` en el JWT). Nombre/apellido no se exponen desde aquí.
4. **Alta de cuenta siempre en dos pasos: reserva (`enabled=false`) + activación.** Nunca una cuenta se crea ya activa de un solo paso — así un fallo de `ukun-api` a mitad de la saga de signup no deja cuentas activas huérfanas. Detalle exacto de la saga en `docs/08-identidad-y-seguridad.md`.
5. **`/internal/accounts` y sus variantes (`:activate`, `:release`) requieren `client_credentials` con scope `accounts:write`** — no son endpoints públicos, los llama `ukun-api`, nunca el navegador.
6. **Idempotencia real vía `Idempotency-Key`**, no "confiar en que el cliente no reintente". La tabla `registration_request` existe para esto.
7. **Reconciliación por job programado**: cuentas `enabled=false` con más de 24h se borran. Sin esto, una saga de signup interrumpida deja basura para siempre. Lleva `@SchedulerLock` desde v1.0 — ver la regla 13.
8. **`storeId` nunca aparece en un claim del JWT.** Es un dato de negocio, vive en `ukun-api`; meterlo aquí acopla este servicio al dominio de tiendas (ver `docs/08-identidad-y-seguridad.md`).
9. **Límite de intentos de login con Bucket4j** (bucket por IP+email, backoff progresivo), **con el presupuesto en `ukun_auth` y no en el heap** desde v1.0 (ADR-0024) — y su otra mitad inseparable, `server.forward-headers-strategy: native` en el documento `!dev`: un presupuesto compartido con la IP equivocada cuenta todos los intentos del mundo contra un solo bucket y no limita nada. `native` y no `framework` porque es la única variante con lista de proxies de confianza. Y **respuesta genérica en fallo** ("credenciales incorrectas" tanto si el correo no existe como si la contraseña es incorrecta) — el detalle real solo va al log, nunca a la respuesta HTTP. Ver `docs/08-identidad-y-seguridad.md` §8.
10. **La página de login es de este servicio, y es la única vista renderizada en servidor de todo el sistema nuevo.** No es un regreso del Thymeleaf del legacy: el frontend sigue siendo React contra REST (ADR-0002), pero la contraseña se teclea aquí y nunca en el SPA (ADR-0003), así que esa pantalla cae de este lado. Cuatro cosas que no se deducen del código y cuestan una tarde cada una:
    - **`/login` se permite por ruta en `authorizeHttpRequests`, no solo con el `permitAll()` de `formLogin`.** Ese registra un `ExactUrlRequestMatcher`: permite `/login` y nada más, así que `/login?lang=en` cae en `anyRequest().authenticated()` y el entry point lo rebota quitándole el parámetro. Su hoja de estilo necesita el suyo propio (`/css/**`) por lo mismo.
    - **`spring.messages.fallback-to-system-locale: false`.** Con el valor por defecto, un idioma servido desde el fichero base —`es`, que vive en `messages.properties`— cae al locale **del sistema** antes que a ese fichero. La imagen trae `LANG=en_US`: la página salía en inglés para todo el mundo, y en español en la máquina de quien la construyó. El idioma de quien compila no decide el de quien lee.
    - **Thymeleaf prohíbe leer `param` dentro de una expresión de enlace** (`th:href="@{...}"`), a propósito, para que un parámetro de la petición no acabe tejido en una URL que la página emite. Lo que dependa de la query se decide en el controlador y llega como atributo de modelo.
    - **La paleta está copiada de `_tokens.scss` y vigilada por `PaletteParityTest`**, no compartida: los dos artefactos los construyen cadenas de herramientas distintas y acoplarlas costaría poder construir este servicio solo. Cambiar un color aquí sin cambiarlo allí rompe el build, que es exactamente lo que se quiere.

11. **La clave de firma sale de configuración, nunca del arranque** (ADR-0024). `ukun.signing-key` es un PKCS#8 en base64 **sin valor por defecto**, igual que los dos secretos de cliente: el servicio se niega a arrancar antes que arrancar con una clave conocida. El `kid` es el thumbprint RFC 7638 de la propia clave, así que dos réplicas publican el mismo sin ponerse de acuerdo. Si te piden rotación con solapamiento de `kid`, relee ADR-0024: está descartada a propósito hasta que haya una rotación real que gestionar.
12. **La sesión vive en `ukun_auth`** (`spring-session-jdbc`, ADR-0024), con sus dos tablas creadas por Liquibase y `initialize-schema: never` — el esquema de esta base tiene un solo dueño. Y **el nombre de la cookie se fija a `JSESSIONID` a mano**: el defecto de Spring Session es `SESSION`, que es el mismo que usa la sesión del gateway, y las cookies **no distinguen puerto**, así que sobre `localhost` se pisan y el login deja de completarse — el gateway pierde su `code_verifier` de PKCE y responde `/login?error` sin decir por qué. Si quitas esa línea, el stack local deja de dejar entrar a nadie. La cadena `/internal/**` sigue siendo `STATELESS` y no toca nada de esto.
13. **Cuándo un `@Scheduled` lleva `@SchedulerLock`**, que es la regla que este servicio estrena y que tiene tres casos y no uno:
    - **Obligatorio** si el efecto no es repetible entre réplicas: manda un correo, cobra, llama a un tercero, o escribe estado derivado. Hoy no hay ningún job así en todo el sistema — por eso ninguno lo lleva por necesidad.
    - **Basta la idempotencia** en un `DELETE … WHERE`: N réplicas ejecutándolo es desperdicio, no un defecto. `AccountReconciliationJob` lo lleva igualmente, porque es el molde del que se copiará el siguiente; `LoginAttemptBuckets.discardIdleBuckets` no, porque corre cada diez minutos y una fila de lock reescrita a ese ritmo cuesta más contención que el borrado que protege.
    - **Prohibido** si el estado es de la JVM. `RequestRateLimitBuckets.discardIdleBuckets` del gateway purga un mapa en memoria: ponerle lock dejaría a todas las réplicas menos una creciendo ese mapa para siempre, y el limitador se convertiría en la denegación de servicio que existe para evitar. **Esta es la mitad de la regla que se olvida.**

    Y una trampa medida, no supuesta: **`lockAtLeastFor` hace que el job se salte en silencio** durante ese tiempo tras cada ejecución. Con `usingDbTime()` hay un solo reloj y no absorbe nada; lo que sí hizo fue que dos tests de `AccountReconciliationIT` pasaran sin ejecutar el job. No lo pongas.

## Claims del access token

```json
{ "iss": "...", "sub": "<uuid de account>", "aud": ["ukun-api"],
  "email": "...", "roles": ["CUSTOMER"], "scope": "openid profile ukun.read ukun.write" }
```

`roles` es un array de un único rol de negocio salvo el sub-flag admin en empleados — ver ADR-0006 sobre por qué cliente y empleado son mutuamente excluyentes.

## Datos de desarrollo

Los datos actuales son desechables (ADR-0007): no hay migración de `cuenta` legacy, se siembra este servicio con las mismas cuentas de demostración pero con contraseña ya hasheada. No construyas ningún script de "migración desde `cuenta`" salvo que esa ADR se revierta explícitamente.

## Testing y verificación arquitectónica

Las **siete reglas** de `docs/03-analisis-y-patrones.md` §3, que viven en el proyecto compartido `:ukun-test-support` y se aplican desde `ArchitectureTest`. Son la única verificación arquitectónica de este servicio desde ADR-0022, y aquí es donde más valen: es el servicio que guarda hashes de contraseña, así que una entidad con `toString` generado o pasada a `log.*(...)` filtra una credencial, no el nombre de un producto.

No hay `ModulithTest` — si estás a punto de escribir uno, relee ADR-0022. Convenciones de test (AssertJ, object mothers, Testcontainers singleton): skill `testing` y `docs/13-testing.md`.

## Skills externas de apoyo

`java-springboot` y `springboot-patterns` (ver `.claude/skills/README.md`) valen como repaso general de Spring Boot, pero **esta skill y `docs/08-identidad-y-seguridad.md` mandan sobre ellas**. En concreto: ninguna de las dos conoce la saga de signup en dos pasos (regla 4), la frontera de qué datos viven aquí y cuáles en `ukun-api` (ADR-0004), ni la prohibición de meter `storeId` en un claim (regla 8). Su consejo genérico de "usa BCrypt" sí coincide con la regla 1. Conflictos completos en `.claude/skills/README.md`.
