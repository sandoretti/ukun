# ADR-0005: Tilt sobre docker-compose, no sobre Kubernetes local

## Estado
Aceptado

## Contexto
Se necesitaba un entorno de desarrollo local con recarga rápida de código. Tilt soporta dos sustratos: `docker_compose()` o un clúster Kubernetes local (kind/k3d). El desarrollador trabaja en Fedora con Docker instalado y sin `tilt`/`kind` previamente instalados. El destino de despliegue final tras abandonar Azure aún no está decidido.

## Decisión
**Tilt sobre docker-compose.** El stack actual ya es docker-compose (`docker-compose.yml`); Tilt lo adopta directamente vía `docker_compose()` sin traducir nada a manifiestos.

## Consecuencias
- Instalación mínima: solo `tilt` (sin `kind`, `kubectl`, `ctlptl`, registro local).
- `restart_container()` — el primitivo que baja el reinicio de un servicio Spring Boot a ~1-2s tras un `sync` de clases — **solo existe para recursos de docker-compose**; en Kubernetes, Tilt lo prohíbe explícitamente. Es el argumento decisivo: con kind el inner loop de Java dependería de `devtools` (frágil, descartado en `docs/03-analisis-y-patrones.md` por sus fallos con proxies de Hibernate) o de reconstruir la imagen completa en cada cambio (lento).
- Fidelidad con producción: alta si el destino final resulta ser compose self-hosted (lo que sugiere `docker-compose-sandoretti.yml`); si el destino resultara ser Kubernetes, el `Tiltfile` se reescribe (~40 líneas) pero los `Dockerfile.dev` se reutilizan tal cual — no es una puerta que se cierre.

## Alternativas descartadas
- **kind/k3d**: más fiel a un destino Kubernetes, pero añade piezas (clúster local, manifiestos, ingress, secrets) sin beneficio mientras el destino de despliegue no esté decidido, y pierde `restart_container()`.
