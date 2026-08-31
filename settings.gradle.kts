// Agregador de los tres servicios Java nuevos (ADR-0001, ADR-0021).
//
// Los módulos viven bajo services/ pero se registran con nombre plano y `projectDir` remapeado,
// así que sus rutas de tarea son `:ukun-api:bootJar` y no `:services:ukun-api:bootJar`. Eso es lo
// que invocan el Tiltfile y los Dockerfile de producción; el nombre del proyecto es también el del
// directorio, así que renombrarlo sin más rompería ambos.
//
// Los dos monolitos legacy quedaron siempre FUERA de este build, y nunca se homologaron: iban en
// Boot 3.2.3 sobre Java 17 con su propio Maven, y homologarlos habría sido trabajo de tirar. Se
// borraron al declarar la paridad de su recorrido —ukun-clientes en v0.6, ukun-empleados en v0.7—,
// así que ya no hay un segundo build del que hablar (ADR-0021 conserva el porqué).

rootProject.name = "ukun"

dependencyResolutionManagement {
    // Único sitio donde se declara de dónde salen las dependencias. Ningún build.gradle.kts abre
    // su propio bloque `repositories`.
    repositories {
        mavenCentral()
    }
}

// El cuarto proyecto NO es un servicio: :ukun-test-support lleva las siete reglas de ArchUnit de
// docs/03-analisis-y-patrones.md §3, que valen igual para ukun-api que para ukun-auth. No aplica el
// plugin de Boot, no produce jar ejecutable y nunca entra en una imagen — solo en el
// `testImplementation` de los servicios que las aplican. Vive fuera de services/ precisamente para
// que se lea como lo que es.
//
// OJO al añadir un proyecto aquí: los Dockerfile de producción de los tres servicios copian el
// build.gradle.kts de CADA proyecto declarado, porque Gradle tiene que poder configurarlos todos
// aunque solo construya uno. Un proyecto nuevo sin su COPY correspondiente rompe las tres imágenes
// de producción, y no se nota con `tilt up`, que usa los Dockerfile.dev.
include("ukun-api", "ukun-auth", "ukun-gateway", "ukun-test-support")

project(":ukun-api").projectDir = file("services/ukun-api")
project(":ukun-auth").projectDir = file("services/ukun-auth")
project(":ukun-gateway").projectDir = file("services/ukun-gateway")
project(":ukun-test-support").projectDir = file("test-support/ukun-test-support")
