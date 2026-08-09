# ADR-0001: Monorepo con build agregador, sin módulo de código compartido

## Estado
Aceptado. La herramienta de build que este ADR eligió (Maven) fue sustituida por Gradle en el **ADR-0021**; la decisión de *agregar* y de no tener módulo común sigue vigente y es lo que este documento decide.

## Contexto
Tres servicios Java nuevos (`ukun-api`, `ukun-auth`, `ukun-gateway`) deben compilar con exactamente la misma versión de Spring Boot, Spring Cloud, Java y plugins del build. El propio proyecto actual demuestra qué pasa sin ese control: `ukun-clientes` y `ukun-empleados` tienen builds independientes con las mismas dependencias declaradas dos veces, y sus entidades JPA —que mapean las mismas tablas— ya han divergido en fetch types y validaciones (ver `docs/03-analisis-y-patrones.md`).

## Decisión
Un único repositorio con un build agregador en la raíz que centraliza la gestión de dependencias (BOM de Spring Boot 4.1.0 + Spring Cloud 2025.1.2, **importados** y nunca heredados) y la configuración común de compilación y test, del que cuelgan `services/ukun-api`, `services/ukun-auth`, `services/ukun-gateway` como proyectos. **No** se crea un módulo `ukun-common` con entidades o código compartido: `ukun-auth` y `ukun-api` poseen tablas disjuntas y no comparten ni una entidad; lo único compartido es el contrato del token, que se duplica como unas pocas constantes de claim antes que acoplar la compilación de los servicios entre sí.

`ukun-clientes` y `ukun-empleados` (legacy, Boot 3.2.3) quedan **fuera** del agregador — homologarlos a Boot 4.1 sería trabajo de tirar, dado que se borran según el calendario de `docs/04-roadmap.md`. Siguen construyéndose con su propio Maven hasta entonces (ADR-0021).

## Consecuencias
- Un solo `./gradlew build` construye los tres servicios nuevos con versiones garantizadas idénticas.
- Actualizar una dependencia es un cambio en un solo lugar.
- Si en el futuro aparece una cuarta pieza compartida real entre servicios (no solo el contrato), se reevalúa esta decisión — no antes.

## Alternativas descartadas
- **Repos separados por servicio**: complica el desarrollo local (Tilt tendría que clonar N repos) y la revisión de cambios que cruzan servicios (p. ej. cambiar el contrato de `/internal/accounts`).
- **Módulo `ukun-common`**: es exactamente el patrón que ya ha producido la divergencia actual entre `ukun-clientes` y `ukun-empleados`, solo que a nivel de código en vez de a nivel de configuración de dependencias.
