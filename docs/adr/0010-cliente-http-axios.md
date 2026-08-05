# ADR-0010: axios como cliente HTTP del frontend, no `fetch`

## Estado
Aceptado

## Contexto
`07-frontend-ux-ui.md` dejaba `shared/api/` documentado como "cliente HTTP (fetch/axios)" sin decidir entre ambos. Con el diseño de autenticación de `08-identidad-y-seguridad.md` (BFF en `ukun-gateway`, cookie de sesión `HttpOnly` + cookie `XSRF-TOKEN`), el cliente HTTP del frontend necesita: adjuntar el header `X-XSRF-TOKEN` leído de la cookie en toda mutación, enviar `credentials`/cookies en cada petición, traducir el `ProblemDetail` (RFC 9457) de cada error a un tipo manejable por TanStack Query, y reaccionar de forma centralizada a un `401` (sesión caducada → redirigir a login) sin repetir ese código en cada hook.

## Decisión
**axios**, envuelto en una única instancia configurada en `shared/api/http.ts`:
- `withCredentials: true` por defecto.
- Interceptor de request que añade `X-XSRF-TOKEN` desde la cookie en toda petición `POST`/`PUT`/`DELETE`.
- Interceptor de response que normaliza cualquier error 4xx/5xx a un tipo `ProblemDetailError` con `status`, `code`, `detail` y `errors[]`, y que redirige a `/login` en un 401.
- Los hooks de TanStack Query (`useQuery`/`useMutation`) llaman a esta instancia, nunca a `fetch` directamente ni a una instancia de axios distinta por feature.

## Consecuencias
- Todo el manejo de `XSRF-TOKEN` y de `401` vive en un único fichero, no repetido por pantalla.
- Los tipos generados por `openapi-typescript` (`types/api.d.ts`) siguen siendo la fuente de los tipos de payload/respuesta; axios solo transporta, no tipa por sí mismo.
- Una dependencia más en el bundle frente a `fetch` nativo — aceptable: el peso de axios es marginal frente a react-bootstrap y TanStack Query ya presentes, y el código que ahorra (interceptores, cancelación, transformación de errores) sería necesario escribirlo a mano sobre `fetch`.

## Alternativas descartadas
- **`fetch` nativo + wrapper propio**: evita una dependencia, pero replica a mano exactamente lo que da axios (interceptores, manejo uniforme de error, cancelación con `AbortController` gestionada). Sobre un BFF de mismo origen el argumento "menos dependencias" pesa menos que en un cliente que hablase con múltiples orígenes.
