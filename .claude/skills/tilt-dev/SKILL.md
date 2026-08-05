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
        fall_back_on(['<carpeta>/pom.xml']),   # cambio de dependencias -> rebuild completo
        sync('<carpeta>/target/classes', '/app/classes'),
        sync('<carpeta>/target/dependency', '/app/deps'),
        restart_container(),
    ],
)
```

La compilación (`mvn compile dependency:copy-dependencies`) ocurre en un `local_resource` o contenedor de build que produce `target/classes`/`target/dependency`, que es lo que el `sync` copia. **No se usa `spring-boot-devtools`** — motivo en `docs/06-entorno-local-tilt.md` y `docs/03-analisis-y-patrones.md` (su `RestartClassLoader` da fallos sutiles con proxies de Hibernate).

## Al añadir un servicio Java nuevo (`ukun-api`, `ukun-auth`, `ukun-gateway`)

1. Añade su bloque `docker_build` siguiendo el patrón de arriba — el `Tiltfile` actual ya deja un comentario marcando dónde.
2. Dale `resource_deps` a la base de datos correspondiente (y a otros servicios de los que dependa en arranque, p. ej. `ukun-gateway` depende de que `ukun-api`/`ukun-auth` publiquen su JWKS/estén sanos).
3. Etiqueta el recurso (`labels=['backend']`) para que aparezca agrupado en la UI.
4. Expón su puerto de debug JDWP en `compose.dev.yaml` (`ukun-api` 5005, `ukun-auth` 5006, `ukun-gateway` 5007 — tabla completa en `docs/06-entorno-local-tilt.md`).

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

`ukun-clientes`, `ukun-empleados` y `nginx` **no se levantan**: no están en servicio y no hay convivencia (`docs/04-roadmap.md`). No añadas sus recursos al `Tiltfile` ni "por si acaso" — el stack de desarrollo es solo el sistema nuevo.

## Dos stacks a la vez (worktrees en paralelo)

Si hay dos worktrees activos, el segundo necesita puertos distintos o los contenedores colisionan. Lo resuelve la skill `worktree`, que genera un `.env.ports` por worktree; el `Tiltfile` lee esas variables en vez de puertos literales.
