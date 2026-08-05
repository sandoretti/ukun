# ADR-0004: `nombre`/`apellido` se quedan en `ukun-api`, no se van a `ukun-auth`

## Estado
Aceptado

## Contexto
Al extraer la tabla `cuenta` a `ukun-auth` (servicio de identidad), la lectura literal del `REFACTOR-PLAN.md` original ("extraer la tabla cuenta") sugería mover todos sus campos —correo, contraseña, nombre, apellido— al nuevo servicio. `cuenta` hoy es la tabla de identidad base de la que `cliente` y `empleado` heredan la PK vía `@MapsId`.

## Decisión
`ukun-auth` guarda únicamente `account(id, email, password_hash, enabled, ...)` y `account_role`. **`nombre` y `apellido` se quedan en `ukun-api`**, como columnas de `cliente`/`empleado`, junto con una proyección de lectura de `email`. Detalle completo del diseño, incluida la saga de signup que escribe en ambos servicios, en `docs/08-identidad-y-seguridad.md`.

## Consecuencias
- Editar el perfil (`PUT /api/me/profile`: nombre, apellido, dirección, tarjeta, tienda favorita) sigue siendo **una única transacción local** en `ukun_app`. Si nombre/apellido vivieran en auth, cada edición de perfil implicaría una escritura distribuida en dos bases por un dato que no es una credencial.
- Solo el correo y la contraseña —las credenciales reales— cruzan la frontera de servicios en operación normal (login, cambio de correo, cambio de contraseña).
- La proyección de `email` en `ukun-api` puede quedar desactualizada hasta el siguiente login del propio usuario tras un cambio de correo. Aceptado explícitamente: el volumen (empleados por tienda, clientes) hace este coste despreciable frente a la complejidad de mantenerlo sincronizado en tiempo real.

## Alternativas descartadas
- **Mover nombre/apellido a `ukun-auth` junto con el resto de `cuenta`**: técnicamente más "puro" (auth como única fuente de identidad completa), pero convierte cada edición de perfil en una escritura distribuida sin necesidad real, ya que nombre/apellido no son datos de autenticación.
