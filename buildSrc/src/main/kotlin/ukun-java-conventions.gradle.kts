// Convenciones comunes a los tres servicios Java. Sustituye al <build> del pom.xml agregador
// (ADR-0021), y como aquel, es el único sitio donde se decide cómo se compila y cómo se testea.
//
// Aquí NO hay ni una coordenada de dependencia a propósito: cada servicio declara las suyas en su
// propio build.gradle.kts contra gradle/libs.versions.toml. Eso evita el problema que el POM sí
// tenía —repetir una versión para poder usarla en dos sitios— y deja que ukun-auth no arrastre los
// BOM de Cloud y Modulith, que no usa.

plugins {
    java
    jacoco
    id("com.adarshr.test-logger")
}

java {
    // El toolchain es una mejora real sobre el `<release>25` de Maven: fija la versión del compilador
    // y de la JVM de test sin depender del JAVA_HOME del host, y Gradle descarga el JDK si no lo
    // encuentra. Es lo que sostiene la decisión de compilar en el host (ADR-0021).
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"

    // Los dos flags de abajo son load-bearing, no estilo:
    //
    // -parameters conserva los nombres de los parámetros en el bytecode. El plugin de Spring Boot ya
    // lo añade, pero se declara aquí porque hay tests que dependen de él y no de que un plugin de
    // terceros siga haciéndolo.
    //
    // La información de depuración (activada por defecto, explícita aquí por lo mismo) es la que
    // permite a archunit/Bytecode.localVariableTypes leer la LocalVariableTable: sin ella un ALOAD no
    // lleva tipo y la regla que prohíbe pasar una @Entity a log.*() se queda ciega.
    options.compilerArgs.add("-parameters")
    options.isDebug = true
}

// *Test -> unitario, *IT -> integración con Testcontainers. El reparto es el mismo que hacían
// surefire y failsafe, y por el mismo motivo: los unitarios corren en cada build, los de integración
// levantan contenedores (docs/13-testing.md §3). Las dos tandas comparten src/test/java; los ficheros
// no se mueven a un source set aparte.
tasks.withType<Test>().configureEach {
    // Sin esto Gradle no ejecutaría ni un test: no autodetecta el motor como hacía surefire.
    useJUnitPlatform()

    // La tanda de tests corre SIN el perfil `dev` —el documento por defecto de application.yaml es el
    // de producción—, así que sin esta línea la salida de cada test saldría en JSON ECS y depurar un
    // fallo pasaría por leer un objeto JSON por línea.
    //
    // La cadena vacía no es un truco: DefaultLogbackConfiguration elige entre PatternLayoutEncoder y
    // StructuredLogEncoder con StringUtils.hasLength(formato), así que "" es exactamente "no
    // estructurado". Es el mismo mecanismo con el que el documento `dev` recupera el patrón legible.
    //
    // Lo que se pierde con ello es la comprobación de que la capa 3 de docs/11 §4 está bien cableada,
    // y por eso existe EcsMaskedLoggingIT: se enciende el formato a sí mismo.
    systemProperty("logging.structured.format.console", "")
}

tasks.test {
    description = "Ejecuta los tests unitarios (*Test). Sin contenedores."
    filter {
        includeTestsMatching("*Test")
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Ejecuta los tests de integración (*IT). Necesita un daemon de Docker."
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    // Reutiliza el source set `test` en vez de crear src/integrationTest/java: la convención de este
    // repo distingue las dos tandas por el nombre de la clase, no por su ubicación.
    val testSourceSet = sourceSets.test.get()
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath

    filter {
        includeTestsMatching("*IT")
    }

    // Los unitarios primero: si una regla de ArchUnit está roja, no hace falta esperar a que
    // arranque Postgres para saberlo.
    shouldRunAfter(tasks.test)
}

// Un solo informe a partir de los .exec de todas las tandas, como el jacoco.exec compartido que
// producía Maven. Se leen como fichero y no como dependencia de tarea para no forzar que las dos
// tandas corran siempre juntas.
val coverageData = fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") }

// Cobertura informativa en todo el backend, sin umbral global — invita a tests basura rellenando
// líneas (docs/03-analisis-y-patrones.md §3).
tasks.jacocoTestReport {
    executionData.setFrom(coverageData)
    mustRunAfter(tasks.withType<Test>())
    reports {
        html.required = true
        xml.required = true
    }
}

// El umbral obligatorio existe, pero **qué** se mide lo declara cada servicio en su propio
// build.gradle.kts: sólo `ukun-api` tiene `orders` e `inventory`, que es donde docs/13-testing.md §3
// pone la barra. Aquí se decide únicamente que la verificación corra — un servicio sin reglas la pasa
// sin hacer nada, y así el día que gane un módulo con dinero dentro sólo tiene que añadir la regla,
// no acordarse también de engancharla a `check`.
tasks.jacocoTestCoverageVerification {
    executionData.setFrom(coverageData)
    mustRunAfter(tasks.withType<Test>())
}

tasks.check {
    dependsOn(integrationTest, tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification)
}

// Reemplazo de `dependency:copy-dependencies`: deja el classpath de runtime en build/dependency para
// que el live update de Tilt lo sincronice dentro del contenedor (docs/06-entorno-local-tilt.md).
// `Sync` y no `Copy`: al quitar una dependencia su jar tiene que desaparecer del directorio, o el
// contenedor seguiría arrancando con ella en el classpath.
tasks.register<Sync>("copyDeps") {
    description = "Copia el classpath de runtime a build/dependency para el live update de Tilt."
    group = LifecycleBasePlugin.BUILD_GROUP

    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("dependency"))
}

// Nombre de jar fijo, heredado de <finalName>app</finalName>. Es lo que hace posible el ENTRYPOINT
// literal de los Dockerfile de producción: el legacy usa `java -jar /opt/app/*.jar` en exec form,
// donde ningún shell expande el glob (defecto #16, docs/03-analisis-y-patrones.md §1).
//
// Va detrás de un `withId` porque el plugin de Boot lo aplica cada servicio, no este convention
// plugin: así los tres declaran explícitamente que producen un ejecutable.
// Se tipa como `Jar` y no como `BootJar` para no tener que meter el plugin de Boot en el classpath de
// buildSrc: BootJar hereda de Jar y `archiveFileName` está en la clase padre.
plugins.withId("org.springframework.boot") {
    tasks.named<Jar>("bootJar") {
        archiveFileName = "app.jar"
    }
}
