# ADR-0024: Estado compartido en Postgres para desplegar con más de una réplica

## Estado
Aceptado — revisa la cuarta consecuencia de ADR-0003 y, de ADR-0023, su tabla de topología y los puntos 2, 3 y 4 de su lista de seis

## Contexto
Tres documentos de este repositorio prometen lo mismo casi con las mismas palabras. ADR-0003: *«si en el futuro hace falta más de una réplica, se añade Redis — no antes»*. `docs/08-identidad-y-seguridad.md` §4: *«en memoria mientras haya una sola réplica; Redis solo si algún día hay más de una»*. `docs/01-arquitectura.md` §2, que es la versión más concreta: *«Redis entra cuando haya una razón real de despliegue con más de una, no antes (issue #118)»*. La regla 5 de la skill `spring-gateway` lo repite como norma operativa.

Ese día llegó, y llegó peor de lo que las tres frases suponen. Al revisar si el checkout de la issue #34 aguanta réplicas en paralelo (issue #118) resultó que **`ukun-api` sí** —el arbitraje del stock y de la idempotencia está en Postgres, no en la JVM— pero que lo que impide desplegar con más de una réplica no es *la sesión*: son cinco cosas, y solo dos son la sesión.

1. **La clave de firma se genera al arrancar.** `JwkSourceConfig` hace `KeyPairGenerator.getInstance("RSA")` con un `keyID` aleatorio en cada arranque. Con dos réplicas hay dos JWKS distintos detrás del balanceador y `401` intermitentes. **Y duele con una sola réplica:** cada despliegue invalida todos los tokens emitidos. ADR-0023 lo recoge en el punto 2 de su lista, con el matiz de que allí deja de ser deuda teórica porque cada revisión de la container app la ejerce.
2. **La sesión en memoria, en el gateway y también en `ukun-auth`.** Las tres frases de arriba hablan solo del gateway. `ukun-auth` tiene la suya —el `JSESSIONID` del formulario de login y del endpoint de autorización— y si el callback del código cae en otra réplica, el flujo se rompe igual.
3. **La purga diaria que `docs/10-modelo-de-datos.md` §4 da por supuesta no existe**, ni para `idempotency_key` ni para `checkout_attempt`. Las dos tablas crecen sin techo. Escrita como `DELETE … WHERE` es idempotente, así que N réplicas ejecutándola es desperdicio y no un fallo — pero es la clase de job que deja de ser inofensiva en cuanto uno mande un correo.
4. **Los `@Scheduled` corren en todas las réplicas.** Hoy los tres que hay son idempotentes o son estado por JVM. La convención es barata ahora y cara el día después de escribir el primer job que mueva dinero.
5. **El limitador de intentos de login es por réplica y su IP no discrimina.** `LoginAttemptBuckets` es un `ConcurrentHashMap` local, así que con N réplicas el presupuesto real es N×5/min; y `LoginRateLimiter` usa `getRemoteAddr()`, que detrás de un balanceador es la misma dirección para todo el mundo. `LoginEventListener` ya razona por qué hoy **no** se lee `X-Forwarded-For` —cabecera que pone quien llama, sin proxy de confianza configurado—, y con un balanceador delante esa decisión correcta pasa a ser un requisito de configuración. Este punto **duele hoy con una sola réplica**, igual que el primero: detrás del ingress de ACA el limitador ya está contando mal.

Y una restricción que no existía cuando se escribieron esas tres frases. **ADR-0023 despliega sobre Azure for Students**: 100 USD para doce meses, con límite de gasto que suspende los recursos en vez de facturarlos, y con un Postgres Flexible Server B1ms ya presupuestado y encendido. Un Azure Cache for Redis es coste fijo mensual sobre ese crédito, para guardar una sesión y dos mapas de contadores. Es el mismo criterio con el que `docs/03-analisis-y-patrones.md` §4 descartó Redis para caché de catálogo y ADR-0014 lo descartó como agregador de logs: no se descarta por malo, se descarta por desproporcionado al volumen real de este sistema.

Queda un dato técnico que decide la forma de la solución y que conviene no descubrir a mitad de la implementación. **Spring Session 4.x publica exactamente tres artefactos** —`spring-session-core`, `spring-session-data-redis` y `spring-session-jdbc`—, comprobado en el BOM que gestiona Spring Boot 4.1 y no supuesto. `spring-session-jdbc` es **solo servlet**: no existe `ReactiveSessionRepository` sobre JDBC. Eso parte el problema de la sesión en dos mitades desiguales — `ukun-auth` es una dependencia y una tabla, el gateway es código propio.

## Decisión
**El estado compartido vive en Postgres, en la base del servicio que lo genera, y no se añade ningún recurso de infraestructura.**

| Estado | Servicio | Dónde vive | Con qué |
|---|---|---|---|
| Clave de firma de los tokens | auth | secreto de la aplicación | clave PEM por variable de entorno, sin valor por defecto |
| Sesión del login y del endpoint de autorización | auth | `ukun_auth` | `spring-session-jdbc` |
| Sesión del BFF (`SecurityContext` + `OAuth2AuthorizedClient`) | gateway | `ukun_gateway`, base nueva | `ReactiveSessionRepository` propio |
| Presupuesto del limitador de intentos de login | auth | `ukun_auth` | proxy manager de PostgreSQL de Bucket4j |
| Presupuesto del limitador de `/api/**` | gateway | en memoria, **sin cambio** | — |
| Exclusión mutua de los `@Scheduled` | auth y api | su propia base | ShedLock con proveedor JDBC |

Cada tabla de esa columna la crea **un changeset de Liquibase**, nunca el script DDL que trae la librería: la regla 3 de la skill `liquibase` no admite excepción por venir el DDL de fuera.

### La clave de firma no es un problema de réplicas
Es el primero de la lista porque es el único que también es un defecto a una sola réplica, y el más barato de arreglar. La clave sale de configuración con el mismo patrón que ya usan `UKUN_OAUTH2_GATEWAY_SECRET` y `UKUN_OAUTH2_API_SECRET`: **sin valor por defecto**, de modo que el servicio se niega a arrancar antes que arrancar con una clave conocida. El `kid` es **estable y derivado de la propia clave** (thumbprint RFC 7638), no configurado ni aleatorio: así dos réplicas con la misma clave publican el mismo `kid` sin ponerse de acuerdo en nada, y cambiar la clave cambia el `kid` solo.

**Rotación con solapamiento de `kid`: no ahora.** Un `JWKSet` de dos claves, la nueva firmando y la vieja aún publicada, es la forma correcta de rotar sin cerrar sesiones — y es trabajo que no compra nada mientras no haya una rotación real que gestionar. Es la misma línea que ADR-0023 traza para Key Vault, y por el mismo motivo.

### Los tokens del BFF no van a la sesión por sí solos

Sacar la sesión del heap no basta en este servicio, y es la clase de detalle que solo aparece
levantando el entorno. Boot autoconfigura el `ServerOAuth2AuthorizedClientRepository` sobre un
`ReactiveOAuth2AuthorizedClientService` **en memoria**, así que el access token y el refresh token
siguen en un mapa de esa JVM aunque la sesión esté en Postgres. Medido con dos réplicas levantadas:
tras reiniciar el gateway la sesión se restaura —el log de acceso ya trae el `accountId`— y la primera
petición a `/api/**` responde `302` hacia `/oauth2/authorize` en vez de su respuesta, porque no hay
token que relevar. Parece «la sesión no sobrevivió» y no lo es.

Se declara a mano un `WebSessionServerOAuth2AuthorizedClientRepository`, que es el que guarda los
clientes autorizados **dentro de la sesión** y por tanto dentro de `web_session.attributes`. Es una
línea, y sin ella la mitad de este ADR es falsa para el gateway. Lo vigila un test que aserta el tipo
del bean y no su comportamiento, porque perderlo es un borrado y un borrado aquí es silencioso: Boot
vuelve a poner el suyo.

### El gateway se queda a una réplica, y aun así gana una sesión persistente
Es la parte de este ADR que parece incoherente si no se escribe, así que se escribe. El gateway mantiene `max=1`; sus buckets de `/api/**` siguen en memoria porque a una réplica son correctos. Su sesión **no**: un repositorio persistente no le compra concurrencia, le compra **sobrevivir a un despliegue**, que es exactamente el mismo argumento que la clave de firma del punto 1 y que en un entorno efímero como el de ADR-0023 se ejerce en cada demo. De propina deja el `max>1` a un cambio de plantilla en vez de a un cambio de diseño.

Dicho al revés, por si alguien lo lee con prisa: **los buckets del gateway no están pendientes de arreglar, están decididos.** Si el gateway pasa algún día a más de una réplica, ese es el momento de moverlos, y el proxy manager de Bucket4j ya estará en el proyecto por el lado de `ukun-auth`.

### Las sesiones pegajosas son el paso intermedio, y conviene saber lo que cuestan
El ingress de Azure Container Apps sabe hacer afinidad de sesión por cookie (`stickySessions.affinity`). Es gratis, es una línea de plantilla y resuelve las dos sesiones sin escribir una clase. **No es la decisión**, y las tres razones son las mismas tres veces: un despliegue rodante sigue cerrando todas las sesiones, una réplica que cae se lleva las suyas, y la afinidad no reparte ningún otro estado —el presupuesto del limitador sigue siendo N×5/min por más pegajosa que sea la sesión—. Queda escrita aquí como lo que es: la red que cubre el hueco si el repositorio propio del gateway diera guerra, y el paso que se configura primero si hiciera falta subir réplicas antes de que ese código esté.

## Consecuencias
- **El gateway deja de ser un servicio sin persistencia.** Gana un datasource, Liquibase y una base propia `ukun_gateway`, que crea `scripts/postgres-init-databases.sh` igual que ya crea `ukun_auth`. Es el coste más alto de este ADR y el que habría evitado Redis: un BFF que enruta y autentica pasa a tener esquema. A cambio, el número de recursos de infraestructura del sistema no cambia.
- **La salud del gateway pasa a depender de Postgres.** `/actuator/health` gana el indicador del datasource, así que una base caída deja al único punto de entrada del sistema en `DOWN`. Es correcto —sin base no hay sesión, y sin sesión no hay nadie autenticado— pero conviene saberlo antes de que un reinicio de la base parezca un fallo del gateway.
- **El repositorio de sesión reactivo es código de este repositorio, no una dependencia.** Hay que mantenerlo, probarlo y entender su expiración. Es la consecuencia directa de que Spring Session no publique esa pieza, y la razón por la que este ADR deja escrita la alternativa de las sesiones pegajosas en vez de darla por superada.
- **La cookie de sesión de `ukun-auth` hay que fijarla a `JSESSIONID` a mano**, y esto costó un login roto entero antes de encontrarlo. Spring Session llama `SESSION` a la suya, que es el mismo nombre que ya usa la sesión del gateway —el de WebFlux por defecto—, y **las cookies no distinguen puerto**: sobre `localhost` las dos son una sola y la segunda pisa a la primera. El síntoma no se parece a lo que es: el navegador vuelve al callback del gateway con la cookie de `ukun-auth`, el gateway no encuentra su petición de autorización ni su `code_verifier` de PKCE, y responde `/login?error` sin que ningún log lo explique. En el despliegue de ADR-0023 los dos servicios tienen FQDN distintos y no colisionarían, pero `tilt up` sí, y el nombre se fija en `application.yaml` para que no dependa de la topología.
- **JDBC es bloqueante y el gateway es reactivo.** Todo acceso a esa base va descargado a un `Scheduler` acotado; una llamada bloqueante en el bucle de eventos de un servicio que es el único punto de entrada del sistema no degrada una petición, las degrada todas.
- **La tabla de sesión del gateway guarda el `OAuth2AuthorizedClient`, o sea el access y el refresh token.** Es material tan sensible como `account.password_hash`: no puede aparecer en ningún `log.*(...)` (`docs/11-observabilidad-y-logging.md` §4) y su serialización no es un detalle de implementación, es superficie de seguridad.
- **ADR-0023 cambia.** `ukun-auth` y `ukun-api` dejan de estar limitados a una réplica y su `max` deja de ser 1 por este motivo; el gateway conserva `max=1` con su razón escrita al lado, que es lo que impide que alguien lo suba «porque los otros dos ya se puede». La plantilla de Bicep no existe todavía —es trabajo de la issue #55—, así que este ADR fija el requisito y esa issue lo aplica.
- **Un `@Scheduled` nuevo deja de ser gratis.** A partir de aquí hay que decidir por cada uno si lleva lock, y la regla vive en las skills `spring-auth` y `spring-api` para que la decisión se tome al escribirlo y no al depurarlo. La mitad que se olvida no es la de poner el lock: es que un job que solo toca estado de su propia JVM —los dos que purgan mapas de buckets— **no debe llevarlo nunca** y tiene que correr en todas las réplicas.
- **`forward-headers-strategy` entra en `ukun-auth` y en el gateway**, y con él la noción de proxy de confianza que hasta hoy este sistema no tenía. Es lo que convierte el comentario de `LoginEventListener` en configuración: sin ella, el limitador compartido de este ADR contaría todas las peticiones contra la misma dirección y no limitaría nada.
- **Dos réplicas arrancando a la vez se pelean por el lock de Liquibase**, y está bien que lo hagan: la segunda escribe `Waiting for changelog lock....`, espera a que la primera termine de migrar y sigue. Es el mecanismo funcionando, no un fallo — pero conviene saberlo antes de ver esa línea en un despliegue rodante y pensar que el arranque se ha colgado. Lo que sí sería un problema es que la primera muriese a mitad de la migración: el lock queda tomado y la segunda agota su espera. No se añade nada para eso; se escribe aquí para que quien lo vea sepa qué mirar.
- **El día que Redis entre por otro motivo** —caché de verdad, colas, algo que este sistema hoy no tiene— esta decisión se revisa entera, no a trozos: tener Redis y seguir con una sesión sobre JDBC escrita a mano sería lo peor de las dos opciones.

## Alternativas descartadas
- **Redis, que es lo que llevaban prometiendo ADR-0003, `docs/01` §2 y `docs/08` §4.** Resuelve las dos sesiones y los dos limitadores con una dependencia y sin código propio, que es una ventaja real y la parte que más duele perder. Se descarta por el presupuesto de ADR-0023: un recurso gestionado más, de coste fijo mensual, sobre un crédito de 100 USD anuales cuyo límite de gasto apaga el entorno. Si esto dejara de ser un proyecto de estudio, es lo primero que se revisaría — junto con el Private Endpoint de Postgres, por el mismo motivo y en el mismo ADR.
- **Solo sesiones pegajosas, sin almacén de ningún tipo.** Es la opción más barata con diferencia y cubre el escenario que el issue nombra. Se descarta como decisión —no como red, ver arriba— porque deja los tres costes intactos: sesiones cerradas en cada despliegue rodante, sesiones perdidas con la réplica que las tenía, y un presupuesto de limitador que sigue multiplicándose por el número de réplicas.
- **Sesión sin estado en el gateway, con los tokens en una cookie cifrada.** Elimina el problema en vez de resolverlo y no necesita ni base ni afinidad. Se descarta porque mete el refresh token en el navegador, que es literalmente lo que ADR-0003 existe para evitar: cifrada o no, la cookie viaja y se guarda en el cliente. Y el tamaño no ayuda: un access token, un refresh token y el `SecurityContext` no caben con holgura en los 4 KB de una cookie.
- **Que el gateway use la base de `ukun-api` o la de `ukun_auth` en vez de una propia.** Ahorra la base nueva y rompe la frontera que la regla 6 de la skill `liquibase` sostiene: dos servicios escribiendo el mismo historial de cambios, y un servicio leyendo tablas de otro sin API por medio, que es exactamente el defecto estructural del que salió este refactor.
- **Bucket4j sobre la base compartida para el limitador del gateway también.** Una vez el gateway tiene base, tentador y coherente. Se descarta hoy porque a `max=1` no compra nada y sí cuesta: son buckets que se consultan **en cada petición a `/api/**`**, no una vez por sesión, y eso es tráfico bloqueante contra la base en el camino de todas las peticiones del sistema.
- **Azure Key Vault para la clave de firma.** Es donde acabará el día que haya rotación real que gestionar. Hoy los secretos de la propia container app cubren el caso, se inyectan como variables de entorno y no añaden un recurso al presupuesto — que es exactamente lo que ADR-0023 ya decidió para los dos secretos de cliente OAuth2.
