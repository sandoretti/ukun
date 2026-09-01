# syntax=docker/dockerfile:1
#
# Production image for ukun-gateway. This is NOT the image the local dev loop uses — that one
# is Dockerfile.dev, driven by Tilt with live_update. See docs/06-entorno-local-tilt.md.
#
# The build context is the REPOSITORY ROOT, not this directory: the aggregator build lives
# there and Gradle needs it. Build with:
#
#     docker build -f services/ukun-gateway/Dockerfile -t ukun/ukun-gateway:0.1.0-SNAPSHOT .
#
# The per-service ignore list is services/ukun-gateway/Dockerfile.dockerignore, which BuildKit
# picks up in place of any .dockerignore at the context root.

# The SPA, built here and not in apps/ukun-web because the artefact never ships on its own: this
# service serves it in production (skill `spring-gateway` §6), which is what keeps the browser on a
# single origin and makes the BFF session cookie possible at all (ADR-0003). Under `tilt up` none of
# this runs — Vite serves its own dev server with HMR and DevSpaRouteConfig routes to it.
FROM node:24-alpine AS web
WORKDIR /web

# Manifests first, sources after, for the same layering reason as the Gradle stage below. `ci` and
# not `install`, matching the frontend job of .github/workflows/ci.yml: it respects the exact lock.
COPY apps/ukun-web/package.json apps/ukun-web/package-lock.json ./
RUN npm ci

# The whole directory, e2e/ and playwright.config.ts included, and that is not an oversight: the
# build script is `tsc -b && vite build`, and tsconfig.json references tsconfig.e2e.json, so leaving
# those two out fails the type build before Vite is ever reached. Nothing of it survives this stage
# — the runtime below copies dist/ and nothing else.
COPY apps/ukun-web/ ./
RUN npm run build


FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

# Build scripts first, sources after, so that editing code does not invalidate the dependency
# resolution layer. EVERY module script is needed even to build one service: settings.gradle.kts
# declares four projects and Gradle has to be able to configure every one of them. Adding a project
# there without adding its COPY here breaks this image, and `tilt up` will not notice — it builds
# from Dockerfile.dev instead.
#
# buildSrc carries the convention plugin the three services apply, and Gradle compiles it before
# anything else — without it the build cannot even be configured.
COPY gradlew ./
COPY gradle/ gradle/
COPY settings.gradle.kts build.gradle.kts ./
COPY buildSrc/ buildSrc/
COPY services/ukun-api/build.gradle.kts     services/ukun-api/
COPY services/ukun-auth/build.gradle.kts    services/ukun-auth/
COPY services/ukun-gateway/build.gradle.kts services/ukun-gateway/
COPY test-support/ukun-test-support/build.gradle.kts test-support/ukun-test-support/

COPY services/ukun-gateway/src services/ukun-gateway/src

# A BuildKit cache mount holds both the dependency cache and the Gradle distribution the wrapper
# downloads, so neither is re-fetched on every build.
#
# --no-daemon is right here and wrong in the Tiltfile: this container is single-use, so a daemon
# would only add startup cost and then be thrown away with the layer.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain -q :ukun-gateway:bootJar

# Boot 3.3+ layered extraction (`layertools` is obsolete). Splits the fat jar into four
# directories so dependencies — the heavy, rarely changing part — get their own Docker layer.
# The jar left in application/ is a thin jar that references the extracted libs, which is why
# the runtime stage can still start it with a plain `java -jar`.
RUN java -Djarmode=tools -jar services/ukun-gateway/build/libs/app.jar \
        extract --layers --destination /extracted


FROM eclipse-temurin:25-jre-alpine

# Never run the JVM as root. Alpine is also what makes the HEALTHCHECK below work without
# installing anything: it ships busybox wget, and no JRE base image ships curl.
RUN addgroup -S app && adduser -S -G app app

WORKDIR /opt/app
COPY --from=builder --chown=app:app /extracted/dependencies/          ./
COPY --from=builder --chown=app:app /extracted/spring-boot-loader/    ./
COPY --from=builder --chown=app:app /extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /extracted/application/           ./

# The SPA build, at the path `ukun.gateway.web-root` already defaults to (file:/opt/app/web/). Until
# this stage existed the directory was simply absent and the fallback answered 404; SpaFallbackConfig
# checks exists() on every request rather than at startup, so neither side had to know which of the
# two situations it was in.
COPY --from=web --chown=app:app /web/dist/ ./web/

USER app
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
    CMD wget -q -O - http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# Fixed jar name, guaranteed by `bootJar { archiveFileName = "app.jar" }` in the convention plugin.
# The legacy
# images use `java -jar /opt/app/*.jar` in exec form, where no shell expands the glob — it only
# works because the file is literally named `*.jar` (defect #16, docs/03-analisis-y-patrones.md).
ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]
