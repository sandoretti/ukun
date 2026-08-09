// Agregador de los tres servicios Java nuevos (ADR-0001, ADR-0021).
//
// Los módulos viven bajo services/ pero se registran con nombre plano y `projectDir` remapeado,
// así que sus rutas de tarea son `:ukun-api:bootJar` y no `:services:ukun-api:bootJar`. Eso es lo
// que invocan el Tiltfile y los Dockerfile de producción; el nombre del proyecto es también el del
// directorio, así que renombrarlo sin más rompería ambos.
//
// Los monolitos legacy (ukun-clientes, ukun-empleados) quedan FUERA a propósito: van en Boot 3.2.3
// sobre Java 17, siguen construyéndose con su propio Maven y se borran en v0.6/v0.7
// (docs/04-roadmap.md). Homologarlos sería trabajo de tirar.

rootProject.name = "ukun"

dependencyResolutionManagement {
    // Único sitio donde se declara de dónde salen las dependencias. Ningún build.gradle.kts abre
    // su propio bloque `repositories`.
    repositories {
        mavenCentral()
    }
}

include("ukun-api", "ukun-auth", "ukun-gateway")

project(":ukun-api").projectDir = file("services/ukun-api")
project(":ukun-auth").projectDir = file("services/ukun-auth")
project(":ukun-gateway").projectDir = file("services/ukun-gateway")
