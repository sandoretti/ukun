// Servicio de identidad. Solo esqueleto en v0.1 (docs/04-roadmap.md); el Authorization Server, el
// baseline de Liquibase de ukun_auth y Bucket4j llegan en v0.4.
//
// Ninguna dependencia declara versión: todas las gestiona el BOM de Spring Boot importado abajo.

plugins {
    id("ukun-java-conventions")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform(libs.spring.boot.bom))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)

    testImplementation(libs.spring.boot.starter.test)
}
