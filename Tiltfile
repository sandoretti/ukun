# -*- mode: Python -*-
#
# Entorno de desarrollo local de Ukun. Ver docs/06-entorno-local-tilt.md para el manual
# de uso y docs/adr/0005-tilt-sobre-docker-compose.md para por qué está montado así.

docker_compose(['compose.yaml', 'compose.dev.yaml'], project_name='ukun')

dc_resource('db', labels=['data'])

# ---------------------------------------------------------------------------
# Compilación de los servicios Java.
def mvn_build_cmd(module):
    return (
        'docker run --rm ' +
        # Sin --user el contenedor compila como root sobre el bind mount del repo, y contra un
        # daemon rootful (systemd, un runner de CI) deja services/*/target propiedad de root:
        # el siguiente ./mvnw clean del host muere con "Failed to delete target/dependency/*.jar".
        # Docker Desktop lo enmascara porque mapea el montaje al usuario del host, así que el
        # fallo aparecería directamente en CI. Mismo principio que los Dockerfiles de producción
        # (docs/06-entorno-local-tilt.md, "usuario no root").
        '--user "$(id -u):$(id -g)" ' +
        '-v "$(pwd)":/opt/app ' +
        # La caché de Maven es el ~/.m2 del host y no un volumen con nombre: Docker crea el
        # volumen como root:root y un contenedor no-root no puede escribir en él. De paso, las
        # compilaciones de Tilt y las del host comparten caché en vez de duplicar descargas.
        # HOME hace falta porque el uid del host no existe en /etc/passwd de la imagen, así que
        # ni el wrapper de Maven ni la JVM sabrían dónde está el home sin decírselo.
        '-e HOME=/var/maven ' +
        '-e MAVEN_OPTS=-Duser.home=/var/maven ' +
        '-v "$HOME/.m2":/var/maven/.m2 ' +
        '-w /opt/app ' +
        'eclipse-temurin:25-jdk-jammy ' +
        './mvnw -q -T1C -DskipTests compile dependency:copy-dependencies -pl {module} -am'
    ).format(module=module)

local_resource(
    name='mvn-api',
    cmd=mvn_build_cmd('services/ukun-api'),
    deps=['services/ukun-api/src', 'services/ukun-api/pom.xml'],
    labels=['build'],
)

local_resource(
    name='mvn-auth',
    cmd=mvn_build_cmd('services/ukun-auth'),
    deps=['services/ukun-auth/src', 'services/ukun-auth/pom.xml'],
    labels=['build'],
)

local_resource(
    name='mvn-gateway',
    cmd=mvn_build_cmd('services/ukun-gateway'),
    deps=['services/ukun-gateway/src', 'services/ukun-gateway/pom.xml'],
    labels=['build'],
)

# ---------------------------------------------------------------------------
# Imágenes de desarrollo + live update.
docker_build(
    ref='ukun/api:dev',
    context='services/ukun-api',
    dockerfile='services/ukun-api/Dockerfile.dev',
    live_update=[
        fall_back_on(['services/ukun-api/pom.xml']),
        sync('services/ukun-api/target/classes', '/opt/app/classes'),
        sync('services/ukun-api/target/dependency', '/opt/app/deps'),
        restart_container(),
    ],
)
dc_resource('ukun-api', resource_deps=['mvn-api', 'db'], labels=['backend'])

docker_build(
    ref='ukun/auth:dev',
    context='services/ukun-auth',
    dockerfile='services/ukun-auth/Dockerfile.dev',
    live_update=[
        fall_back_on(['services/ukun-auth/pom.xml']),
        sync('services/ukun-auth/target/classes', '/opt/app/classes'),
        sync('services/ukun-auth/target/dependency', '/opt/app/deps'),
        restart_container(),
    ],
)
dc_resource('ukun-auth', resource_deps=['mvn-auth', 'db'], labels=['backend'])

docker_build(
    ref='ukun/gateway:dev',
    context='services/ukun-gateway',
    dockerfile='services/ukun-gateway/Dockerfile.dev',
    live_update=[
        fall_back_on(['services/ukun-gateway/pom.xml']),
        sync('services/ukun-gateway/target/classes', '/opt/app/classes'),
        sync('services/ukun-gateway/target/dependency', '/opt/app/deps'),
        restart_container(),
    ],
)
dc_resource('ukun-gateway', resource_deps=['mvn-gateway', 'ukun-api', 'ukun-auth'], labels=['backend'])

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

local_resource(
    name='db-reset',
    cmd=(
        'docker compose -p ukun -f compose.yaml -f compose.dev.yaml stop db && ' +
        'docker compose -p ukun -f compose.yaml -f compose.dev.yaml rm -f db && ' +
        'docker volume rm ukun_db_data'
    ),
    trigger_mode=TRIGGER_MODE_MANUAL,
    auto_init=False,
    labels=['ops'],
)

local_resource(
    name='swagger-ui',
    cmd='echo "Abriendo Swagger UI..." && (xdg-open "http://localhost:8081/swagger-ui.html" || open "http://localhost:8081/swagger-ui.html" || true)',
    trigger_mode=TRIGGER_MODE_MANUAL,
    auto_init=False,
    labels=['ops'],
)
