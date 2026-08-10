// Gateway / BFF. Sustituye a nginx y es el único punto de entrada público (ADR-0003).
//
// Es el único servicio que necesita el BOM de Spring Cloud, de ahí que se importe aquí y no en el
// convention plugin.
//
// No hay dominio aquí y no lo habrá: este servicio enruta, autentica y aplica políticas
// transversales (skill `spring-gateway`, "Qué no construir aquí"). De ahí que no aparezcan ni JPA,
// ni MapStruct, ni Lombok — no hay entidad, DTO ni servicio que anotar.

plugins {
    id("ukun-java-conventions")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.cloud.bom))

    // Arrastra spring-boot-starter-webflux, así que no se declara aparte: el pom del starter de
    // Gateway lo pide a 4.0.7 y el BOM de Boot lo eleva a 4.1.0, que es la versión del resto.
    implementation(libs.spring.cloud.starter.gateway.server.webflux)
    implementation(libs.spring.boot.starter.actuator)

    // El filtro grueso por rol de ADR-0020 y las cuatro cabeceras de docs/08 §8.
    implementation(libs.spring.boot.starter.security)

    // El BFF de ADR-0003: oauth2Login, la sesión y el relevo del token hacia ukun-api. Además de las
    // clases, es lo que enciende dos autoconfiguraciones condicionadas a su presencia — la del
    // TokenRelayGatewayFilterFactory de Spring Cloud Gateway, que necesita un
    // ReactiveOAuth2AuthorizedClientManager en el contexto, y la del propio cliente.
    implementation(libs.spring.boot.starter.security.oauth2.client)

    // Genera spring-configuration-metadata.json para GatewayProperties: sin él el IDE marca
    // `ukun.gateway.api-uri` como propiedad no resuelta.
    annotationProcessor(platform(libs.spring.boot.bom))
    annotationProcessor(libs.spring.boot.configuration.processor)

    // Trae WebTestClient con spring-test, que es todo lo que hace falta: a diferencia de ukun-api,
    // aquí no se declara spring-boot-resttestclient. RestTestClient es el cliente que Boot 4 ofrece
    // contra un servidor real, pero los mutadores de spring-security-test que necesita
    // RouteAuthorizationIT solo existen para WebTestClient.
    testImplementation(libs.spring.boot.starter.test)

    // SecurityMockServerConfigurers.mockUser(), para probar /api/admin/** por rol sin que exista
    // todavía ningún mecanismo de autenticación (issue #28).
    testImplementation(libs.spring.security.test)

    // Las siete reglas de docs/03 §3, las mismas que ukun-api y ukun-auth. Varias no tienen nada que
    // comprobar en un servicio sin entidades, y se aplican igual: el conjunto entra entero desde el
    // primer commit, que es lo que evita discutir cuál toca en cada servicio.
    testImplementation(project(":ukun-test-support"))
}
