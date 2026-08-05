# ADR-0002: Frontend en react-bootstrap, no Tailwind

## Estado
Aceptado

## Contexto
El frontend actual (18 plantillas Thymeleaf) usa Bootstrap 5.3 vía CDN con overrides de CSS a mano. Al planificar el frontend React se evaluaron dos caminos: continuidad con Bootstrap (migración mecánica del markup existente) o un rediseño con Tailwind + primitivos de accesibilidad (Radix), que habría significado reescribir las 18 pantallas desde cero.

## Decisión
**react-bootstrap**, manteniendo Bootstrap 5.3 como sistema visual base. Se unifica en un único tema (hoy hay dos paletas verdes distintas entre `ukun-clientes` y `ukun-empleados`, ver `docs/07-frontend-ux-ui.md`), pero el markup y los componentes de cada pantalla se traducen desde las plantillas actuales en vez de diseñarse de cero.

## Consecuencias
- Menor riesgo de calendario en la pista de frontend (`04-roadmap.md`, P3): cada pantalla tiene un equivalente Thymeleaf del que partir.
- El objetivo de "buen UX/UI" se persigue dentro de este marco: tema unificado, componentes base consistentes, estados de carga/vacío/error — no mediante un sistema de diseño nuevo.
- Bootstrap se instala como dependencia npm versionada, no vía CDN — desaparece la dependencia de un recurso externo en tiempo de ejecución.

## Alternativas descartadas
- **Tailwind 4 + Radix**: mejor control de accesibilidad y diseño a medida, pero convierte el frontend en un proyecto de diseño desde cero en paralelo al refactor de arquitectura, con impacto de calendario no acotado. Queda como opción a revisar en una iteración de producto posterior a v1.0, no durante el refactor.
