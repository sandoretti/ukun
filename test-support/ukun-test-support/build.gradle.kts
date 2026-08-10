// Lo que los tres servicios backend comparten al testear. NO es un servicio: no lleva el plugin de
// Spring Boot, no produce un bootJar y no se empaqueta en ninguna imagen — solo aparece en el
// classpath de `testImplementation` de quien la consume.
//
// Existe porque las siete reglas de docs/03-analisis-y-patrones.md §3 valen igual para ukun-api que
// para ukun-auth, y vivían en las fuentes de test del primero. Duplicarlas para el segundo era
// garantizar que divergieran. Ese mismo argumento es el que trajo después a `tracing/Traceparent`
// (v0.5): el formato de la cabecera W3C tiene que ser el mismo en los tres servicios o sus tests
// dejan de hablar del mismo hecho.
//
// El criterio para entrar aquí no ha cambiado: sirve a más de un servicio y no contiene nada propio
// de ninguno. Un fixture con nombres de base de datos o de eventos de un servicio se queda en él —
// por eso AbstractIntegrationTest sigue duplicado y documenta por qué.
//
// Aplica `ukun-java-conventions` y eso NO es opcional: sus flags -parameters e isDebug son los que
// dejan la LocalVariableTable en el bytecode, y sin ella EntityLoggingCondition no puede leer el
// tipo de un ALOAD, así que la regla que prohíbe pasar una @Entity a log.*() se queda ciega sin
// fallar. El convention plugin también fija el toolchain 25, que java.lang.classfile exige.

plugins {
    id("ukun-java-conventions")
    // `java-library` y no solo `java`: es lo que habilita la configuración `api`, y sin ella ArchUnit
    // no se propagaría al classpath de compilación de los ArchitectureTest de cada servicio.
    `java-library`
}

// Este proyecto no tiene ni tendrá tests de integración: sus reglas son análisis de bytecode puro,
// sin contexto de Spring y sin contenedor. La tarea `integrationTest` del convention plugin filtra
// por `*IT` y falla si no encuentra ninguno, que es la protección correcta en los tres servicios —
// ahí "cero tests de integración" es un síntoma— y ruido aquí.
//
// Se relaja el filtro en vez de deshabilitar la tarea, para que un *IT que alguien añada algún día
// sí se ejecute en lugar de quedarse mudo.
tasks.named<Test>("integrationTest") {
    filter.isFailOnNoMatchingTests = false
}

dependencies {
    implementation(platform(libs.spring.boot.bom))

    // `api` y no `implementation`: quien consume este proyecto escribe su propio ArchitectureTest,
    // que necesita ClassFileImporter y JavaClasses en su classpath de compilación.
    api(libs.archunit)

    // Las reglas hablan de @Entity (jakarta.persistence) y de la jerarquía de SLF4J. Ninguna de las
    // dos se propaga a los servicios: ambos ya las tienen por sus propios starters.
    implementation(libs.jakarta.persistence.api)
    implementation(libs.slf4j.api)

    // Solo para el self-test y sus fixtures. violations/LombokEntity necesita Lombok de verdad: es
    // una @Entity con @Data escrita a propósito para que la regla la cace, y lo que la hace visible
    // en bytecode es el @lombok.Generated que pide el lombok.config de este directorio.
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.web)
    testImplementation(libs.spring.context)
    testImplementation(libs.spring.data.commons)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testRuntimeOnly(libs.junit.platform.launcher)
}
