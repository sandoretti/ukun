// Compila los convention plugins de src/main/kotlin. Gradle construye este proyecto antes que
// cualquier otro, así que todo lo que se declare aquí está disponible en los build.gradle.kts de los
// tres servicios.

plugins {
    `kotlin-dsl`
}

dependencies {
    // Un convention plugin solo puede aplicar plugins que estén en su propio classpath de
    // compilación. `test-logger` se aplica desde ukun-java-conventions, así que entra aquí como
    // dependencia de implementación usando el marcador que publica el portal de plugins.
    implementation(libs.plugins.test.logger.toDependencyNotation())
}

/**
 * Convierte una entrada `[plugins]` del catálogo en las coordenadas de su artefacto marcador
 * (`<id>:<id>.gradle.plugin:<version>`), que es la única forma de meter un plugin en el classpath de
 * buildSrc sin repetir su versión a mano.
 */
fun Provider<PluginDependency>.toDependencyNotation(): String = get().let {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}"
}
