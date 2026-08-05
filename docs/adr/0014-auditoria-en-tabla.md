# ADR-0014: Rastro de auditoría en tabla propia, log de aplicación a stdout

## Estado
Aceptado

## Contexto
El requisito no funcional de observabilidad (`02-requisitos.md` §4) pide registrar las acciones que afectan al usuario, sin cobertura total. Hacían falta dos decisiones distintas: dónde vive el rastro de "quién hizo qué a qué recurso" (auditoría, consultada por recurso, no puede perderse) frente al log de diagnóstico (consultado por `traceId`, puede perderse sin consecuencias). Se evaluó si ambos debían ser el mismo mecanismo.

## Decisión
Se separan. El log de aplicación va a **stdout en JSON estructurado** (formato nativo de Spring Boot, ver `11-observabilidad-y-logging.md` §5) y no se persiste desde la propia aplicación. El rastro de auditoría —solo para los eventos donde alguien modifica un recurso ajeno o reclamable por otro después: stock, productos, altas de empleado, signup completado, cambios de perfil/email— se escribe en una tabla `audit_log` dentro de `ukun_app` (nombre en inglés por ADR-0018), en la misma transacción que el cambio que audita, con `trace_id` como puente hacia el log completo de esa petición.

## Consecuencias
- La consulta "todo lo que le ha pasado a este producto" es un `SELECT` indexado (`ix_audit_log_resource`), sin agregador externo.
- La auditoría es transaccionalmente consistente con el cambio: si el cambio hace rollback, la fila de auditoría no se escribe.
- No se introduce ninguna pieza de infraestructura nueva (Loki, ELK, Kafka) para un volumen de unos pocos miles de eventos al año — coherente con el resto de sobreingeniería descartada en `03-analisis-y-patrones.md` §4.
- La tabla no tiene purga automática por diseño; si algún día se acerca a ~1M de filas, la respuesta es particionar por fecha, no borrar — borrar auditoría la inutiliza para su único propósito.
- Los eventos de autenticación (login, logout, emisión de token) quedan fuera de la tabla de auditoría a propósito: viven en `ukun-auth`, que `08-identidad-y-seguridad.md` §2 define como deliberadamente anémico, y su pregunta característica ("¿hay fuerza bruta?") se responde contando eventos en el log, no leyendo filas de una tabla.

## Alternativas descartadas
- **Hibernate Envers**: versiona entidades completas (una tabla `_AUD` por entidad auditada más `REVINFO`), no acciones de negocio — no distingue "un empleado editó el stock" de "el checkout descontó stock durante un pedido", que es la distinción que de verdad interesa auditar. Añadiría una tabla espejo por cada entidad auditada para responder una pregunta que aquí solo se plantea sobre cuatro tipos de recurso.
- **Agregador de logs (Loki/ELK) como único mecanismo, sin tabla**: resolvería la consulta por recurso solo si existiera ya esa infraestructura; introducirla únicamente para esto es coste operativo desproporcionado al volumen real, igual que se descartó Redis para caché de catálogo.
