---
name: spring-gateway
description: Convenciones de este repo para el gateway (services/ukun-gateway) — Spring Cloud Gateway como BFF, oauth2Login, TokenRelay, CSRF con cookie XSRF-TOKEN, enrutado y servido del SPA. Usar al implementar o revisar rutas, filtros de seguridad, o el reemplazo de nginx.
---

# Convenciones de `services/ukun-gateway`

Lee `docs/08-identidad-y-seguridad.md` (sección BFF) y ADR-0003 antes de tocar este servicio.

## Rol de este servicio

Es el único punto de entrada del sistema desde el navegador, sustituyendo a `nginx/`. Actúa de **BFF**: el navegador nunca ve un JWT, solo una cookie de sesión `HttpOnly`/`SameSite=Lax`. Internamente hace `oauth2Login` contra `ukun-auth` y relevo de token (`TokenRelay`) hacia `ukun-api` con `Authorization: Bearer <JWT>`.

## Reglas duras

1. **Nunca se expone el JWT al navegador** en ninguna respuesta ni cabecera legible por JavaScript. Si una tarea pide "pasar el token al frontend", para — probablemente el diseño correcto es que el gateway haga la llamada por el frontend, no que el frontend reciba el token.
2. **CSRF vía cookie `XSRF-TOKEN`** validada en cada mutación (`POST`/`PUT`/`DELETE`) que pase por el gateway. Es la sustitución directa del problema real que tiene el legacy (mutaciones por `GET`, cero protección CSRF).
3. **Filtro grueso de rutas por rol** aquí (`/api/admin/**` exige `ROLE_ADMIN`, `/api/me/**` exige sesión, `/api/products` público) — es defensa en profundidad, **no** la única barrera. `ukun-api` vuelve a validar todo como resource server; el gateway nunca es la única autoridad de autorización.
4. **Reglas de enrutado explícitas y versionadas**, equivalentes y sustitutas de `nginx/nginx.conf`. Cuando se borre `nginx/` (v0.5 en `docs/04-roadmap.md`), este servicio debe cubrir cada ruta que nginx cubría, incluyendo el `client_max_body_size` para subida de imágenes que el nginx actual no tenía (bug real corregido, no una feature nueva).
5. **Sesión en memoria mientras haya una sola réplica.** No introducir Redis u otro backend de sesión distribuida sin que haya una razón real de despliegue con más de una réplica.
6. **Sirve el SPA estático en producción** (build de `apps/ukun-web`) — en desarrollo, Vite sirve su propio dev server con HMR y el gateway solo enruta `/api`, `/oauth2`, etc. (ver `docs/06-entorno-local-tilt.md`).
7. **Cabeceras de seguridad en toda respuesta**: `Content-Security-Policy`, `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin` — es el reemplazo directo de que nginx hoy no emita ninguna. Ver `docs/08-identidad-y-seguridad.md` §8.
8. **Cookie de sesión con `SameSite=Lax` y `Secure`** en cualquier entorno servido por HTTPS (todos salvo `tilt up` local), además del `HttpOnly` ya fijado en ADR-0003.
9. **Propaga `traceparent`** hacia `ukun-api`/`ukun-auth` (Micrometer Tracing, desde v0.5) — es el único servicio que ve la petición completa del navegador, sin su span la saga de signup no se puede correlacionar. Ver `docs/11-observabilidad-y-logging.md` §5.

## Qué no construir aquí

Lógica de negocio de ningún tipo. Este servicio enruta, autentica y aplica políticas transversales (CORS ya no aplica al ser mismo origen, rate limiting en v1.0) — cualquier regla de dominio (disponibilidad de stock, cálculo de totales, autorización sobre un recurso concreto) vive en `ukun-api`.
