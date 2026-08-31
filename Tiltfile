# -*- mode: Python -*-
#
# Entorno de desarrollo local de Ukun. Ver docs/06-entorno-local-tilt.md para el manual
# de uso y docs/adr/0005-tilt-sobre-docker-compose.md para por qué está montado así.

# Puertos y nombre de proyecto por worktree. `.env.ports` lo genera la skill `worktree` para
# que dos stacks convivan (desplazamiento +100 por worktree); si no existe, los ficheros de
# compose caen a sus valores base y el comportamiento es el de un checkout único.
#
# Se parsea a mano porque Tilt no tiene builtin de dotenv: `os.putenv` sí llega a tiempo a la
# interpolación de ${VAR} de los ficheros de compose, que es lo que se necesita aquí.
#
# TILT_PORT es la excepción y no se puede resolver desde aquí: el servidor HTTP de Tilt arranca
# ANTES de evaluar este fichero, así que tiene que venir ya exportado del shell. Por eso el
# script de arranque de la skill hace `set -a; . ./.env.ports; set +a` antes de `tilt up`.
def load_env_file(path):
    for line in str(read_file(path)).splitlines():
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            os.putenv(key.strip(), value.strip().strip('"').strip("'"))

if os.path.exists('.env.ports'):
    load_env_file('.env.ports')

project_name = os.getenv('COMPOSE_PROJECT_NAME', 'ukun')

docker_compose(['compose.yaml', 'compose.dev.yaml'], project_name=project_name)

dc_resource('db', labels=['data'])

# El colector OTLP al que exportan los tres servicios Java (issue #52). Imagen de terceros: no lleva
# docker_build ni live update, solo hay que enseñárselo a Tilt para que su panel muestre lo que llega.
#
# A propósito SIN resource_deps y sin que nadie dependa de él: si el colector no arranca, los tres
# backends tienen que seguir levantándose igual. Un destino de telemetría es lo último que puede ser
# capaz de impedir que el sistema arranque.
dc_resource('otel-collector', labels=['observability'])

# ---------------------------------------------------------------------------
# Compilación de los servicios Java.
#
# Se compila en el HOST, no en un contenedor auxiliar (ADR-0021). El daemon de Gradle es lo que
# mantiene caliente la JVM y el análisis incremental entre ciclos, y un `docker run --rm` lo mata en
# cada invocación: el arranque en frío costaría más que todo el ciclo. A cambio, el host necesita un
# JDK — requisito nuevo, ver docs/06-entorno-local-tilt.md.
#
# De paso desaparecen tres cosas que la variante en contenedor obligaba a arrastrar: el `--user`
# para que los directorios de salida no quedaran de root, el bind mount de la caché, y el HOME
# postizo para que el wrapper supiera dónde escribir.
#
# `classes` (no `build`) y `copyDeps` son el mínimo que el live update necesita: nada de tests, nada
# de empaquetar un jar. Los ficheros de build entran en `deps` además de src/ — con Maven un cambio
# en el POM raíz no disparaba nada, que era un agujero latente.
def gradle_build_cmd(module):
    # flock serializa las llamadas concurrentes de Tilt al daemon de Gradle para
    # evitar cuelgues por colisión de locks o de la configuration cache.
    return 'flock .tilt-gradle.lock ./gradlew -q :{module}:classes :{module}:copyDeps'.format(module=module)

# Cambiar cualquiera de estos invalida el classpath de los tres servicios, no solo de uno.
BUILD_FILES = [
    'gradle/libs.versions.toml',
    'settings.gradle.kts',
    'build.gradle.kts',
    'buildSrc',
]

def java_service_deps(module):
    return ['services/{}/src'.format(module), 'services/{}/build.gradle.kts'.format(module)] + BUILD_FILES

local_resource(
    name='gradle-api',
    cmd=gradle_build_cmd('ukun-api'),
    deps=java_service_deps('ukun-api'),
    labels=['build'],
)

local_resource(
    name='gradle-auth',
    cmd=gradle_build_cmd('ukun-auth'),
    deps=java_service_deps('ukun-auth'),
    labels=['build'],
)

local_resource(
    name='gradle-gateway',
    cmd=gradle_build_cmd('ukun-gateway'),
    deps=java_service_deps('ukun-gateway'),
    labels=['build'],
)

# ---------------------------------------------------------------------------
# Imágenes de desarrollo + live update.
# Tres `sync` y no dos: Gradle separa clases y recursos en directorios distintos, mientras Maven
# mandaba ambos a target/classes. El de recursos no es opcional — application.yaml vive ahí, y sin él
# el servicio no arranca.
docker_build(
    ref='ukun/api:dev',
    # El contexto es la raíz, igual que en el Dockerfile de producción. Hace falta para que
    # `fall_back_on` pueda vigilar los ficheros de build compartidos: Tilt solo observa rutas que
    # cuelguen del contexto, y gradle/, buildSrc/ y settings.gradle.kts están fuera del servicio.
    context='.',
    dockerfile='services/ukun-api/Dockerfile.dev',
    live_update=[
        fall_back_on(['services/ukun-api/build.gradle.kts'] + BUILD_FILES),
        sync('services/ukun-api/build/classes/java/main', '/opt/app/classes'),
        sync('services/ukun-api/build/resources/main', '/opt/app/resources'),
        sync('services/ukun-api/build/dependency', '/opt/app/deps'),
        restart_container(),
    ],
)
dc_resource('ukun-api', resource_deps=['gradle-api', 'db'], labels=['backend'])

docker_build(
    ref='ukun/auth:dev',
    # El contexto es la raíz, igual que en el Dockerfile de producción. Hace falta para que
    # `fall_back_on` pueda vigilar los ficheros de build compartidos: Tilt solo observa rutas que
    # cuelguen del contexto, y gradle/, buildSrc/ y settings.gradle.kts están fuera del servicio.
    context='.',
    dockerfile='services/ukun-auth/Dockerfile.dev',
    live_update=[
        fall_back_on(['services/ukun-auth/build.gradle.kts'] + BUILD_FILES),
        sync('services/ukun-auth/build/classes/java/main', '/opt/app/classes'),
        sync('services/ukun-auth/build/resources/main', '/opt/app/resources'),
        sync('services/ukun-auth/build/dependency', '/opt/app/deps'),
        restart_container(),
    ],
)
dc_resource('ukun-auth', resource_deps=['gradle-auth', 'db'], labels=['backend'])

docker_build(
    ref='ukun/gateway:dev',
    # El contexto es la raíz, igual que en el Dockerfile de producción. Hace falta para que
    # `fall_back_on` pueda vigilar los ficheros de build compartidos: Tilt solo observa rutas que
    # cuelguen del contexto, y gradle/, buildSrc/ y settings.gradle.kts están fuera del servicio.
    context='.',
    dockerfile='services/ukun-gateway/Dockerfile.dev',
    live_update=[
        fall_back_on(['services/ukun-gateway/build.gradle.kts'] + BUILD_FILES),
        sync('services/ukun-gateway/build/classes/java/main', '/opt/app/classes'),
        sync('services/ukun-gateway/build/resources/main', '/opt/app/resources'),
        sync('services/ukun-gateway/build/dependency', '/opt/app/deps'),
        restart_container(),
    ],
)
# Depende también de ukun-web: desde v0.5 el gateway es el único punto de entrada y enruta hacia el
# dev server de Vite todo lo que no sea backend, así que localhost:$GATEWAY_PORT sirve la aplicación
# entera (docs/06-entorno-local-tilt.md).
dc_resource('ukun-gateway', resource_deps=['gradle-gateway', 'ukun-api', 'ukun-auth', 'ukun-web'], labels=['backend'])

docker_build(
    ref='ukun/web:dev',
    context='apps/ukun-web',
    dockerfile='apps/ukun-web/Dockerfile.dev',
    live_update=[
        fall_back_on(['apps/ukun-web/package-lock.json', 'apps/ukun-web/vite.config.ts']),
        sync('apps/ukun-web/src', '/app/src'),
        run('npm ci || true', trigger=['apps/ukun-web/package.json']),
    ],
)
dc_resource('ukun-web', labels=['frontend'])

# ---------------------------------------------------------------------------
# Operaciones manuales y recursos UI.

# El nombre del proyecto va compuesto, no literal: el volumen real es <proyecto>_db_data, así que
# con `-p ukun` fijo este recurso borraba la base del checkout principal al lanzarlo desde un worktree.
local_resource(
    name='db-reset',
    cmd=(
        'docker compose -p {p} -f compose.yaml -f compose.dev.yaml stop db && ' +
        'docker compose -p {p} -f compose.yaml -f compose.dev.yaml rm -f db && ' +
        'docker volume rm {p}_db_data'
    ).format(p=project_name),
    trigger_mode=TRIGGER_MODE_MANUAL,
    auto_init=False,
    labels=['ops'],
)

local_resource(
    name='swagger-ui',
    cmd=(
        'echo "Abriendo Swagger UI..." && ' +
        '(xdg-open "http://localhost:{port}/swagger-ui.html" || ' +
        'open "http://localhost:{port}/swagger-ui.html" || true)'
    ).format(port=os.getenv('API_PORT', '8081')),
    trigger_mode=TRIGGER_MODE_MANUAL,
    auto_init=False,
    labels=['ops'],
)
