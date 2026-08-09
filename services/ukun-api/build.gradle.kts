// Monolito de la API de dominio. Lleva los diez módulos de aplicación de Spring Modulith del
// ADR-0017 §1 y el ModulithTest que verifica su grafo de dependencias en cada build.
//
// Ninguna dependencia declara versión: las gestionan los tres BOM importados abajo o el catálogo
// (gradle/libs.versions.toml). Es el único servicio que usa Lombok y MapStruct, así que su cadena de
// procesadores de anotaciones y los flags de MapStruct viven aquí y no en el convention plugin.

plugins {
    id("ukun-java-conventions")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.modulith.bom))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)

    // Liquibase es el dueño del esquema de ukun_app (skill `liquibase`, regla 3): Hibernate no lo
    // crea nunca, `ddl-auto: validate` solo lo comprueba.
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.liquibase)
    runtimeOnly(libs.postgresql)

    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Módulos de aplicación (ADR-0017, docs/14-modules.md).
    implementation(libs.spring.modulith.starter.core)
    runtimeOnly(libs.spring.modulith.actuator)

    compileOnly(libs.lombok)
    implementation(libs.mapstruct)

    // EL ORDEN DE ESTOS CUATRO ES LOAD-BEARING Y NO ES INTERCAMBIABLE.
    //
    // Gradle construye el -processorpath en el orden en que se declaran aquí. Lombok tiene que correr
    // primero: si MapStruct lee la entidad antes de que existan los getters/setters, genera un mapper
    // vacío en silencio. `lombok-mapstruct-binding` es lo que hace cooperar a los dos procesadores, y
    // el de configuración va último porque necesita leer los accessors que Lombok ya generó sobre
    // CatalogProperties.
    //
    // Quien lo rompa lo sabrá por StackValidationIT.mapstructReadsLombokAccessorsAndIsASpringBean,
    // que existe precisamente para eso. Ver docs/05-stack-y-versiones.md (fila de MapStruct).
    //
    // El BOM se importa otra vez aquí: `annotationProcessor` no extiende a `implementation`, así que
    // no hereda su gestión de versiones y sin esto spring-boot-configuration-processor se queda sin
    // versión. Es una diferencia real con Maven, donde <dependencyManagement> alcanzaba también a
    // annotationProcessorPaths.
    annotationProcessor(platform(libs.spring.boot.bom))
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.lombok.mapstruct.binding)
    annotationProcessor(libs.mapstruct.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.resttestclient)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.spring.modulith.docs)

    // Las siete reglas de docs/03 §3 y su self-test viven en :ukun-test-support, compartidas con
    // ukun-auth. Lo único que se queda aquí es ArchitectureTest, que las aplica nombrando el paquete
    // de producción de este servicio. Arrastra ArchUnit de forma transitiva (`api`).
    testImplementation(project(":ukun-test-support"))

    // El driver es `runtimeOnly` en producción, pero SchemaBaselineIT importa org.postgresql.Driver
    // para registrarlo a mano. Hace falta declararlo otra vez: el scope `runtime` de Maven sí estaba
    // en el classpath de compilación de los tests, `runtimeOnly` de Gradle no.
    testImplementation(libs.postgresql)

    // Los tests también llevan mappers (ProbeMapper), así que necesitan la misma cadena y en el mismo
    // orden. Gradle no propaga `annotationProcessor` a `testAnnotationProcessor`, a diferencia de la
    // única `annotationProcessorPaths` que Maven aplicaba a compile y a testCompile.
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testAnnotationProcessor(libs.lombok.mapstruct.binding)
    testAnnotationProcessor(libs.mapstruct.processor)
}

// Configuración de MapStruct. Los mappers declaran `@Mapper` desnudo, sin `componentModel`, así que
// el @Component y la inyección por constructor del *MapperImpl generado salen SOLO de aquí: sin estos
// flags el contexto de Spring no arranca por falta de bean, no falla al compilar.
//
// unmappedTargetPolicy=ERROR es la regla dura de docs/03-analisis-y-patrones.md §3: un campo del DTO
// sin origen rompe el build en vez de quedarse nulo en producción.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(
        listOf(
            "-Amapstruct.unmappedTargetPolicy=ERROR",
            "-Amapstruct.defaultComponentModel=spring",
            "-Amapstruct.defaultInjectionStrategy=constructor",
        )
    )
}
