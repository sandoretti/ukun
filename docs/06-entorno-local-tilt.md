# Entorno de desarrollo local con Tilt

> **Fuente de verdad de:** cómo se levanta y se trabaja en el entorno local, y qué corrige el Dockerfile de producción respecto al legacy.
> **Léelo si:** vas a programar y necesitas el stack corriendo, o vas a añadir un servicio nuevo al `Tiltfile`.
> **No decide:** cómo se escribe el `Tiltfile` línea a línea (skill `tilt-dev`), ni versiones (`05-stack-y-versiones.md`).

Tilt orquesta el stack de desarrollo vía `docker_compose()` (ADR-0005), con reinicio de los servicios Java en segundos en vez de un rebuild completo de imagen cada vez que cambias una línea.

## Qué compone el stack

| Recurso | Qué es | Puerto | Desde |
|---|---|:-:|:-:|
| `db` | Postgres 17 con las dos bases: `ukun_app` y `ukun_auth` | 5432 | v0.1 |
| `ukun-api` | monolito de dominio (resource server) | 8081 · JDWP 5005 | v0.2 |
| `ukun-auth` | Authorization Server | 9000 · JDWP 5006 | v0.4 |
| `ukun-gateway` | BFF + único punto de entrada | 8080 · JDWP 5007 | v0.5 |
| `ukun-web` | SPA con Vite en modo dev (HMR) | 5173 | v0.1 |

Los monolitos legacy (`ukun-clientes`, `ukun-empleados`, `nginx`) **no forman parte de este stack**: no están en servicio y no se levantan. Sus `docker-compose*.yml` siguen en el repo como referencia hasta que se borran (`04-roadmap.md` v0.7).

## Instalación (una vez, Fedora, sin root)

```bash
curl -fsSL https://raw.githubusercontent.com/tilt-dev/tilt/master/scripts/install.sh | bash
```

Se instala en `~/.local/bin`, que ya está en tu `PATH`. Verifica con `tilt version`.

## Requisitos previos

- Docker corriendo.
- `.env` en la raíz del repo con las credenciales de Postgres (usa `.env.example` como plantilla).
- JDK, Maven y Node **no hacen falta en el host**: la compilación incremental ocurre en contenedores auxiliares del propio `Tiltfile`, así el entorno es reproducible sin depender de qué tengas instalado localmente.

## Comandos

```bash
tilt up      # levanta todo el stack y abre la UI en localhost:10350
tilt down    # para y elimina los contenedores (no borra el volumen de datos)
```

Al ejecutar `tilt up` se abre `http://localhost:10350`: cada servicio aparece como un recurso con su log en vivo, su estado (verde = arriba, amarillo = construyendo, rojo = error) y sus etiquetas (`build`, `ops`).

Con el stack arriba, `http://localhost:8080` sirve la aplicación completa a través del gateway. Las cuentas de prueba las siembra el changelog de Liquibase con `context: demo` (skill `liquibase`), ya con contraseña hasheada — nunca en texto plano, a diferencia del `database/init.sql` legacy.

## Cómo funciona el live update de los servicios Java

No usa `spring-boot-devtools` a propósito: su `RestartClassLoader` provoca fallos sutiles con proxies de Hibernate e `instanceof` entre classloaders — justo el tipo de ruido que arruina una sesión de depuración. En su lugar:

1. Guardas un fichero en `services/<servicio>/src/`.
2. Tilt dispara `./mvnw -q -o -T1C -DskipTests compile dependency:copy-dependencies` en un contenedor de build.
3. Sincroniza `target/classes` y `target/dependency` dentro del contenedor de la app (`sync`).
4. Reinicia el proceso Java dentro del **mismo contenedor** (`restart_container()`) — no reconstruye la imagen.

Ciclo completo: ~2-4 segundos. Si cambias `pom.xml`, Tilt fuerza una reconstrucción completa de la imagen en vez de un sync (`fall_back_on`), porque un cambio de dependencias no se puede sincronizar en caliente.

Para `ukun-web` el `live_update` es solo `sync` **sin** `restart_container()`: Vite hace HMR real (~150ms) y reiniciar el proceso sería más lento que dejarlo recargar solo.

## Depuración remota

Cada servicio Java expone su puerto JDWP (ver tabla de arriba) — conéctate desde tu IDE como *Remote JVM Debug*. Sobrevive a `restart_container()`: el IDE reconecta solo.

## Resetear la base de datos

Botón **`db-reset`** en la UI de Tilt (etiqueta `ops`, es manual — no se ejecuta solo). Borra el volumen de Postgres; el siguiente arranque recrea el esquema con el baseline de Liquibase y los datos demo en unos segundos. Úsalo sin miedo cuando quieras partir de datos limpios.

## Imágenes de producción

Las imágenes de desarrollo (`Dockerfile.dev`, con `live_update`) son distintas de las que se despliegan de verdad — este apartado es sobre las segundas, entregable de v0.1 (`04-roadmap.md`). Cinco correcciones sobre el `Dockerfile` legacy, cada una con su motivo:

1. **`ENTRYPOINT` con nombre de jar fijo.** El legacy usa `java -jar /opt/app/*.jar` en exec form, que no expande el glob — funciona por accidente porque el jar resultante se llama literalmente `*.jar` (defecto #16). Se fija con `<finalName>` en el POM y `ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]`.
2. **Jar en capas** (`java -Djarmode=tools -jar app.jar extract --layers` o el equivalente Maven): dependencias, loader, snapshots y clases de aplicación en capas Docker separadas, para que un cambio de código no invalide la capa (pesada) de dependencias.
3. **Caché de BuildKit para `~/.m2`** (`RUN --mount=type=cache,target=/root/.m2 ./mvnw ...`) en vez de `dependency:go-offline`, que vuelve a descargar todo el árbol de dependencias en cuanto cambia una línea del `pom.xml`.
4. **Usuario no root**: `USER` explícito sobre una base `-jre-jammy` o `-jre-alpine`, en vez de ejecutar el proceso Java como root por omisión.
5. **`HEALTHCHECK`** contra `/actuator/health` y un `.dockerignore` por servicio.

Se descarta explícitamente CDS/AOT (Class Data Sharing / `native-image`): reducen el tiempo de arranque a cambio de bastante complejidad de build, y el arranque no es un problema real de este proyecto — ningún requisito de `02-requisitos.md` lo pide.

## Cómo probar el API

- **springdoc con Swagger UI** en `ukun-api`, activo solo en el perfil de desarrollo. Matiz importante: como el contrato es design-first (`04-roadmap.md`), **springdoc no es la fuente de verdad** — el YAML versionado en `docs/api/openapi/` lo es. Springdoc sirve para explorar y probar a mano, y es el productor del `/v3/api-docs` que el check de deriva de CI compara contra ese YAML.
- **Colección Bruno versionada en `docs/api/bruno/`**, no Postman: son ficheros de texto que se revisan en un diff normal de git y no exigen cuenta ni sincronización en la nube — coherente con que el contrato también viva como texto versionado. Con un entorno `local` (contra `tilt up`) y uno `mocks` (contra MSW).
- Un `local_resource` de UI (botón en `localhost:10350`) que abre Swagger UI, siguiendo el mismo patrón manual que `db-reset` — ver skill `tilt-dev`.

## Qué hacer si algo no va

- **Un recurso se queda rojo**: mira su log en la UI; casi siempre es un fallo de compilación Maven o la base de datos aún no está lista (el `resource_deps` del `Tiltfile` ya ordena el arranque, pero si acabas de hacer `db-reset` dale unos segundos).
- **Un cambio de código no se refleja**: comprueba que el fichero está dentro de `src/` (el `sync` solo vigila esa carpeta) y que no has tocado `pom.xml` a la vez sin dejar que termine el rebuild completo.
- **Puertos ocupados**: si tenías un `docker compose up` corriendo por fuera de Tilt, páralo primero (`docker compose down`).
- **Dos stacks a la vez** (dos worktrees en paralelo): usa la skill `worktree`, que reasigna los puertos del segundo stack en vez de dejar que colisionen.
