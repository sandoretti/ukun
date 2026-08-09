// buildSrc es un build propio y no hereda el `dependencyResolutionManagement` de la raíz, así que
// declara aquí de dónde salen sus propias dependencias (el plugin de Kotlin DSL y los plugins de
// terceros que el convention plugin aplica).

rootProject.name = "buildSrc"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    // El mismo catálogo que usa el build de arriba, para que las versiones de los plugins que se
    // resuelven aquí sigan viniendo de gradle/libs.versions.toml y no se dupliquen.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
