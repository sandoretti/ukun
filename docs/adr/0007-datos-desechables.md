# ADR-0007: Los datos actuales son desechables — no hay migración de identidad

## Estado
Aceptado

## Contexto
Dos hechos, no uno:

1. La base de datos legacy contiene únicamente datos semilla de demostración: 12 empleados y 2 clientes, todos con contraseña `123456` en texto plano (`database/init.sql`).
2. **`ukun-clientes` y `ukun-empleados` no están en servicio.** No hay un despliegue vivo al que romperle el esquema, ni usuarios cuyas sesiones haya que preservar.

Al diseñar la extracción de `cuenta` a `ukun-auth` (ADR-0004, `docs/08-identidad-y-seguridad.md`) había que decidir si el proceso trata esos datos como algo que preservar (ensayo de migración contra un dump, ventana de mantenimiento, plan de rollback) o como algo desechable.

## Decisión
Los datos actuales son desechables y **el legacy no es un consumidor al que haya que mantener contento**. `ukun_auth` y `ukun_app` se crean de cero: `ukun-auth` se siembra con las mismas cuentas de demostración, ahora con contraseña hasheada (BCrypt), y `ukun_app` con `customer`/`employee` ya llevando `account_id`.

Este ADR es la premisa de la que cuelgan varias simplificaciones estructurales del plan, y por eso conviene tenerlo presente: si alguna vez deja de ser cierto, hay que revisar todas ellas a la vez.

## Consecuencias
- **No hay migración de identidad** en v0.4: sin ensayo contra dump, sin ventana de mantenimiento, sin plan de rollback de datos, sin coexistencia `cuenta`/`account`.
- **No hay esquema escalonado.** El baseline de Liquibase de cada base es completo y definitivo desde el primer changeset (`docs/10-modelo-de-datos.md` §8): ninguna restricción entra permisiva para endurecerse después, ninguna columna nace nullable "hasta que el legacy muera".
- **No hay renombrado.** El esquema nace en inglés (ADR-0018), sin changesets de `renameTable`/`renameColumn` ni mapeos JPA de traducción.
- **No hay estrategia de convivencia** en `docs/04-roadmap.md`: ni *strangler fig*, ni enrutado por path entre legacy y nuevo, ni feature flags de infraestructura. "Borrar el legacy" es limpieza de repositorio, no un hito de riesgo; el hito real es la paridad funcional.
- El diseño de la saga de signup (`docs/08-identidad-y-seguridad.md`) sigue siendo necesario igualmente — no es un mecanismo de migración, es cómo funciona el alta de cuentas en marcha una vez el sistema está partido en dos servicios.

## Riesgo / decisión a revisar si cambia el contexto
Si en algún momento el legacy vuelve a ponerse en servicio, o el sistema pasa a tener usuarios reales antes de completar la refactorización, **esta ADR debe revisarse y con ella las cuatro consecuencias de arriba**, no solo la primera. En ese escenario reaparecen: el procedimiento completo de migración (ensayo con dump ejecutado dos veces para verificar idempotencia, coexistencia `cuenta`/`account`, y decidir si se fuerza restablecimiento de contraseña a todos los usuarios, dado que las contraseñas actuales ya estuvieron expuestas en claro), el escalonado de restricciones compatibles hacia atrás, y una estrategia de corte de tráfico ruta a ruta.
