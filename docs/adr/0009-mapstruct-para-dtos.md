# ADR-0009: MapStruct para el mapeo entidad↔DTO, revirtiendo la decisión de fábricas estáticas

## Estado
Aceptado — revierte una decisión previamente aceptada de este mismo repositorio

## Contexto
`03-analisis-y-patrones.md` había fijado, con motivo explícito, el mapeo entidad→DTO mediante fábricas estáticas en el propio record (`ProductoResponse.from(Producto p)`), y descartaba MapStruct por sobreingeniería: con el tamaño de este dominio (~10 agregados, ~25 DTOs), el coste del procesador de anotaciones y de mappers generados que depurar superaba el beneficio, y la migración posterior a MapStruct se consideraba mecánica si los DTOs se multiplicaban en v1.x.

Esa premisa (~25 DTOs, dominio pequeño) **sigue siendo cierta** — este ADR no la contradice. La reversión es una decisión de estandarización: usar la herramienta que el ecosistema Spring emplea por defecto para este problema, en vez de un patrón manual propio, aunque el tamaño actual no lo exija todavía.

## Decisión
**MapStruct** sustituye a las fábricas estáticas como mecanismo de mapeo entidad↔DTO en `ukun-api` y `ukun-auth`, desde el primer commit de cada servicio:

- `componentModel = "spring"` — los mappers se inyectan como beans, no se invocan como métodos estáticos.
- Un mapper por agregado (`ProductoMapper`, `PedidoMapper`, ...), interfaz + implementación generada.
- `unmappedTargetPolicy = ERROR` en la configuración global: un campo nuevo en el DTO sin mapeo explícito rompe la compilación, no se descubre en producción.
- Sigue prohibido `ModelMapper`/`BeanUtils.copyProperties` — el argumento original contra el mapeo por reflexión no cambia.
- Los DTOs siguen siendo `record`; MapStruct soporta records como origen y destino de forma nativa desde su versión 1.5.

## Consecuencias
- Un mapper nuevo (interfaz vacía con `@Mapper`) por cada agregado que necesite DTOs, en vez de un método estático por DTO. Más ficheros, menos código repetido de asignación campo a campo.
- El procesador de anotaciones de MapStruct debe convivir con el de Lombok en el mismo módulo — orden de declaración en la configuración `annotationProcessor`, con Lombok primero. Es la trampa clásica de esta combinación, la vigila `StackValidationIT` y se deja documentada en `05-stack-y-versiones.md`.
- La verificación del "hola mundo" de v0.1 (Boot 4.1 + Lombok + Testcontainers + springdoc sobre JDK 25, ya prevista en `05-stack-y-versiones.md`) debe incluir MapStruct desde ese mismo día, no añadirse después.
- `03-analisis-y-patrones.md` se actualiza para reflejar esta decisión, con una nota explícita de que reemplaza a la sección anterior de fábricas estáticas.

## Alternativas descartadas
- **Mantener fábricas estáticas** (decisión previa, ADR implícito en `03-analisis-y-patrones.md`): sigue siendo válida en términos de coste para este tamaño de dominio. Se descarta aquí no porque el argumento fuera erróneo, sino por preferencia de usar una herramienta estándar del ecosistema en vez de un patrón manual mantenido a mano.
