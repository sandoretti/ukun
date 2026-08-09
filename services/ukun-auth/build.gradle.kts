// Servicio de identidad: Authorization Server de Spring Security 7 sobre su propia base, ukun_auth.
// Lleva los tres módulos de aplicación de ADR-0017 §4 (accounts, registration, oauth2) y su
// ModulithTest desde el primer commit con código.
//
// Ninguna dependencia declara versión: las gestionan los dos BOM importados abajo o el catálogo
// (gradle/libs.versions.toml). No importa el BOM de Spring Cloud, que no usa.

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

    // El Authorization Server dejó de ser un proyecto aparte y vive dentro de Spring Security 7
    // (docs/05-stack-y-versiones.md). El BOM de Boot 4.1 publica DOS coordenadas para esto:
    // `spring-boot-starter-oauth2-authorization-server` (nombre heredado) y la de aquí. Resueltos
    // los dos árboles, son idénticos salvo por el propio alias — el heredado no aporta nada más que
    // una capa que depende de este. Se usa el nombre nuevo, que es el que sigue la partición por
    // tecnología de Boot 4 (la misma razón por la que abajo va `spring-boot-liquibase`).
    implementation(libs.spring.boot.starter.security.oauth2.authorization.server)

    // Liquibase es el dueño del esquema de ukun_auth (skill `liquibase`, regla 3): Hibernate no lo
    // crea nunca, `ddl-auto: validate` solo lo comprueba. Y es el artefacto de Boot, no
    // `liquibase-core`: ese trae el motor pero no la autoconfiguración, así que el changelog no se
    // aplicaría y el servicio arrancaría contra una base vacía sin dar ningún error (regla 8).
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.liquibase)
    runtimeOnly(libs.postgresql)

    // Módulos de aplicación (ADR-0017 §4, docs/14-modules.md).
    implementation(libs.spring.modulith.starter.core)
    runtimeOnly(libs.spring.modulith.actuator)

    // A diferencia de ukun-api, aquí no hay MapStruct: este servicio es anémico (ADR-0004) y sus
    // DTOs son tres records que se construyen a mano. Lombok sí, porque la regla dura de CLAUDE.md
    // pide @Getter/@Setter con equals/hashCode escritos a mano en toda entidad — y sin la cadena de
    // cuatro procesadores que ukun-api necesita justamente por MapStruct.
    compileOnly(libs.lombok)
    annotationProcessor(platform(libs.spring.boot.bom))
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.resttestclient)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.spring.modulith.docs)

    // Las siete reglas de docs/03 §3, compartidas con ukun-api. Arrastra ArchUnit por `api`.
    testImplementation(project(":ukun-test-support"))

    // El driver es runtimeOnly en producción, pero SchemaBaselineIT registra org.postgresql.Driver a
    // mano. `runtimeOnly` de Gradle no llega al classpath de compilación de los tests, a diferencia
    // del scope `runtime` de Maven.
    testImplementation(libs.postgresql)
}
