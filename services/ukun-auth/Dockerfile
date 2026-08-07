# syntax=docker/dockerfile:1
#
# Production image for ukun-auth. This is NOT the image the local dev loop uses — that one is
# Dockerfile.dev, driven by Tilt with live_update. See docs/06-entorno-local-tilt.md.
#
# The build context is the REPOSITORY ROOT, not this directory: the aggregator pom.xml lives
# there and the reactor needs it. Build with:
#
#     docker build -f services/ukun-auth/Dockerfile -t ukun/ukun-auth:0.1.0-SNAPSHOT .
#
# The per-service ignore list is services/ukun-auth/Dockerfile.dockerignore, which BuildKit
# picks up in place of any .dockerignore at the context root.

FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

# POMs first, sources after, so that editing code does not invalidate the dependency
# resolution layer. All three module POMs are needed even for a single -pl build: Maven has
# to read the whole reactor declared by the aggregator.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY services/ukun-api/pom.xml     services/ukun-api/
COPY services/ukun-auth/pom.xml    services/ukun-auth/
COPY services/ukun-gateway/pom.xml services/ukun-gateway/

COPY services/ukun-auth/src services/ukun-auth/src

# A BuildKit cache mount instead of `dependency:go-offline`: go-offline re-downloads the whole
# dependency tree whenever a single line of the POM changes, the cache mount does not.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -pl services/ukun-auth -am -DskipTests package

# Boot 3.3+ layered extraction (`layertools` is obsolete). Splits the fat jar into four
# directories so dependencies — the heavy, rarely changing part — get their own Docker layer.
# The jar left in application/ is a thin jar that references the extracted libs, which is why
# the runtime stage can still start it with a plain `java -jar`.
RUN java -Djarmode=tools -jar services/ukun-auth/target/app.jar \
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
EXPOSE 9000

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
    CMD wget -q -O - http://127.0.0.1:9000/actuator/health | grep -q '"status":"UP"' || exit 1

# Fixed jar name, guaranteed by <finalName>app</finalName> in the root pom.xml. The legacy
# images use `java -jar /opt/app/*.jar` in exec form, where no shell expands the glob — it only
# works because the file is literally named `*.jar` (defect #16, docs/03-analisis-y-patrones.md).
ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]
