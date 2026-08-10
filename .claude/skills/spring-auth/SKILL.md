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
                RegisteredClientProperties, RegisteredClientSeeder
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
```

**`name`, `last_name` NO viven aquí** — son datos de perfil, viven en `ukun-api` (ver ADR-0004). Si te piden añadir un campo de perfil a `account`, para y relee el ADR antes de hacerlo: probablemente pertenece a `ukun-api`. Nombres de tabla y de código en inglés desde el primer commit (ADR-0018) — este servicio nace en inglés, no tiene fase transitoria con esquema legacy.

## Reglas duras

1. **Contraseñas con hash** (BCrypt vía `PasswordEncoder` de Spring Security), nunca en claro. El legacy las guardaba en claro en `VARCHAR(16)` — no repetir ni el algoritmo ni el límite de longitud. Política alineada con NIST SP 800-63B: mínimo 12 caracteres, sin reglas de composición arbitrarias, sin máximo artificial, comprobación contra contraseñas comprometidas conocidas en signup y cambio de contraseña. Detalle en `docs/08-identidad-y-seguridad.md` §8.
2. **Nunca loguear contraseña, token ni ningún claim sensible.** El legacy lo hacía en cada intento fallido de login — es exactamente el error a no repetir. Los eventos de login/logout se emiten desde un `ApplicationListener` de los eventos que Spring Security ya publica (`AuthenticationSuccessEvent`/`AbstractAuthenticationFailureEvent`), no a mano desde un controller — ver `docs/11-observabilidad-y-logging.md` §6.
3. **El correo es la única credencial de identidad que sale de este servicio hacia el resto del sistema** (como claim `email` en el JWT). Nombre/apellido no se exponen desde aquí.
4. **Alta de cuenta siempre en dos pasos: reserva (`enabled=false`) + activación.** Nunca una cuenta se crea ya activa de un solo paso — así un fallo de `ukun-api` a mitad de la saga de signup no deja cuentas activas huérfanas. Detalle exacto de la saga en `docs/08-identidad-y-seguridad.md`.
5. **`/internal/accounts` y sus variantes (`:activate`, `:release`) requieren `client_credentials` con scope `accounts:write`** — no son endpoints públicos, los llama `ukun-api`, nunca el navegador.
6. **Idempotencia real vía `Idempotency-Key`**, no "confiar en que el cliente no reintente". La tabla `registration_request` existe para esto.
7. **Reconciliación por job programado**: cuentas `enabled=false` con más de 24h se borran. Sin esto, una saga de signup interrumpida deja basura para siempre.
8. **`storeId` nunca aparece en un claim del JWT.** Es un dato de negocio, vive en `ukun-api`; meterlo aquí acopla este servicio al dominio de tiendas (ver `docs/08-identidad-y-seguridad.md`).
9. **Límite de intentos de login con Bucket4j** (bucket por IP+email, backoff progresivo) y **respuesta genérica en fallo** ("credenciales incorrectas" tanto si el correo no existe como si la contraseña es incorrecta) — el detalle real solo va al log, nunca a la respuesta HTTP. Ver `docs/08-identidad-y-seguridad.md` §8.
10. **La página de login es de este servicio, y es la única vista renderizada en servidor de todo el sistema nuevo.** No es un regreso del Thymeleaf del legacy: el frontend sigue siendo React contra REST (ADR-0002), pero la contraseña se teclea aquí y nunca en el SPA (ADR-0003), así que esa pantalla cae de este lado. Cuatro cosas que no se deducen del código y cuestan una tarde cada una:
    - **`/login` se permite por ruta en `authorizeHttpRequests`, no solo con el `permitAll()` de `formLogin`.** Ese registra un `ExactUrlRequestMatcher`: permite `/login` y nada más, así que `/login?lang=en` cae en `anyRequest().authenticated()` y el entry point lo rebota quitándole el parámetro. Su hoja de estilo necesita el suyo propio (`/css/**`) por lo mismo.
    - **`spring.messages.fallback-to-system-locale: false`.** Con el valor por defecto, un idioma servido desde el fichero base —`es`, que vive en `messages.properties`— cae al locale **del sistema** antes que a ese fichero. La imagen trae `LANG=en_US`: la página salía en inglés para todo el mundo, y en español en la máquina de quien la construyó. El idioma de quien compila no decide el de quien lee.
    - **Thymeleaf prohíbe leer `param` dentro de una expresión de enlace** (`th:href="@{...}"`), a propósito, para que un parámetro de la petición no acabe tejido en una URL que la página emite. Lo que dependa de la query se decide en el controlador y llega como atributo de modelo.
    - **La paleta está copiada de `_tokens.scss` y vigilada por `PaletteParityTest`**, no compartida: los dos artefactos los construyen cadenas de herramientas distintas y acoplarlas costaría poder construir este servicio solo. Cambiar un color aquí sin cambiarlo allí rompe el build, que es exactamente lo que se quiere.

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
