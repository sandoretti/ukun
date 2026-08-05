# ADR-0001: Monorepo con POM agregador, sin módulo de código compartido

## Estado
Aceptado

## Contexto
Tres servicios Java nuevos (`ukun-api`, `ukun-auth`, `ukun-gateway`) deben compilar con exactamente la misma versión de Spring Boot, Spring Cloud, Java y plugins. El propio proyecto actual demuestra qué pasa sin ese control: `ukun-clientes` y `ukun-empleados` tienen POMs independientes con las mismas dependencias declaradas dos veces, y sus entidades JPA —que mapean las mismas tablas— ya han divergido en fetch types y validaciones (ver `docs/03-analisis-y-patrones.md`).

## Decisión
Un único repositorio con un `pom.xml` agregador en la raíz que centraliza `<dependencyManagement>` (BOM de Spring Boot 4.1.0 + Spring Cloud 2025.1.2) y `<pluginManagement>`, del que cuelgan `services/ukun-api`, `services/ukun-auth`, `services/ukun-gateway` como módulos. **No** se crea un módulo `ukun-common` con entidades o código compartido: `ukun-auth` y `ukun-api` poseen tablas disjuntas y no comparten ni una entidad; lo único compartido es el contrato del token, que se duplica como unas pocas constantes de claim antes que acoplar la compilación de los servicios entre sí.

`ukun-clientes` y `ukun-empleados` (legacy, Boot 3.2.3) quedan **fuera** del agregador — homologarlos a Boot 4.1 sería trabajo de tirar, dado que se borran según el calendario de `docs/04-roadmap.md`.

## Consecuencias
- Un solo `./mvnw install` construye los tres servicios nuevos con versiones garantizadas idénticas.
- Actualizar una dependencia es un cambio en un solo lugar.
- Si en el futuro aparece una cuarta pieza compartida real entre servicios (no solo el contrato), se reevalúa esta decisión — no antes.

## Alternativas descartadas
- **Repos separados por servicio**: complica el desarrollo local (Tilt tendría que clonar N repos) y la revisión de cambios que cruzan servicios (p. ej. cambiar el contrato de `/internal/accounts`).
- **Módulo `ukun-common`**: es exactamente el patrón que ya ha producido la divergencia actual entre `ukun-clientes` y `ukun-empleados`, solo que a nivel de código en vez de a nivel de configuración de dependencias.
