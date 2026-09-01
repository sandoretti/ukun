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

    // Correlación entre los tres procesos y exportación por OTLP (docs/11 §5). Aquí es donde empieza
    // el trace: este servicio es el único que habla con el navegador, así que es quien acuña el
    // traceId que auth y api heredan por la cabecera `traceparent`.
    implementation(libs.spring.boot.starter.opentelemetry)
    runtimeOnly(libs.micrometer.tracing.bridge.otel)

    // El filtro grueso por rol de ADR-0020 y las cuatro cabeceras de docs/08 §8.
    implementation(libs.spring.boot.starter.security)

    // El BFF de ADR-0003: oauth2Login, la sesión y el relevo del token hacia ukun-api. Además de las
    // clases, es lo que enciende dos autoconfiguraciones condicionadas a su presencia — la del
    // TokenRelayGatewayFilterFactory de Spring Cloud Gateway, que necesita un
    // ReactiveOAuth2AuthorizedClientManager en el contexto, y la del propio cliente.
    implementation(libs.spring.boot.starter.security.oauth2.client)

    // El limitador de tasa de docs/08 §8, el mismo artefacto que ya usa ukun-auth para el límite de
    // intentos de login. Ojo al nombre: desde 8.10 Bucket4j se publica partido por JDK y el
    // `bucket4j-core` a secas que sale en cualquier ejemplo antiguo ya no existe (libs.versions.toml).
    implementation(libs.bucket4j.core)

    // La sesión del BFF fuera del heap, en su propia base ukun_gateway (ADR-0024, issue #118). Tres
    // piezas y ninguna es opcional: el núcleo de Spring Session da el contrato reactivo (la
    // implementación sobre JDBC la escribe este repo — no existe), JdbcClient da el acceso, y
    // Liquibase es el dueño del esquema igual que en los otros dos servicios.
    //
    // Es la primera vez que este servicio tiene persistencia, y ADR-0024 lo declara como el coste más
    // alto de esa decisión: un BFF que enruta y autentica pasa a tener esquema. Lo que compra es que
    // la sesión sobreviva a un despliegue, no concurrencia — el gateway sigue a una réplica.
    implementation(libs.spring.session.core)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.liquibase)
    runtimeOnly(libs.postgresql)

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

    // Postgres de verdad para la sesión de ADR-0024. Desde que este servicio tiene esquema, arrancar
    // su contexto exige una base: la alternativa sería un repositorio en memoria solo para los tests,
    // que es asertar sobre una implementación que no se despliega. Nunca H2 (skill `testing`).
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)

    // Las siete reglas de docs/03 §3, las mismas que ukun-api y ukun-auth. Varias no tienen nada que
    // comprobar en un servicio sin entidades, y se aplican igual: el conjunto entra entero desde el
    // primer commit, que es lo que evita discutir cuál toca en cada servicio.
    testImplementation(project(":ukun-test-support"))
}
