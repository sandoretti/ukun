---
name: spring-auth
description: Convenciones de este repo para el servicio de identidad (services/ukun-auth) — Authorization Server dentro de Spring Security 7, modelo de datos anémico (account/account_role), claims del JWT, endpoints internos /internal/accounts y la saga de signup idempotente. Usar al implementar o revisar cualquier cosa relacionada con login, registro de cuentas o emisión de tokens.
---

# Convenciones de `services/ukun-auth`

Lee `docs/08-identidad-y-seguridad.md` antes de tocar este servicio — este skill resume sus reglas operativas, el documento explica el porqué completo.

## Estructura: módulos de Spring Modulith (ver ADR-0017)

`ukun-auth` es deliberadamente anémico, y aquí la modularización pesa menos que en `ukun-api` — se adopta por coherencia de herramienta con el resto del backend, no porque haga cumplir una frontera de negocio compleja:

| Módulo | Contenido | `allowedDependencies` |
|---|---|---|
| `accounts` | `account`, `account_role`, hash de contraseña, endpoints `/internal/accounts` | — |
| `registration` | saga de signup, `registration_request` | `accounts` |
| `oauth2` | `RegisteredClient`, JWKS, claims, rate limit de login | `accounts` |

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

## Claims del access token

```json
{ "iss": "...", "sub": "<uuid de account>", "aud": ["ukun-api"],
  "email": "...", "roles": ["CUSTOMER"], "scope": "openid profile ukun.read ukun.write" }
```

`roles` es un array de un único rol de negocio salvo el sub-flag admin en empleados — ver ADR-0006 sobre por qué cliente y empleado son mutuamente excluyentes.

## Datos de desarrollo

Los datos actuales son desechables (ADR-0007): no hay migración de `cuenta` legacy, se siembra este servicio con las mismas cuentas de demostración pero con contraseña ya hasheada. No construyas ningún script de "migración desde `cuenta`" salvo que esa ADR se revierta explícitamente.

## Testing y verificación de módulos

`ApplicationModules.verify()` (`docs/14-modules.md`) desde el skeleton. Convenciones de test (AssertJ, object mothers, Testcontainers singleton): skill `testing` y `docs/13-testing.md`.

## Skills externas de apoyo

`java-springboot` y `springboot-patterns` (ver `.claude/skills/README.md`) valen como repaso general de Spring Boot, pero **esta skill y `docs/08-identidad-y-seguridad.md` mandan sobre ellas**. En concreto: ninguna de las dos conoce la saga de signup en dos pasos (regla 4), la frontera de qué datos viven aquí y cuáles en `ukun-api` (ADR-0004), ni la prohibición de meter `storeId` en un claim (regla 8). Su consejo genérico de "usa BCrypt" sí coincide con la regla 1. Conflictos completos en `.claude/skills/README.md`.
