# -*- mode: Python -*-
#
# Entorno de desarrollo local de Ukun. Ver docs/06-entorno-local-tilt.md para el manual
# de uso y docs/adr/0005-tilt-sobre-docker-compose.md para por qué está montado así.
#
# Hoy orquesta exactamente lo que docker-compose.yml levanta (db, ukun-clientes,
# ukun-empleados, nginx) — docker-compose.yml no se ha tocado, `docker compose up
# --build` sigue funcionando igual sin Tilt. Cuando lleguen los servicios nuevos
# (ukun-api, ukun-auth, ukun-gateway, apps/ukun-web — ver docs/04-roadmap.md), se
# añaden siguiendo el mismo patrón: ver la skill "tilt-dev" en .claude/skills/.

# project_name fijo para que el nombre del volumen de datos (ukun_db_data) sea
# predecible y el botón db-reset de más abajo pueda referenciarlo sin ambigüedad.
docker_compose(['docker-compose.yml', 'compose.dev.yaml'], project_name='ukun')

dc_resource('db', labels=['data'])
dc_resource('nginx', labels=['edge'])

# ---------------------------------------------------------------------------
# Compilación de los servicios Java.
#
# Corre dentro de un contenedor efímero (no hace falta JDK/Maven en el host, solo
# Docker) usando el mismo wrapper que el proyecto ya trae. El resultado
# (target/classes, target/dependency) queda en el host y es lo que las imágenes
# Dockerfile.dev copian y lo que Tilt sincroniza en caliente.
#
# El volumen ukun_m2 cachea el repositorio local de Maven entre ejecuciones para que
# solo la primera compilación sea lenta.

def mvn_build_cmd(module):
    return (
        'docker run --rm ' +
        '-v "$(pwd)/{module}":/opt/app ' +
        '-v ukun_m2:/root/.m2 ' +
        '-w /opt/app ' +
        'eclipse-temurin:17-jdk-jammy ' +
        './mvnw -q -T1C -DskipTests compile dependency:copy-dependencies'
    ).format(module=module)

local_resource(
    name='mvn-clientes',
    cmd=mvn_build_cmd('ukun-clientes'),
    deps=['ukun-clientes/src', 'ukun-clientes/pom.xml'],
    labels=['build'],
)

local_resource(
    name='mvn-empleados',
    cmd=mvn_build_cmd('ukun-empleados'),
    deps=['ukun-empleados/src', 'ukun-empleados/pom.xml'],
    labels=['build'],
)

# ---------------------------------------------------------------------------
# Imágenes de desarrollo + live update.
#
# Sin spring-boot-devtools a propósito (ver docs/03-analisis-y-patrones.md): se
# sincroniza el classpath ya compilado y se reinicia el proceso dentro del MISMO
# contenedor (restart_container()), en vez de depender del RestartClassLoader de
# devtools. Ciclo esperado: ~2-4s tras guardar un fichero.

docker_build(
    ref='ukun_ukun-clientes',
    context='ukun-clientes',
    dockerfile='ukun-clientes/Dockerfile.dev',
    live_update=[
        fall_back_on(['ukun-clientes/pom.xml']),
        sync('ukun-clientes/target/classes', '/opt/app/classes'),
        sync('ukun-clientes/target/dependency', '/opt/app/deps'),
        restart_container(),
    ],
)
dc_resource('ukun-clientes', resource_deps=['mvn-clientes', 'db'], labels=['legacy'])

docker_build(
    ref='ukun_ukun-empleados',
    context='ukun-empleados',
    dockerfile='ukun-empleados/Dockerfile.dev',
    live_update=[
        fall_back_on(['ukun-empleados/pom.xml']),
        sync('ukun-empleados/target/classes', '/opt/app/classes'),
        sync('ukun-empleados/target/dependency', '/opt/app/deps'),
        restart_container(),
    ],
)
dc_resource('ukun-empleados', resource_deps=['mvn-empleados', 'db'], labels=['legacy'])

# ---------------------------------------------------------------------------
# Cuando exista services/ukun-api, services/ukun-auth, services/ukun-gateway y
# apps/ukun-web (docs/04-roadmap.md), sus bloques docker_build + dc_resource se
# añaden aquí siguiendo el mismo patrón que arriba (los tres servicios Java) o el
# patrón "sync sin restart_container(), HMR de Vite" para el frontend — detallado
# en la skill "tilt-dev".

# ---------------------------------------------------------------------------
# Operaciones manuales.

local_resource(
    name='db-reset',
    cmd=(
        'docker compose -p ukun -f docker-compose.yml -f compose.dev.yaml stop db && ' +
        'docker compose -p ukun -f docker-compose.yml -f compose.dev.yaml rm -f db && ' +
        'docker volume rm ukun_db_data'
    ),
    trigger_mode=TRIGGER_MODE_MANUAL,
    auto_init=False,
    labels=['ops'],
)
