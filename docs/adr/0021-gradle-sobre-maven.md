# ADR-0021: Gradle sobre Maven, y compilación en el host para el ciclo de Tilt

## Estado
Aceptado

## Contexto
El ADR-0001 decidió un agregador único para los tres servicios nuevos y lo materializó en un `pom.xml` de 273 líneas. La decisión de *agregar* sigue siendo buena; la herramienta se ha quedado corta en dos frentes concretos:

1. **El agregador tenía que reimplementar a mano todo lo que un padre habría dado gratis.** Al exigir que los BOM de plataforma se *importen* en `<dependencyManagement>` y nunca se hereden como `<parent>`, el POM se quedaba sin `spring-boot-starter-parent`, y con él sin la configuración del compilador, sin la ejecución de `repackage` y sin el cableado de surefire/failsafe. Los cuatro había que declararlos y versionarlos a mano.
2. **El ciclo de test y de recompilación no tenía forma de ser incremental.** Maven no cachea entre invocaciones: cada `compile` reevalúa el módulo completo, y cada tanda de tests arranca una JVM nueva. Es lo que se paga en cada iteración del live update de Tilt y en cada ejecución de los `*Test`.

Hay además un detalle que este repo arrastraba sin dueño: la decisión de "no heredar de `spring-boot-starter-parent`" se atribuía al ADR-0001 pero no estaba escrita en él — vivía solo en un comentario de cabecera del `pom.xml`, que es justo el fichero que esta migración borra.

## Decisión
Los tres servicios de `services/` pasan a **Gradle 9.7.0 con Kotlin DSL**, wrapper versionado y checksum de la distribución fijado.

- **La agregación del ADR-0001 no cambia.** `settings.gradle.kts` declara los tres proyectos y sigue sin haber módulo de código compartido.
- **Los BOM se siguen importando, no heredando**, vía `implementation(platform(...))`. La distinción que el ADR-0001 quería proteger desaparece por construcción: en Gradle no existe la herencia de `<parent>` que había que evitar, así que ya no hay nada que un futuro colaborador pueda "arreglar" rompiéndolo. El grafo resuelto se comprobó idéntico al de Maven: 117 dependencias, las mismas versiones.
- **Las versiones viven en `gradle/libs.versions.toml`**, un único sitio. Desaparece la duplicación que Maven obligaba a mantener (`lombok.version` repetido a mano para poder usarlo en `annotationProcessorPaths`, porque un BOM importado no aporta sus propiedades).
- **Lo compartido entre servicios vive en un convention plugin** (`buildSrc/src/main/kotlin/ukun-java-conventions.gradle.kts`) que cada servicio aplica explícitamente, en vez de un bloque `subprojects {}` que los configuraría desde fuera. Leer el `build.gradle.kts` de un servicio dice qué se le aplica.
- **La convención `*Test` / `*IT` se conserva** y los ficheros de test no se mueven: una tarea `test` filtra por nombre los unitarios y una tarea `integrationTest` los de integración, sobre el mismo source set. Es el reparto que hacían surefire y failsafe, sin adoptar el `src/integrationTest/java` que Gradle sugiere y que habría movido 36 ficheros sin ganar nada.

### El ciclo de Tilt se compila en el host
`docs/06-entorno-local-tilt.md` prometía que el host no necesitaba JDK ni Maven porque la compilación ocurría en contenedores auxiliares. **Esa promesa se rompe a propósito**: el `docker run --rm` del Tiltfile mataba el daemon en cada invocación, y sin daemon Gradle es más lento que el Maven que sustituye — se perdería justo lo que motivaba el cambio. El `Tiltfile` invoca `./gradlew` directamente.

Con ello desaparecen tres complicaciones que la variante en contenedor obligaba a arrastrar: el `--user "$(id -u):$(id -g)"` para que los directorios de salida no quedaran de root contra un daemon rootful, el bind mount de la caché (que no podía ser un volumen con nombre porque Docker lo crea `root:root`), y el `HOME` postizo para que el wrapper supiera dónde escribir.

El coste queda acotado por el toolchain de Gradle: el `languageVersion` está fijado a 25 y Gradle descarga el JDK si no lo encuentra, así que el requisito real del host es "un JDK que arranque Gradle", no "exactamente el JDK 25".

En los `Dockerfile` de producción el daemon sí se desactiva (`--no-daemon`): ese contenedor es de un solo uso.

## Consecuencias
- Un solo `./gradlew build` construye los tres servicios; `./gradlew test` e `integrationTest` reparten las dos tandas.
- **Requisito nuevo del entorno local: un JDK en el host.** Documentado en `docs/06-entorno-local-tilt.md`.
- Las versiones de los plugins del build pasan a estar documentadas en `docs/05-stack-y-versiones.md`, que antes no las cubría: vivían solo en el `pom.xml`.
- Dos diferencias semánticas de Maven a Gradle quedaron a la vista y hubo que declararlas explícitamente, porque en Maven eran implícitas:
  - `annotationProcessor` no hereda la gestión de versiones de `implementation`, mientras `<dependencyManagement>` sí alcanzaba a `annotationProcessorPaths`.
  - El scope `runtime` de Maven estaba en el classpath de compilación de los tests; `runtimeOnly` de Gradle no lo está.
- Una tercera diferencia cambió el contenido del artefacto de producción: Maven resolvía ArchUnit como `test` por declararlo a profundidad 1, lo que lo sacaba del classpath de runtime **incluido el de desarrollo** — donde `spring-modulith-actuator` lo necesita para servir `/actuator/modulith`. Con Gradle, ArchUnit queda en runtime porque es lo que `spring-modulith-core` declara. Se acepta el jar algo más pesado: el comportamiento anterior era un agujero latente, no una optimización.
- **La caché de configuración queda activada** (`org.gradle.configuration-cache=true`), verificada con un `clean build --no-build-cache` completo antes de encenderla: las dos tandas de test en verde y cero problemas reportados. No es un requisito del build — si una subida del plugin de Boot o de `test-logger` empieza a reportar problemas, se desactiva y se anota el motivo.
- `ukun-clientes` y `ukun-empleados` **siguen en Maven** con su propio `mvnw`, y el repo convive temporalmente con dos herramientas de build. Se resuelve solo cuando se borren en v0.6/v0.7 (`docs/04-roadmap.md`).

## Alternativas descartadas
- **Seguir en Maven y aceptar el ciclo actual.** Es la opción de menor riesgo, pero deja sin resolver el motivo del cambio: no hay forma de hacer incremental la recompilación ni de cachear entre invocaciones.
- **Compilar en un contenedor persistente con el daemon dentro**, invocado con `docker exec`. Conserva la promesa de no exigir nada en el host, pero añade un contenedor cuyo ciclo de vida hay que gestionar desde el `Tiltfile` y una caché que ya no se comparte con las compilaciones del host. Se descartó por complejidad frente a un requisito —tener un JDK— que el toolchain de Gradle ya sabe satisfacer solo.
- **Mover los tests de integración a `src/integrationTest/java`**, que es lo que Gradle sugiere con `JvmTestSuite`. Mueve 36 ficheros y cambia la convención de este repo (que distingue las tandas por el nombre de la clase, no por su ubicación) sin ganar nada a cambio.
- **Migrar también los dos monolitos legacy.** Trabajo de tirar sobre código que no está en servicio y que es la única especificación ejecutable que queda del negocio.
- **Un bloque `subprojects {}` en el build de la raíz** en vez de `buildSrc`. Replicaría la filosofía del POM actual —una sola pieza que leer— pero configura a un hijo desde fuera y pierde el autocompletado del IDE.
