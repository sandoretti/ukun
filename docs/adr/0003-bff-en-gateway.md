# ADR-0003: Autenticación vía BFF en el gateway, no token en el SPA

## Estado
Aceptado

## Contexto
El sistema actual no tiene CSRF real (mutaciones por `GET` en toda la aplicación, ver `docs/03-analisis-y-patrones.md`) y la sesión se maneja como un atributo `HttpSession` por app. Al diseñar la autenticación nueva (Spring Authorization Server en `ukun-auth`) había que decidir cómo el navegador maneja el token: guardarlo en el SPA (Authorization Code + PKCE directo desde React) o nunca exponerlo al navegador, con el gateway actuando de Backend For Frontend (BFF).

## Decisión
**BFF en `ukun-gateway`**: el navegador solo recibe una cookie de sesión `HttpOnly`/`SameSite=Lax`. El gateway hace `oauth2Login` contra `ukun-auth` (Authorization Code + PKCE) y releva las peticiones hacia `ukun-api` con `Authorization: Bearer <JWT>` (TokenRelay). El SPA nunca ve ni maneja el token. Diseño completo en `docs/08-identidad-y-seguridad.md`.

## Consecuencias
- El token queda fuera del alcance de cualquier XSS del SPA — no hay dónde robarlo desde JavaScript.
- No hace falta resolver refresh silencioso de tokens en el cliente: lo gestiona el gateway server-side.
- Frontend y API quedan de facto en el mismo origen desde el punto de vista del navegador (el gateway es el único destino): sin CORS, sin preflights.
- El gateway pasa a tener estado (sesión). Asumible con una sola réplica (sesión en memoria); si en el futuro hace falta más de una réplica, hay que sacar esa sesión del proceso. **Esa frase decía «se añade Redis», y ADR-0024 la revisa**: llegado el momento (issue #118) el almacén fue Postgres y no Redis, por el presupuesto de despliegue de ADR-0023. El coste está donde se decidió, no aquí.
- Hace falta emitir y validar una cookie `XSRF-TOKEN` en cada mutación, ya que la protección CSRF que da "no hay token en JS" no cubre las cookies en sí mismas.

## Alternativas descartadas
- **Token en el SPA** (Authorization Code + PKCE directo desde React, token en memoria, refresh rotativo): gateway más simple y sin estado, pero expone el token a cualquier XSS y obliga a resolver CORS y refresh silencioso en el cliente. Dado que el propio sistema legacy ya tiene un historial de CSRF trivial, se prioriza el diseño que elimina esa clase de riesgo por construcción.
