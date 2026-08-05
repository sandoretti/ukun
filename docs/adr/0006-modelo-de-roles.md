# ADR-0006: Cliente y empleado son roles mutuamente excluyentes

## Estado
Aceptado — pendiente de confirmación de negocio (ver "riesgo" más abajo)

## Contexto
El esquema actual permite, sin quererlo, que una misma persona sea cliente y empleado a la vez: `cliente` y `empleado` son tablas independientes que ambas cuelgan de `cuenta` por `@MapsId`. Ninguna de las dos aplicaciones actuales contempla ese caso en la práctica (no hay navegación ni lógica para "soy cliente y empleado"), pero tampoco lo impiden.

## Decisión
Se prohíbe explícitamente en el diseño nuevo: la saga de alta (signup de cliente, alta de empleado) valida que el email no tenga ya una cuenta con el otro rol antes de crear la reserva en `ukun-auth`. El array `roles` del JWT (`docs/08-identidad-y-seguridad.md`) se diseña asumiendo un único rol de negocio por cuenta (más el sub-flag `admin` para empleados).

## Consecuencias
- Simplifica el modelo de roles del JWT y la navegación tras login: no hay que decidir a qué "modo" entra un usuario con ambos roles.
- Simplifica la autorización de recurso: un `account_id` se resuelve sin ambigüedad a un `cliente` o a un `empleado`, nunca a ambos.

## Riesgo / decisión abierta
Esta restricción es una simplificación técnica, no un requisito de negocio confirmado. Si en algún momento aparece un caso real de negocio que lo necesite (p. ej. un empleado que también compra como cliente con la misma cuenta), esta ADR debe revisarse explícitamente antes de escribir el código que dependa de la exclusión mutua.
