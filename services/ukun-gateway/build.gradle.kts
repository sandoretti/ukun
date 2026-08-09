// Gateway / BFF. Sustituye a nginx y es el único punto de entrada público (ADR-0003).
//
// Es el único servicio que necesita el BOM de Spring Cloud, de ahí que se importe aquí y no en el
// convention plugin.

plugins {
    id("ukun-java-conventions")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.cloud.bom))

    implementation(libs.spring.cloud.starter.gateway.server.webflux)
    implementation(libs.spring.boot.starter.actuator)

    testImplementation(libs.spring.boot.starter.test)
}
