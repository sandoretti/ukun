// Build de la raíz. Deliberadamente casi vacío: no produce ningún artefacto y no configura a sus
// hijos con un bloque `subprojects {}`.
//
// Todo lo compartido vive en buildSrc/src/main/kotlin/ukun-java-conventions.gradle.kts, que cada
// servicio aplica explícitamente. Un `subprojects {}` haría lo mismo con menos ficheros, pero
// configuraría a un hijo desde fuera —no se puede leer un build.gradle.kts de servicio y saber qué se
// le está aplicando— y perdería el autocompletado del IDE. Ver ADR-0021.
//
// El plugin de Boot se declara sin aplicar para que su versión salga del catálogo una sola vez y los
// tres servicios lo activen con `alias(...)` sin repetirla.

plugins {
    alias(libs.plugins.spring.boot) apply false
}

// Los legacy (ukun-clientes, ukun-empleados) no son subproyectos de este build: siguen en Maven con su
// propio mvnw hasta que se borren en v0.6/v0.7 (docs/04-roadmap.md, ADR-0001).
