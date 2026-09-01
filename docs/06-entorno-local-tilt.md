# Entorno de desarrollo local con Tilt

> **Fuente de verdad de:** cómo se levanta y se trabaja en el entorno local, y qué corrige el Dockerfile de producción respecto al legacy.
> **Léelo si:** vas a programar y necesitas el stack corriendo, o vas a añadir un servicio nuevo al `Tiltfile`.
> **No decide:** cómo se escribe el `Tiltfile` línea a línea (skill `tilt-dev`), ni versiones (`05-stack-y-versiones.md`).

Tilt orquesta el stack de desarrollo vía `docker_compose()` (ADR-0005), con reinicio de los servicios Java en segundos en vez de un rebuild completo de imagen cada vez que cambias una línea.

## Qué compone el stack

| Recurso | Qué es | Puerto | Variable | Desde |
|---|---|:-:|:-:|:-:|
| `db` | Postgres 17 con las tres bases: `ukun_app`, `ukun_auth` y `ukun_gateway` | 5432 | `DB_PORT` | v0.1 |
| `ukun-api` | monolito de dominio (resource server) | 8081 · JDWP 5005 | `API_PORT` · `JDWP_API_PORT` | v0.2 |
| `ukun-auth` | Authorization Server | 9000 · JDWP 5006 | `AUTH_PORT` · `JDWP_AUTH_PORT` | v0.4 |
| `ukun-gateway` | BFF + único punto de entrada | 8080 · JDWP 5007 | `GATEWAY_PORT` · `JDWP_GATEWAY_PORT` | v0.5 |
| `ukun-web` | SPA con Vite en modo dev (HMR) | 5173 | `WEB_PORT` | v0.1 |
| `otel-collector` | destino OTLP de trazas y métricas de los tres servicios Java | 4318 | `OTLP_PORT` | v1.0 |

La columna **Puerto** es el valor por defecto: son los que se usan si no defines nada, que es el
caso normal de un checkout único. Solo se tocan al levantar dos stacks a la vez (ver más abajo).

`ukun-api` monta además `resources/uploads/` del repo en `/opt/resources/uploads`, que es de donde el módulo `storage` sirve `GET /media/products/{key}` — las fotos de los productos demo. **Desde v0.7 el bind ya no es de solo lectura**: la subida de RF-10 (`PUT /api/admin/products/{id}/image`) escribe ahí, así que trabajar con el back-office deja fotos nuevas en tu copia del repo. Las nombra el servidor con un UUID y `.gitignore` las ignora por esa forma, de modo que no ensucian el `git status` ni tapan a las demo. Es un bind mount de `compose.yaml`, no del override de desarrollo, así que **cambiarlo obliga a recrear el contenedor**: el live update sincroniza clases y recursos, no volúmenes.

Ya no hay nada legacy con lo que convivir: `ukun-empleados`, `nginx/`, `database/` y los tres `docker-compose*.yml` se borraron en v0.7 al declarar la paridad del back-office (issue #50), como `ukun-clientes` en v0.6 (issue #40). Todo eso se lee en el tag `v0.0-legacy`. Este stack es el sistema nuevo entero y nada más.

## Instalación (una vez, Fedora, sin root)

```bash
curl -fsSL https://raw.githubusercontent.com/tilt-dev/tilt/master/scripts/install.sh | bash
```

Se instala en `~/.local/bin`, que ya está en tu `PATH`. Verifica con `tilt version`.

## Requisitos previos

- Docker corriendo.
- `.env` en la raíz del repo con las credenciales de Postgres (usa `.env.example` como plantilla).
- **Un JDK en el host.** Requisito desde ADR-0021: la compilación incremental de los servicios Java se hace con `./gradlew` en el host, no en un contenedor auxiliar, porque un contenedor efímero mata el daemon de Gradle en cada ciclo y sin daemon no hay ciclo rápido. Sirve cualquier JDK que arranque Gradle 9.7 (de 17 a 26); el toolchain se encarga de compilar con 25 aunque el tuyo sea otro, descargándolo si hace falta.
- Node **no hace falta en el host**: el frontend se construye dentro de su contenedor.

## Comandos

```bash
tilt up      # levanta todo el stack y abre la UI en localhost:10350
tilt down    # para y elimina los contenedores (no borra el volumen de datos)
```

Al ejecutar `tilt up` se abre `http://localhost:10350`: cada servicio aparece como un recurso con su log en vivo, su estado (verde = arriba, amarillo = construyendo, rojo = error) y sus etiquetas (`build`, `ops`).

Con el stack arriba, `http://localhost:8080` sirve la aplicación completa a través del gateway. Las cuentas de prueba las siembra el changelog de Liquibase con `context: demo` (skill `liquibase`), ya con contraseña hasheada — nunca en texto plano, a diferencia del `database/init.sql` legacy.

**Las 14 cuentas demo comparten contraseña: `ukun-demo-2026`.** En `ukun_auth` solo existe su hash de BCrypt; en claro aparece aquí y en `apps/ukun-web/e2e/fixtures/staff.ts`, que es donde los smokes de Playwright entran con las dos cuentas de trabajo. No es un secreto que proteger —siembra un stack local y no existe fuera de él—, pero si alguna vez lo fuera, esos son los dos sitios que hay que tocar. Una de cada rol, para entrar rápido:

| Rol | Correo |
|---|---|
| Cliente | `joel.sandoval@email.com` |
| Empleado | `laura.martinez@ukun.com` |
| Empleado + admin | `salvador.oton@ukun.com` |

Las otras once están en `services/ukun-auth/src/main/resources/db/changelog/demo/data/accounts.csv`, y sus `account_id` son los mismos que `ukun_app` guarda en `customer`/`employee` (`docs/08-identidad-y-seguridad.md` §1). Si cambias la contraseña, el hash tiene que salir del `PasswordEncoder` delegante del propio servicio: uno pegado de cualquier otro sitio no lo verifica nadie y el fallo se ve como «credenciales incorrectas».

## Cómo funciona el live update de los servicios Java

No usa `spring-boot-devtools` a propósito: su `RestartClassLoader` provoca fallos sutiles con proxies de Hibernate e `instanceof` entre classloaders — justo el tipo de ruido que arruina una sesión de depuración. En su lugar:

1. Guardas un fichero en `services/<servicio>/src/`.
2. Tilt dispara `./gradlew -q :<servicio>:classes :<servicio>:copyDeps` **en el host**. `classes` y no `build`: no se empaqueta ningún jar ni se corre ningún test. `copyDeps` deja el classpath de runtime en `build/dependency`, y es el reemplazo de lo que en Maven hacía `dependency:copy-dependencies`.
3. Sincroniza **tres** directorios dentro del contenedor de la app (`sync`): `build/classes/java/main`, `build/resources/main` y `build/dependency`. Son tres y no dos porque Gradle separa clases y recursos, mientras Maven mandaba ambos a `target/classes`; el de recursos no es opcional, `application.yaml` vive ahí.
4. Reinicia el proceso Java dentro del **mismo contenedor** (`restart_container()`) — no reconstruye la imagen.

El paso 2 tarda **~1 segundo** con el daemon caliente (medido sobre `ukun-api` tras tocar un controlador), al que hay que sumar el `sync` y el arranque de la JVM del paso 4. Es lo que se compraba renunciando a compilar en contenedor.

Si cambias un fichero de build —el `build.gradle.kts` del servicio, `gradle/libs.versions.toml`, `settings.gradle.kts` o `buildSrc/`— Tilt fuerza una reconstrucción completa de la imagen en vez de un sync (`fall_back_on`), porque un cambio de dependencias no se puede sincronizar en caliente. Los cuatro están también en los `deps` del `local_resource`: con Maven un cambio en el POM raíz no disparaba nada, que era un agujero latente.

Para `ukun-web` el `live_update` es solo `sync` **sin** `restart_container()`: Vite hace HMR real (~150ms) y reiniciar el proceso sería más lento que dejarlo recargar solo.

## Depuración remota

Cada servicio Java expone su puerto JDWP (ver tabla de arriba) — conéctate desde tu IDE como *Remote JVM Debug*. Sobrevive a `restart_container()`: el IDE reconecta solo.

## Ver trazas y métricas en local

Los tres servicios Java exportan por OTLP al recurso `otel-collector` desde v1.0 (issue #52). No hay
nada que arrancar aparte: `tilt up` lo levanta con los demás.

1. Abre el recurso **`otel-collector`** en la UI de Tilt. Vacío al principio: nadie ha pedido nada.
2. Navega el SPA por el 8080 — un login y el catálogo bastan.
3. En el panel del colector aparecen los spans. Lo que hay que mirar no es que lleguen, sino que los
   de `ukun-gateway`, `ukun-api` y `ukun-auth` compartan **el mismo `trace_id`**: eso es lo que
   distingue tener tracing de tener tres servicios que escriben cada uno lo suyo.
4. Las métricas llegan cada 10 segundos (`MANAGEMENT_OTLP_METRICS_EXPORT_STEP` en `compose.dev.yaml`;
   en producción el paso es de un minuto). `http.server.requests` es la que responde «¿cuántas
   peticiones y a qué latencia?» sin una línea de log por cada `GET` de catálogo.

**Si paras el colector, no pasa nada.** El exportador reintenta y los servicios siguen atendiendo:
un destino de telemetría caído no puede tumbar el sistema que observa. Es una propiedad del diseño,
no una casualidad — pruébala con `docker compose stop otel-collector` si tienes la duda.

El exportador es `debug`, que vuelca a stdout, y no hay Jaeger ni Prometheus en el stack. Cuando haga
falta un waterfall de verdad, la imagen a la que cambiar es `grafana/otel-lgtm` — está descartada hoy
por tamaño (~1,2 GB, y `compose.dev.yaml` lo levanta también el job `e2e` de CI), no por capacidad.

## Al venir de una rama anterior a v1.0

Dos cosas que hay que hacer una vez, y sin las cuales el stack no arranca (ADR-0024, issue #118):

- **`UKUN_SIGNING_KEY` en tu `.env`.** La clave con la que `ukun-auth` firma los tokens salió del
  arranque y pasó a ser configuración, sin valor por defecto — igual que los dos secretos de cliente.
  Cómo generarla está en `.env.example`; sin ella el servicio se niega a arrancar, que es exactamente
  lo que se quiere frente a una clave conocida. A cambio, reiniciar `ukun-auth` ya no cierra la sesión
  de nadie.
- **La base `ukun_gateway`.** El gateway tiene base propia desde v1.0 y la crea
  `scripts/postgres-init-databases.sh`, que Postgres solo ejecuta **sobre un volumen virgen**: sobre
  uno de antes de v1.0 esa base no existe y el gateway no encuentra dónde guardar la sesión. El
  `db-reset` de más abajo la crea, pero se lleva por delante todo lo que hayas hecho probando; si
  prefieres conservarlo, basta con crearla a mano una vez y dejar que Liquibase la migre al arrancar:

  ```bash
  docker compose exec db psql -U "$POSTGRES_USER" -d ukun_app -c 'CREATE DATABASE ukun_gateway'
  ```

## Comprobar el sistema con más de una réplica

Fuera de Tilt y a mano, porque no es entorno de desarrollo sino la condición de salida del issue #118:

```bash
docker compose -f compose.yaml -f compose.replicas.yaml up --build -d \
  db ukun-api ukun-auth ukun-auth-b auth-lb ukun-gateway
./scripts/replicas-journey.sh
```

Levanta **dos** `ukun-api` y **dos** `ukun-auth`; el gateway sigue siendo uno y sigue publicando el
8080, que es por donde se entra. La forma del override —por qué no se usa `compose.dev.yaml`, por qué
`ukun-auth` lleva balanceador delante y `ukun-api` no— está explicada en la cabecera de
`compose.replicas.yaml`, que es donde se lee al tocarlo.

El script comprueba lo que se puede comprobar hablando por HTTP: el recorrido login → catálogo →
carrito → pedido, doce peticiones seguidas **sin `401` intermitentes**, un solo `kid` en veinte
lecturas del JWKS repartidas entre réplicas, y que el presupuesto del limitador de login corta al
sexto intento y no al undécimo. Antes de v1.0 fallaban las cuatro, por motivos distintos (ADR-0024).

Las dos que exigen parar una réplica se hacen a mano, y son las más concluyentes:

```bash
# La sesión se crea en una réplica y se usa en la otra: pide la página de login, mira en el log del
# balanceador quién la sirvió, para ESA, y envía las credenciales. Si la superviviente las acepta, la
# sesión salió del heap de verdad; antes de v1.0 responde /login?error.
docker logs ukun-auth-lb-1 | grep 'GET /login '
docker stop ukun-ukun-auth-1   # o -b, la que aparezca

# La sesión del BFF sobrevive a un reinicio del gateway: inicia sesión, reinicia, y repite la
# petición con la misma cookie. Antes de v1.0 responde 401; con la sesión a medias, un 302 hacia
# /oauth2/authorize (que es lo que pasa si faltan los tokens y no la sesión — ADR-0024).
docker restart ukun-ukun-gateway-1
```

Tres cosas que al leer los logs parecen fallos y no lo son:

- La segunda réplica de `ukun-api` escribe `Waiting for changelog lock....` al arrancar. Las dos corren
  Liquibase contra `ukun_app` y la segunda espera su turno (skill `liquibase`).
- `ukun-auth` y `ukun-api` no escriben nada más en el log. Fuera del perfil `dev` el nivel raíz es
  `WARN`, así que un arranque limpio es silencioso; `ukun-api` además no tiene access log, y probar
  que sus dos réplicas sirven se hace parando una y viendo que el recorrido sigue en 200.
- El balanceador parece mandarlo todo a una réplica durante las primeras peticiones. El round-robin de
  nginx es estado por proceso worker; con treinta peticiones ya se ve el reparto.

## Resetear la base de datos

Botón **`db-reset`** en la UI de Tilt (etiqueta `ops`, es manual — no se ejecuta solo). Borra el volumen de Postgres; el siguiente arranque recrea el esquema con el baseline de Liquibase y los datos demo en unos segundos. Úsalo sin miedo cuando quieras partir de datos limpios. Borra el volumen **del proyecto activo**, así que desde un worktree no toca la base del checkout principal.

### Borrar solo lo que dejan los recorridos de Playwright

Botón **`e2e-cleanup`** (misma etiqueta `ops`, también manual), o `scripts/e2e-cleanup.sh` desde la
terminal — con `--dry-run` para ver qué borraría sin tocar nada.

Cada pasada del smoke da de alta datos nuevos y no los borra: los correos y los SKU llevan marca de
tiempo justamente para que dos ejecuciones seguidas no choquen (`13-testing.md` §3). Tras un rato de
smokes, el catálogo y el back-office locales se llenan de «Tipo E2E …» y de cuentas
`e2e-…@example.com`, que es ruido para mirar cualquier pantalla a ojo. Este script borra **solo** lo
que casa con los patrones que generan las fixtures —cliente, empleado, tipo, producto con su foto en
`resources/uploads`, pedido, cuenta en `ukun_auth` y su rastro en `audit_log`— y deja intactos los
datos demo y lo que hayas creado tú probando, que es lo que `db-reset` sí se lleva.

Lo que **no** hace es reponer el stock que el recorrido consumió: esas cantidades son datos demo
modificados, no filas nuevas, y para volver al catálogo recién sembrado el camino sigue siendo
`db-reset`.

## Varios worktrees a la vez

Dos stacks simultáneos colisionan en los puertos del host y, peor, en el nombre del proyecto de compose: con el mismo nombre, el segundo `tilt up` no levanta un stack nuevo, sino que **reutiliza los contenedores del primero**.

Lo resuelve un `.env.ports` por worktree, que genera la skill `worktree` con un desplazamiento de +100 por worktree activo:

```bash
COMPOSE_PROJECT_NAME=ukun-catalog-api
API_PORT=8181
DB_PORT=5532
TILT_PORT=10450
# …el resto de variables de la tabla de arriba
```

El `Tiltfile` lo carga si existe, así que `tilt up` a secas ya hace lo correcto desde un worktree. Sin ese fichero no se lee nada y valen los puertos por defecto: un checkout único no necesita configurar nada.

**`TILT_PORT` es la excepción**: el servidor HTTP de Tilt arranca antes de que se evalúe el `Tiltfile`, así que ahí llega tarde y tiene que estar exportado en el shell:

```bash
set -a; . ./.env.ports; set +a
tilt up
```

Que es exactamente lo que hace el script de arranque que la skill deja en `.claude/session/tilt.sh`. Si lanzas `tilt up` a mano sin ese `source`, el stack sale con los puertos correctos pero la UI intentará abrirse en el 10350 del stack principal.

## Imágenes de producción

Las imágenes de desarrollo (`Dockerfile.dev`, con `live_update`) son distintas de las que se despliegan de verdad — este apartado es sobre las segundas, entregable de v0.1 (`04-roadmap.md`). Cinco correcciones sobre el `Dockerfile` legacy, cada una con su motivo:

1. **`ENTRYPOINT` con nombre de jar fijo.** El legacy usa `java -jar /opt/app/*.jar` en exec form, que no expande el glob — funciona por accidente porque el jar resultante se llama literalmente `*.jar` (defecto #16). Se fija con `bootJar { archiveFileName = "app.jar" }` en el convention plugin y `ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]`.
2. **Jar en capas** (`java -Djarmode=tools -jar app.jar extract --layers`): dependencias, loader, snapshots y clases de aplicación en capas Docker separadas, para que un cambio de código no invalide la capa (pesada) de dependencias.
3. **Caché de BuildKit para `~/.gradle`** (`RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon ...`), que guarda tanto las dependencias como la distribución que descarga el wrapper. Aquí el daemon **sí** se desactiva, al contrario que en el ciclo de Tilt: el contenedor es de un solo uso, así que un daemon solo añadiría arranque para tirarlo con la capa.
4. **Usuario no root**: `USER` explícito sobre una base `-jre-jammy` o `-jre-alpine`, en vez de ejecutar el proceso Java como root por omisión.
5. **`HEALTHCHECK`** contra `/actuator/health` y un `.dockerignore` por servicio.

Se descarta explícitamente CDS/AOT (Class Data Sharing / `native-image`): reducen el tiempo de arranque a cambio de bastante complejidad de build, y el arranque no es un problema real de este proyecto — ningún requisito de `02-requisitos.md` lo pide.

## Cómo probar el API

- **springdoc con Swagger UI** en `ukun-api`, activo solo en el perfil de desarrollo. Matiz importante: como el contrato es design-first (`04-roadmap.md`), **springdoc no es la fuente de verdad** — el YAML versionado en `docs/api/openapi/` lo es. Springdoc sirve para explorar y probar a mano, y es el productor del `/v3/api-docs` que el check de deriva de CI compara contra ese YAML.
- **Colección Bruno versionada en `docs/api/bruno/`**, no Postman: son ficheros de texto que se revisan en un diff normal de git y no exigen cuenta ni sincronización en la nube — coherente con que el contrato también viva como texto versionado. Un solo entorno, `local`, contra el stack de `tilt up` — no hay backend de mocks contra el que apuntar.
- Un `local_resource` de UI (botón en `localhost:10350`) que abre Swagger UI, siguiendo el mismo patrón manual que `db-reset` — ver skill `tilt-dev`.

## Qué hacer si algo no va

- **Un recurso se queda rojo**: mira su log en la UI; casi siempre es un fallo de compilación de Gradle o la base de datos aún no está lista (el `resource_deps` del `Tiltfile` ya ordena el arranque, pero si acabas de hacer `db-reset` dale unos segundos).
- **Un cambio de código no se refleja**: comprueba que el fichero está dentro de `src/` (el `sync` solo vigila esa carpeta) y que no has tocado a la vez un fichero de build sin dejar que termine el rebuild completo.
- **El servicio arranca y muere quejándose de configuración ausente**: es el `sync` de `build/resources/main`. Si falta esa entrada del classpath, `application.yaml` no está y el contexto no llega a levantar.
- **Puertos ocupados**: si tenías un `docker compose up` corriendo por fuera de Tilt, páralo primero (`docker compose down`).
- **Dos stacks a la vez** (dos worktrees en paralelo): usa la skill `worktree`, que reasigna los puertos del segundo stack en vez de dejar que colisionen.
