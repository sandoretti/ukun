# syntax=docker/dockerfile:1
#
# Production image for ukun-api. This is NOT the image the local dev loop uses — that one is
# Dockerfile.dev, driven by Tilt with live_update. See docs/06-entorno-local-tilt.md.
#
# The build context is the REPOSITORY ROOT, not this directory: the aggregator build lives
# there and Gradle needs it. Build with:
#
#     docker build -f services/ukun-api/Dockerfile -t ukun/ukun-api:0.1.0-SNAPSHOT .
#
# The per-service ignore list is services/ukun-api/Dockerfile.dockerignore, which BuildKit
# picks up in place of any .dockerignore at the context root.

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

COPY services/ukun-api/src services/ukun-api/src

# A BuildKit cache mount holds both the dependency cache and the Gradle distribution the wrapper
# downloads, so neither is re-fetched on every build.
#
# --no-daemon is right here and wrong in the Tiltfile: this container is single-use, so a daemon
# would only add startup cost and then be thrown away with the layer.
#
# Tests are skipped by building bootJar directly rather than `build` — they need a Docker daemon
# (Testcontainers) and they run in the build pipeline, not in the image.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain -q :ukun-api:bootJar

# Boot 3.3+ layered extraction (`layertools` is obsolete). Splits the fat jar into four
# directories so dependencies — the heavy, rarely changing part — get their own Docker layer.
# The jar left in application/ is a thin jar that references the extracted libs, which is why
# the runtime stage can still start it with a plain `java -jar`.
RUN java -Djarmode=tools -jar services/ukun-api/build/libs/app.jar \
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

USER app
EXPOSE 8081

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
    CMD wget -q -O - http://127.0.0.1:8081/actuator/health | grep -q '"status":"UP"' || exit 1

# Fixed jar name, guaranteed by `bootJar { archiveFileName = "app.jar" }` in the convention plugin.
# The legacy images use `java -jar /opt/app/*.jar` in exec form, where no shell expands the glob — it
# only works because the file is literally named `*.jar` (defect #16, docs/03-analisis-y-patrones.md).
ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]
