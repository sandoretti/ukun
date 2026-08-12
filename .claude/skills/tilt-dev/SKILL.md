---
name: tilt-dev
description: Cómo usar y extender el entorno de desarrollo local con Tilt en este repo — comandos, live update de servicios Java y del frontend, reseteo de base de datos, y cómo añadir un servicio nuevo al Tiltfile. Usar al trabajar con el entorno local o al añadir un servicio nuevo (ukun-api, ukun-auth, ukun-gateway, ukun-web) a Tilt.
---

# Trabajar con el `Tiltfile` de este repo

Referencia completa de uso en `docs/06-entorno-local-tilt.md`. Este skill es para cuando hay que **modificar** el `Tiltfile`, no solo usarlo.

## Comandos

```bash
tilt up      # levanta el stack, abre la UI en localhost:10350
tilt down    # para los contenedores (no borra volúmenes)
```

## Cómo está montado hoy

`Tiltfile` adopta `compose.yaml` + `compose.dev.yaml` (override con healthchecks, `depends_on: condition`, puertos de debug JDWP). Para cada servicio Java (`ukun-api`, `ukun-auth`, `ukun-gateway`):

```python
docker_build(
    ref = 'ukun/<servicio>:dev',
    context = '<carpeta-del-servicio>',
    dockerfile = '<carpeta-del-servicio>/Dockerfile.dev',
    live_update = [
        # cambio de dependencias -> rebuild completo. BUILD_FILES son los ficheros compartidos
        # (catálogo, settings, build raíz, buildSrc): tocarlos afecta a los tres servicios.
        fall_back_on(['<carpeta>/build.gradle.kts'] + BUILD_FILES),
        sync('<carpeta>/build/classes/java/main', '/opt/app/classes'),
        sync('<carpeta>/build/resources/main', '/opt/app/resources'),
        sync('<carpeta>/build/dependency', '/opt/app/deps'),
        restart_container(),
    ],
)
```

El destino es **`/opt/app`**, que es donde los `Dockerfile.dev` colocan la aplicación — no `/app`. Equivocarse aquí no rompe nada de forma visible: el `sync` sigue funcionando y lo que deja de pasar es que los cambios se reflejen.

**Son tres `sync`, no dos.** Gradle separa clases y recursos en directorios distintos (Maven mandaba ambos a `target/classes`), y `application.yaml` está en el de recursos: si te dejas ese `sync`, el servicio no arranca. El `ENTRYPOINT` del `Dockerfile.dev` lleva las tres entradas de classpath en el mismo orden.

La compilación (`./gradlew :<servicio>:classes :<servicio>:copyDeps`) ocurre en un `local_resource` que produce esos directorios. `classes` y no `build`: no se empaqueta jar ni se corren tests. **No se usa `spring-boot-devtools`** — motivo en `docs/06-entorno-local-tilt.md` y `docs/03-analisis-y-patrones.md` (su `RestartClassLoader` da fallos sutiles con proxies de Hibernate).

### La compilación va en el host, no en un contenedor

`gradle_build_cmd` invoca `./gradlew` directamente en el host. **No lo metas en un `docker run`** (ADR-0021): un contenedor efímero mata el daemon de Gradle en cada ciclo, y sin daemon el ciclo es más lento que el Maven que se sustituyó — se perdería justo lo que motivó el cambio. El precio es que el host necesita un JDK, y está aceptado y documentado en `docs/06`.

Esto es un cambio respecto a cómo estaba montado con Maven, y hace desaparecer tres cosas que ya no vas a encontrar en el `Tiltfile`: el `--user "$(id -u):$(id -g)"` (que existía para que los directorios de salida no quedaran de root contra un daemon rootful), el bind mount de la caché, y el `HOME` postizo. Si alguna vez vuelves a compilar en contenedor, vuelven las tres — y con el daemon de Gradle además tendrías que decidir qué hacer con `~/.gradle`, que guarda locks y el registro del daemon, no solo dependencias descargadas.

## Al añadir un servicio Java nuevo (`ukun-api`, `ukun-auth`, `ukun-gateway`)

1. Añade su bloque `docker_build` siguiendo el patrón de arriba — el `Tiltfile` actual ya deja un comentario marcando dónde.
2. Dale `resource_deps` a la base de datos correspondiente (y a otros servicios de los que dependa en arranque, p. ej. `ukun-gateway` depende de que `ukun-api`/`ukun-auth` publiquen su JWKS/estén sanos).
3. Etiqueta el recurso (`labels=['backend']`) para que aparezca agrupado en la UI.
4. Expón su puerto de debug JDWP en `compose.dev.yaml` (`ukun-api` 5005, `ukun-auth` 5006, `ukun-gateway` 5007 — tabla completa en `docs/06-entorno-local-tilt.md`).

Todo puerto publicado va **parametrizado con su valor actual como default**, nunca literal — `"${JDWP_API_PORT:-5005}:5005"`. Solo el lado host: el lado contenedor se queda fijo, porque la comunicación interna (`db:5432`, `ukun-api:8081`) no se mueve entre worktrees. Un literal nuevo rompe el aislamiento del apartado de abajo sin que nadie se entere hasta que dos stacks chocan.

## Al añadir `apps/ukun-web`

El `live_update` **no lleva `restart_container()`** — Vite hace HMR real sobre los ficheros sincronizados (~150ms). Sí lleva un `run('npm ci', trigger=['package.json'])` para cuando cambian dependencias.

```python
docker_build(
    ref = 'ukun/web:dev',
    context = 'apps/ukun-web',
    dockerfile = 'apps/ukun-web/Dockerfile.dev',
    live_update = [
        fall_back_on(['apps/ukun-web/package-lock.json', 'apps/ukun-web/vite.config.ts']),
        sync('apps/ukun-web/src', '/app/src'),
        run('npm ci', trigger=['apps/ukun-web/package.json']),
    ],
)
```

## `db-reset`

`local_resource` manual (`trigger_mode=TRIGGER_MODE_MANUAL, auto_init=False`) que borra el volumen de Postgres. Añádelo como dependencia de arranque de nada — es una acción explícita del desarrollador, nunca automática.

## Botón de Swagger UI

Mismo patrón que `db-reset`: un `local_resource` (o un `link` de recurso, si Tilt lo soporta para el servicio `ukun-api`) que abre `http://localhost:<puerto>/swagger-ui.html`. Ver `docs/06-entorno-local-tilt.md` ("Cómo probar el API") para qué es y qué no es springdoc en este proyecto.

## Nada de legacy en el `Tiltfile`

`ukun-empleados` y `nginx` **no se levantan**: no están en servicio y no hay convivencia (`docs/04-roadmap.md`). No añadas sus recursos al `Tiltfile` ni "por si acaso" — el stack de desarrollo es solo el sistema nuevo. (`ukun-clientes` ya se borró en v0.6, así que ni existe.)

## Dos stacks a la vez (worktrees en paralelo)

Si hay dos worktrees activos, el segundo necesita puertos distintos o los contenedores colisionan. Lo resuelve la skill `worktree`, que genera un `.env.ports` por worktree; el `Tiltfile` lo carga si existe y compone con él tanto los puertos como el `project_name`.

Tres cosas que hay que respetar al tocar esta parte:

- **Tilt no tiene builtin de `dotenv`** (probado en 0.37.6: `undefined: dotenv`). El `Tiltfile` parsea el fichero a mano y llama a `os.putenv`, que sí llega a tiempo a la interpolación de `${VAR}` de los ficheros de compose.
- **El `project_name` no puede ser literal.** Con el mismo nombre, el segundo `tilt up` reutiliza los contenedores del primero en vez de levantar un stack nuevo. Y como el volumen real es `<proyecto>_db_data`, un `db-reset` con el nombre fijo borra la base del otro stack — era el caso antes de parametrizarlo.
- **`TILT_PORT` no se resuelve desde el `Tiltfile`**: el servidor HTTP arranca antes de evaluarlo. Va exportado en el shell.
