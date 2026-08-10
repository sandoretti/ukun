# Plantilla del cuerpo de un PR

Cópiala, rellénala, **borra las secciones que no apliquen** y las líneas `<!-- … -->`. Los comentarios HTML no se ven en GitHub, pero dejarlos delata la plantilla sin aportar nada.

Obligatorias: el párrafo de apertura, la línea de referencia al issue, «Qué entra» y «Verificación». Las demás entran cuando hay algo que decir.

Ejemplos reales ya mergeados, por si hace falta calibrar el tono: **#95** (pantalla completa, con limpieza y pendiente visual), **#93** (siembra de datos), **#85** (dos endpoints en un PR), **#88** (fix acotado).

---

```markdown
<!-- Dos a cuatro líneas: qué entra y por qué ahora. Si el trabajo existe porque algo
     anterior lo dejó a medias o inalcanzable, dilo aquí: es el contexto que quien
     revisa no tiene. -->

Closes #N
<!-- `Refs #N` en su lugar si el issue lleva `revision-humana`, `sinc` o `contrato`,
     o si el usuario prefiere cerrarlo él tras revisar. Un solo issue por línea.
     Añade pista y milestone si ayuda: «— pista P3 Frontend, milestone v0.3 Catálogo REST». -->

## Qué entra

<!-- Viñetas de comportamiento, no de ficheros. Cada regla de negocio con su fuente
     entre paréntesis: RF-01, ADR-0013, defecto #12, `docs/10-modelo-de-datos.md` §4.
     Lo que se decidió NO hacer vale tanto como lo que se hizo. -->

- 
- 

## Decisiones que conviene mirar

<!-- Lo que un revisor aprobaría por inercia y no debería: una alternativa descartada,
     un caso de borde resuelto de una forma discutible, un valor por defecto elegido a mano.
     Si no hay ninguna, borra la sección — no la rellenes por completar. -->

- 

## Limpieza (sin cambio de comportamiento)

<!-- Sale tal cual de la skill `refactor`. Abre con la cifra que lo demuestra
     («los mismos 89 tests en verde y sin tocar ninguna aserción») y luego una viñeta
     por extracción: qué se sacó, desde cuántos sitios venía, y por qué vive donde vive.
     Ese «por qué vive ahí» es lo único que seguirá sirviendo dentro de tres meses. -->

- 

## Documentación

<!-- Qué documento o skill se actualiza y por qué. Si un ADR o una convención cambia,
     se propaga a `docs/` Y a la skill en este mismo PR (`docs/09` §7).
     Si no se toca nada de `docs/`, dilo explícitamente cuando el cambio pudiera
     hacer pensar lo contrario («RF-01 se cumple entero, no hace falta tocar nada»). -->

## Verificación

<!-- Solo lo ejecutado de verdad, con cifras. Nada de «se han probado los casos». -->

- Backend: `./gradlew :ukun-api:test :ukun-api:integrationTest` en verde — N tests, ArchUnit y `ApplicationModules.verify()` incluidos.
- Frontend: `npm run lint`, `npx tsc -b`, `npm run test` en verde en `apps/ukun-web` — **N tests, N ficheros**.
- Contra el stack real (`tilt up`): <qué se llamó y qué respondió>.

**Pendiente de revisión humana:** <lo que no se ha comprobado y la ruta o el caso concreto que hay que mirar. Borra la línea si no hay nada pendiente; no la dejes vacía.>

## Para quien revise

<!-- Por dónde empezar a leer, qué fichero concentra la decisión importante, y qué
     riesgo asumido conviene confirmar. En un PR con `revision-humana`, esta sección
     no es opcional: es el motivo de la etiqueta. -->

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```
