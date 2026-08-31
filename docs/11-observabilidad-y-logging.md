# Observabilidad y logging de acciones

> **Fuente de verdad de:** la lista cerrada de eventos de negocio, qué nunca se loguea, el formato/correlación de logs y la tabla `audit_log`.
> **Léelo si:** vas a emitir un log, a decidir si una acción necesita auditoría, o a configurar tracing.
> **No decide:** el contrato de errores (`12-error-contract.md`), ni las reglas ArchUnit completas (`03-analisis-y-patrones.md` §3), ni los defectos del legacy que motivan todo esto (`03` §1).
> **Idioma:** identificadores, nombres de evento y esquema en inglés (ADR-0018).

Responde al requisito no funcional de `02-requisitos.md` §4 ("los logs de negocio no contienen PII innecesaria ni secretos") con un diseño concreto de **qué** se loguea, **qué nunca** se loguea y **cómo se hace cumplir sin depender de disciplina** — no solo qué se prohíbe.

## 1. Dos cosas distintas que no hay que mezclar

- **Log de aplicación (diagnóstico).** Responde "¿por qué falló esto?". Volumen alto, vida corta, se puede perder sin consecuencias. Su consumidor es un desarrollador con el `traceId` de una petición.
- **Rastro de auditoría.** Responde "¿quién cambió el stock de este producto el martes?" o "¿desde cuándo tiene este empleado el flag admin?". No se puede perder, se consulta por recurso, y su consumidor natural es un admin desde una pantalla, no un desarrollador con `grep`.

**Decisión: los dos, pero solo una parte va a tabla.** Criterio: a la tabla `audit_log` van únicamente los eventos en los que alguien modifica un recurso que no es suyo o que otro puede reclamar después — es donde la pregunta "¿quién hizo esto?" se llega a formular de verdad en un negocio de este tamaño.

Qué **no** va a tabla, y por qué: los eventos de autenticación. Un login fallido es diagnóstico y señal de seguridad agregada ("¿hay fuerza bruta?"), y esa pregunta se responde contando eventos en una ventana de tiempo, no leyendo filas. Además viven en `ukun-auth`, que `08-identidad-y-seguridad.md` §2 define como deliberadamente anémico — meterle una tabla de auditoría de negocio contradice esa frontera.

## 2. La tabla `audit_log` (en `ukun_app`, v0.7 — ver ADR-0014)

```sql
CREATE TABLE audit_log (
  id               BIGSERIAL PRIMARY KEY,
  occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actor_account_id UUID         NOT NULL,
  actor_role       VARCHAR(20)  NOT NULL,
  action           VARCHAR(50)  NOT NULL,   -- STOCK_UPDATED, PRODUCT_DELETED...
  resource_type    VARCHAR(30)  NOT NULL,   -- product | product_type | store_stock | employee | customer | customer_order
  resource_id      VARCHAR(64)  NOT NULL,   -- texto: mismo campo para ids numéricos y UUIDs
  details          JSONB,                   -- {"before":{"quantity":5},"after":{"quantity":3}}
  trace_id         VARCHAR(32)
);
CREATE INDEX ix_audit_log_resource ON audit_log (resource_type, resource_id, occurred_at DESC);
CREATE INDEX ix_audit_log_actor    ON audit_log (actor_account_id, occurred_at DESC);
```

`trace_id` es la pieza que impide que sean dos sistemas separados: desde una fila de auditoría se salta al log completo de esa petición, y al revés.

`resource_type` lleva seis valores y no cinco. La primera redacción listaba cinco, escrita antes de que existiera RF-13; los tres eventos `product_type.*` de §3 están marcados como auditables y renombrar un tipo cambia lo que ve el catálogo público entero, así que `product_type` es un recurso del rastro como los demás.

**Por qué tabla y no fichero para esta mitad:** la consulta natural es "todo lo que le ha pasado a este producto", que sobre ficheros exige un agregador (Loki/ELK) — infraestructura que este proyecto no tiene, y se descartaría por el mismo criterio que descartó Redis en `03-analisis-y-patrones.md` §4. Con una tabla es un `SELECT` con un índice y un endpoint. Se escribe **en la misma transacción** que el cambio, así que no puede quedar auditoría de algo que se revirtió.

**Descartado explícitamente: Hibernate Envers.** Versiona *entidades* (una tabla `_AUD` por entidad más `REVINFO`), no *acciones de negocio*: no distingue "un empleado editó el stock" de "el checkout descontó stock", que es justo la distinción que interesa. Y añade una tabla espejo por entidad para responder una pregunta que aquí solo se hace sobre cuatro recursos.

## 3. Tabla cerrada de eventos

| Evento | Nivel | Servicio | Campos | ¿Auditoría? |
|---|:-:|:-:|---|:-:|
| `auth.login.succeeded` | INFO | auth | `accountId`, `roles` | — |
| `auth.login.failed` | WARN | auth | `email` normalizado, `reason` (`CREDENTIALS`\|`DISABLED`), `ip` | — |
| `auth.logout` | INFO | gateway | `accountId` | — |
| `auth.token.issued` | DEBUG | auth | `accountId`, `clientId`, `scopes` | — |
| `auth.authorization.denied` | WARN | api | `accountId`, `role`, método+ruta, `reason` (`ROLE`\|`FOREIGN_RESOURCE`) | — |
| `signup.started` | INFO | api | `registrationId`, `email` | — |
| `signup.account.reserved` | INFO | api | `registrationId`, `accountId` | — |
| `signup.completed` | INFO | api | `registrationId`, `accountId`, `customerId` | ✅ |
| `signup.email.duplicate` | INFO | api | `email` | — |
| `signup.compensation` | **ERROR** | api | `registrationId`, `accountId`, `step` | — |
| `signup.activation.pending` | **ERROR** | api | `registrationId`, `accountId`, `attempts` | — |
| `order.created` | INFO | api | `orderId`, `customerId`, `total`, `lineCount`, `idempotencyKey` | ✅ |
| `order.rejected.stock` | WARN | api | `customerId`, `productId`, `requested`, `available` | — |
| `order.rejected.empty_cart` | INFO | api | `customerId` | — |
| `order.idempotent.replay` | INFO | api | `customerId`, `idempotencyKey`, `orderId` | — |
| `stock.updated` | INFO | api | `productId`, `storeId`, `before`, `after`, `employeeId` | ✅ |
| `stock.deleted` | **WARN** | api | `productId`, `storeId`, `previousQuantity`, `employeeId` | ✅ |
| `stock.optimistic.conflict` | WARN | api | `productId`, `storeId`, `employeeId` | — |
| `stock.decremented.checkout` | DEBUG | api | `orderId`, `productId`, `before`, `after` | — |
| `product.created` | INFO | api | `productId`, `name`, `adminId` | ✅ |
| `product.updated` | INFO | api | `productId`, `changedFields` | ✅ |
| `product.deleted` | **WARN** | api | `productId`, `name`, `adminId` | ✅ |
| `product.deletion.rejected` | INFO | api | `productId`, `inOrders`, `inStock`, `inCarts` | — |
| `product.discontinued` | INFO | api | `productId`, `sku`, `adminId` | ✅ |
| `product.reactivated` | INFO | api | `productId`, `sku`, `adminId` | ✅ |
| `product.image.replaced` | INFO | api | `productId`, `previousKey`, `newKey` | — |
| `product.image.removed` | INFO | api | `productId`, `previousKey`, `adminId` | — |
| `product_type.created` | INFO | api | `productTypeId`, `name`, `adminId` | ✅ |
| `product_type.updated` | INFO | api | `productTypeId`, `previousName`, `newName`, `adminId` | ✅ |
| `product_type.deleted` | **WARN** | api | `productTypeId`, `name`, `adminId` | ✅ |
| `product_type.deletion.rejected` | INFO | api | `productTypeId`, `inProducts` | — |
| `employee.created` | INFO | api | `employeeId`, `accountId`, `storeId`, `isAdmin`, `adminId` | ✅ |
| `profile.updated` | INFO | api | `customerId`, `changedFields` (nombres, no valores) | ✅ |
| `profile.email.changed` | INFO | auth + api | `accountId` | ✅ (sin caso de uso) |
| `profile.password.changed` | INFO | auth | `accountId` | — |
| `favorite_store.changed` | INFO | api | `customerId`, `previousStoreId`, `newStoreId` | — |

**`profile.email.changed` no tiene todavía operación que lo emita.** No hay endpoint de cambio de correo en `docs/api/openapi/ukun-api.yaml` ni código que lo dispare: la fila describe el evento que *tendrá* esa operación cuando exista, no un hueco en la implementación de la auditoría. Los otros catorce ✅ sí están en uso desde v0.7 (`AuditedEvent`, ADR-0014), y `AuditedEventRegistryTest` compara esta tabla con ese enum para que documento y código no puedan separarse.

`product_type.updated` es el único evento que registra el valor **anterior y el nuevo** de un texto libre: renombrar un tipo cambia lo que ve todo el catálogo público (RF-13) y sin el nombre previo la línea de log no permite reconstruir qué pasó. Frente a `profile.updated`, que registra solo *qué campos* cambiaron y nunca sus valores, la diferencia es que un nombre de tipo no es dato personal.

**`adminId` es el `sub` del token, no el `id` de la fila de `employee`.** Vale para los seis eventos del back-office que lo llevan, y no es un atajo: la tabla `employee` la posee el módulo `staff`, y `catalog` no depende de él (ADR-0017 §1) — resolver el identificador de empleado sería una consulta y una dependencia de módulo para rellenar un campo de log. El UUID de cuenta es además el valor que ya une la línea de log, el MDC (§5) y `audit_log.actor_account_id`, que es exactamente la correlación para la que existe el campo. Quien lo lee es `CurrentActor`, en `shared`: nombra al actor, no autoriza nada — decidir si alguien *puede* sigue pasando por el módulo `identity` (`08-identidad-y-seguridad.md` §4).

Tres niveles que sorprenden y por qué: `stock.deleted`, `product.deleted` y `product_type.deleted` son **WARN**, no INFO. RF-09 distingue explícitamente "eliminar la fila de stock" de "poner el stock a 0" — es una acción destructiva y difícil de deshacer sin conocer el valor previo, y merece destacarse en el log en vez de diluirse entre los INFO.

### Lo que NO se loguea, por ruido

- Cualquier `GET` de catálogo, ficha, tiendas o listado de stock. Ya lo cubre la métrica `http.server.requests` de Micrometer (`04-roadmap.md` v1.0), que responde "¿cuántas y a qué latencia?" sin una línea por petición.
- Añadir/quitar del carrito: es un borrador que cambia decenas de veces por sesión y cuyo resultado final ya queda registrado en `order.created`.
- Cada consulta de perfil o de historial.
- El arranque de Spring más allá del INFO por defecto.

## 4. Qué nunca se loguea, y cómo se hace cumplir

**Prohibido:** contraseñas (en claro o hasheadas), tokens (access, refresh, authorization code, `code_verifier`), cookies de sesión, PAN completo, CVV, cabeceras `Authorization` y `Cookie`, y el cuerpo completo de `POST /api/signup` o `PUT /api/me/profile`.

Los tres casos reales del legacy (defectos #10, #20a y #20b, catalogados en `03-analisis-y-patrones.md` §1) no son tres descuidos independientes: **los tres salen de la misma causa estructural**, `@Data` sobre entidades cuyo `toString()` arrastra `contrasenna`, `tarjeta.numero` (PAN) y `tarjeta.cvv`. Eso es lo que hace que la primera capa de cumplimiento sea gratis.

### Cuatro capas, ninguna llamada "disciplina"

1. **Que no exista el objeto que se puede filtrar.** La prohibición ya acordada de `@Data` en entidades (`03-analisis-y-patrones.md` §2) elimina de raíz los casos #20a/#20b. Los DTOs con secreto sobrescriben `toString()` a mano:

   ```java
   public record SignupRequest(String email, String password, String name /* ... */) {
       @Override public String toString() {
           return "SignupRequest[email=" + Masks.email(email) + ", password=***]";
       }
   }
   public record CardData(String number, String cvv, YearMonth expiry) {
       @Override public String toString() {
           return "CardData[last4=" + Masks.last4(number) + ", cvv=***]";
       }
   }
   ```
   Un `record` sin `toString()` explícito imprime **todos** sus componentes — por eso esto no es opcional en los que llevan secreto, es la contrapartida de haber elegido records para los DTOs.

2. **ArchUnit lo hace fallar en CI.** Son las dos últimas reglas de la lista de `03-analisis-y-patrones.md` §3 (clase `Request`/`Command` con campo `password|token|cvv|number|pan` debe declarar `toString()`; ningún método de producción pasa un `@Entity` a `log.*(...)`). La segunda habría cazado los tres casos reales antes de llegar a `main`.

3. **Red final al serializar el log**: un `StructuredLoggingJsonMembersCustomizer` que enmascara lo que se cuele (`"password"\s*:\s*"[^"]*"`, `\b\d{13,19}\b`, `Bearer [A-Za-z0-9._-]+`). No es la defensa principal — es cara y frágil a base de regex, y el patrón de tarjeta no distingue un PAN de un epoch en milisegundos —, pero convierte un accidente futuro en un `***` en vez de en un incidente. Se registra con `logging.structured.json.customizer` y alcanza **todas** las cadenas que escribe el encoder: el mensaje, la traza de la excepción y los campos que `BusinessEvents` estampa en el MDC. Implementado en `SecretMaskingJsonCustomizer`, uno por servicio (ADR-0001, la misma razón por la que `BusinessEvents` está triplicado).

   **La primera redacción de esta capa decía `TurboFilter`, y era inejecutable**: `TurboFilter.decide(...)` devuelve un `FilterReply` — decide si el evento se registra — y no puede reescribir su mensaje. Queda anotado en vez de corregido en silencio porque el error es instructivo: el punto de extensión correcto depende del formato de salida, y este proyecto ya había elegido el suyo dos secciones más abajo.

   **Se activa solo donde la salida es JSON**, que es exactamente producción: vive en el mismo documento `!dev` que `logging.structured.format.console: ecs` (§5). En local no está, y no por descuido — un secreto mal logueado tiene que verse para que se corrija, no esconderse.

4. **Nada de logging de cuerpos.** Ni `CommonsRequestLoggingFilter` con `setIncludePayload(true)`, ni clientes HTTP en DEBUG en producción, ni `logging.level.org.springframework.security=DEBUG` en `ukun-auth` (a ese nivel Spring Security vuelca detalle de la autenticación). El gateway loguea método, ruta, estado y duración: nunca cuerpo ni `Authorization`/`Cookie`.

**Y en la tabla `audit_log`:** `details JSONB` es un campo abierto, la misma regla aplica. Para `profile.updated` se guardan los **nombres** de los campos cambiados, nunca sus valores (`{"fields":["address","card"]}`) — el "antes/después" de una dirección es PII y el de una tarjeta es PCI. Auditar *que* cambió la dirección es el requisito; auditar *cuál era* no lo es.

## 5. Formato y correlación

### Formato: structured logging nativo de Spring Boot, no `logstash-logback-encoder`

Desde Boot 3.4 existe structured logging integrado con los formatos `ecs`, `logstash` y `gelf`, configurable por propiedad, y sigue presente en Boot 4.1. Antes hacía falta traer `net.logstash.logback:logstash-logback-encoder` y un `logback-spring.xml` a mano; hoy es una línea:

```yaml
# application.yaml — documento por defecto, que en este repo ES el de producción
logging:
  structured:
    format:
      console: ecs
    json:
      customizer: dev.sandoretti.ukun.<servicio>.<paquete>.SecretMaskingJsonCustomizer
```

**Recomendación: nativo + formato ECS**, sobre `logstash`, porque nombra los campos igual que cualquier stack Elastic/OpenSearch al que se envíen algún día, y **incluye el MDC en el JSON sin configuración adicional** — que es la mitad del valor de este diseño. Traer `logstash-logback-encoder` hoy es exactamente el tipo de dependencia que ya no hace falta. Para enriquecer campos globalmente existe el punto de extensión oficial (`StructuredLoggingJsonMembersCustomizer`), sin encoder de terceros. *(Verificar el nombre exacto de la propiedad contra la doc de 4.1 al fijarla, según la política de `05-stack-y-versiones.md`.)*

En local, **sin** `logging.structured.*`: el patrón legible de siempre. Es una diferencia de perfil, no de dependencias. Vive en un documento `spring.config.activate.on-profile: "!dev"` de cada `application.yaml`, y **no hace falta inventar un perfil `prod`**: el documento por defecto ya es el de producción, igual que con `springdoc` o con `spring.liquibase.contexts`.

Que sea un documento aparte y no una propiedad arriba con un `console: ""` abajo es deliberado, aunque las dos formas funcionan —`DefaultLogbackConfiguration` elige entre `PatternLayoutEncoder` y `StructuredLogEncoder` con `StringUtils.hasLength(formato)`, así que la cadena vacía es exactamente «no estructurado», y es lo que usa la tanda de tests de Gradle—. La razón es que el formato y la máscara de §4 **tienen que ir juntos**: la máscara solo actúa sobre el JSON, de modo que un ECS activo sin su `customizer` es logging de producción sin la capa 3, y eso no lo nota nadie. En un solo documento no se pueden desalinear.

### Correlación: Micrometer Tracing

Con Micrometer Tracing en el classpath, Boot inyecta `traceId`/`spanId` en el MDC automáticamente cuando hay un span activo, y los formatos ECS/Logstash los emiten como campos. La propagación gateway → api → auth es la instrumentación estándar de W3C `traceparent`: cero código.

**Boot 4 introduce un starter oficial `spring-boot-starter-opentelemetry`** que agrupa la API de OpenTelemetry, el bridge de Micrometer Tracing y los exportadores OTLP sin arrastrar Actuator entero. Es la opción cuando llegue la observabilidad de verdad (v1.0).

- **Descartado el java-agent de OpenTelemetry**: instrumenta sin tocar código, pero mete un `-javaagent` en el arranque de cada contenedor y complica el `restart_container()` del live-update de Tilt (`06-entorno-local-tilt.md`), a cambio de traces que el bridge ya da sin ese coste.
- **Descartado Brave/Zipkin**: funciona y es la alternativa histórica, pero OTLP es el protocolo hacia el que va el ecosistema y el starter oficial de Boot 4 ya lo asume.

### Fases, ancladas al roadmap

| Release | Qué |
|:-:|---|
| v0.2–v0.4 | nada de tracing. Un solo servicio, log en consola, patrón legible. Instrumentar un salto entre procesos que aún no existe es coste sin señal |
| **v0.5** | `micrometer-tracing` en gateway, api y auth en cuanto el gateway habla con los dos. Aquí el `traceId` deja de ser decorativo: una petición del navegador cruza tres procesos, y sin él la saga de signup no se puede depurar. Muestreo 1.0 |
| **v1.0** | Hecho (issue #52). `spring-boot-starter-opentelemetry` sustituye al módulo de tracing en los tres servicios y trae con él `opentelemetry-exporter-otlp` y `micrometer-registry-otlp`: trazas **y** métricas por OTLP. El destino lo pone el entorno (`MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` y `MANAGEMENT_OTLP_METRICS_EXPORT_URL`), nunca el `application.yaml`, de modo que sin colector no se instala exportador y el servicio arranca igual. En local ese destino es el `otel-collector` de `compose.dev.yaml`; en producción, el de ADR-0023. Más `logging.structured.format.console=ecs` y la capa 3 de §4, ambos en el documento `!dev` |

### MDC: `accountId`, y poco más

Un filtro de servlet en `ukun-api` (y su equivalente reactivo en el gateway) que, tras validar el JWT, hace `MDC.put("accountId", jwt.getSubject())` y limpia **en el `finally`**.

- `accountId` sí: es el `sub`, un UUID opaco, no PII directa, y es la clave que une log y tabla de auditoría.
- `customerId`/`employeeId` sí, una vez resueltos: es la clave con la que se busca desde la pantalla de auditoría.
- `email` no: es PII y aparecería en cada línea de cada petición, incluidas las que no lo necesitan. Solo aparece en los tres eventos donde es el sujeto (`auth.login.failed`, `signup.*`).

El `finally` no es opcional: sin él, el pool de hilos de Tomcat filtra el `accountId` de un usuario a la petición siguiente de otro. Es un bug de correlación que en una investigación parece un bug de seguridad, y se tarda horas en descartar.

## 6. Dónde se emite cada evento

Coherente con la arquitectura por módulos (ADR-0017) y con los casos de uso explícitos de `03-analisis-y-patrones.md` §3:

| Familia | Dónde | Por qué ahí |
|---|---|---|
| `auth.login.*`, `auth.logout` | `ApplicationListener<AuthenticationSuccessEvent>` y `<AbstractAuthenticationFailureEvent>` en `ukun-auth` | Spring Security **ya publica** estos eventos con el resultado y la causa tipada. Escribirlos a mano en un controller es reimplementar el punto donde ya ocurren — literalmente lo que hace mal el `HomeController` legacy. Un listener **no puede** filtrar la contraseña porque el evento no la lleva: la corrección del defecto #10 sale de la elección de sitio, no de acordarse |
| `auth.authorization.denied` | `AuthorizationDeniedEvent` (para `@PreAuthorize`) **+** el `@RestControllerAdvice` que traduce `AccessDeniedException` a `403 ProblemDetail` (para la autorización de recurso: "esta tienda no es la tuya") | son las capas 2 y 3 de la tabla de `08-identidad-y-seguridad.md` §4; cada una tiene su punto natural y no se pueden unificar sin perder el motivo |
| Eventos de negocio (`order.*`, `stock.*`, `product.*`, `employee.created`, `profile.*`, `signup.*`) | en el caso de uso, dentro de su transacción | es el único sitio que conoce a la vez qué se pidió, qué se decidió y por qué. `order.rejected.stock` necesita `requested` y `available`, que solo existen dentro de `CreateOrderUseCase` |
| Escritura en `audit_log` | `AuditService.record(...)` llamado desde el mismo caso de uso, con `@Transactional(propagation = REQUIRED)` | si el cambio hace rollback, la auditoría también. `REQUIRES_NEW` dejaría filas de cambios que nunca ocurrieron; un `@TransactionalEventListener(AFTER_COMMIT)` perdería la auditoría si algo falla tras el commit |
| Excepciones no controladas | `@RestControllerAdvice`, un único `log.error` con la excepción completa y el `traceId` | evita el patrón "loguear y relanzar" que duplica cada stack trace en tres sitios |
| Acceso HTTP (método, ruta, estado, duración) | filtro del gateway, no de cada servicio | un solo punto de verdad para el tráfico; api y auth no lo repiten |

### Descartado: un `@Aspect` genérico de auditoría

Un `@Around` sobre una anotación `@Auditable` es tentador. El problema concreto: el argumento del método es el DTO de entrada, **no el estado anterior del recurso**. Un `stock.updated` emitido desde un aspecto no puede decir "de 5 a 3" — que es el 90% del valor de auditar un cambio de stock. Se acabaría pasando el "antes" por un `ThreadLocal`, y a eso ya no se le llama aspecto, se le llama complicación.

Un aspecto (o un filtro) sí vale para lo uniforme y sin contexto de negocio: poner y limpiar el MDC, medir latencia — para eso ya está el filtro descrito arriba.

Para no repetir la línea de log en cada caso de uso, un único emisor compartido, con logger propio para poder afinar su nivel sin tocar el resto del árbol:

```java
public final class BusinessEvents {
    private static final Logger log = LoggerFactory.getLogger("ukun.business");
    public static void emit(String event, Map<String, Object> fields) { /* MDC + log.info */ }
}
```

`logging.level.ukun.business=INFO` sobrevive aunque todo lo demás baje a WARN. Eso es lo que hace que "solo lo que afecta al usuario" sea una configuración y no una promesa.

## 7. Niveles y retención

| Nivel | Significado en este proyecto | Ejemplos |
|:-:|---|---|
| **ERROR** | algo se rompió y nadie más lo va a arreglar: requiere que un humano mire | `signup.compensation`, `signup.activation.pending`, `ukun-auth` inalcanzable, cualquier 500 no controlado |
| **WARN** | comportamiento esperado pero indeseable: el sistema respondió bien, pero el resultado es negativo para alguien | `auth.login.failed`, `order.rejected.stock`, `stock.optimistic.conflict`, `auth.authorization.denied`, `stock.deleted`, `product.deleted` |
| **INFO** | acción de negocio exitosa que afecta al usuario — la lista de §3 | `order.created`, `stock.updated`, `signup.completed`, `employee.created` |
| **DEBUG** | detalle interno útil solo mientras se depura | `auth.token.issued`, `stock.decremented.checkout`, SQL de Hibernate |
| **TRACE** | nunca en un entorno compartido | — |

Dos precisiones sobre esa fila, las dos aprendidas al escribirla en los `application.yaml` (issue #52):

- **`dev.sandoretti.ukun` es el paquete, no los loggers de negocio.** La primera redacción decía `ukun.*`, que se lee como si `ukun.business` y `ukun.access` colgaran de él. No cuelgan: llevan nombre propio precisamente para poder afinarlos sin tocar el árbol de clases, y por eso van nombrados uno a uno en el documento de producción.
- **`ukun.access` faltaba en esta tabla**, escrita antes de que el gateway existiera. Con `root: WARN` y sin nombrarlo, producción se quedaría sin el access log que §6 define como el único punto de verdad del tráfico HTTP.

Y una consecuencia de `root: WARN` que conviene conocer antes de encontrársela: **apaga también los `INFO` de arranque de Spring**, incluido el «Started UkunApiApplication in N seconds». Un servicio recién desplegado deja de anunciarse, y eso se parece mucho a un arranque fallido sin serlo — quien responde por él es `/actuator/health`, que es lo que sondea el contenedor.

Dos reglas que evitan la degeneración habitual:

- **Un `400` de validación no es un ERROR.** El legacy hace `log.error("Hay errores al intentar validar el producto: {}", result)`: un usuario que deja un campo vacío no es un fallo del sistema. Va a DEBUG, o a nada — el `ProblemDetail` ya se lo cuenta al usuario. Si todo es ERROR, ERROR no significa nada y nadie mira los logs.
- **Regla mecánica: un `4xx` nunca es ERROR; un `5xx` siempre lo es.** La aplica el `@RestControllerAdvice`, no cada handler por su cuenta.

### Local (Tilt) vs producción

| | Local (Tilt) | Producción |
|---|---|---|
| Formato | patrón legible con color | JSON ECS a stdout |
| Nivel raíz | `INFO`; `dev.sandoretti.ukun` en `DEBUG` | `WARN`; `ukun.business` en `INFO`, y `ukun.access` en el gateway |
| SQL | `spring.jpa.show-sql=false` siempre; para depurar, `org.hibernate.SQL=DEBUG` a mano y temporalmente | apagado |
| Spring Security | `DEBUG` permitido | prohibido (vuelca detalle de autenticación) |
| Máscara de secretos (`SecretMaskingJsonCustomizer`, §4 capa 3) | desactivada — un secreto mal logueado tiene que verse para arreglarse | activada, con el formato ECS que la lleva |
| Destino | stdout; la UI de Tilt agrupa por recurso | stdout; lo recoge el runtime del contenedor |
| Muestreo de traces | 1.0 | 1.0 mientras el volumen sea este |
| Exportación OTLP | trazas y métricas al `otel-collector` de `compose.dev.yaml` | al destino que fije el despliegue (ADR-0023) |
| Métricas por OTLP | encendidas por `compose.dev.yaml` | encendidas por el despliegue. **Apagadas en el `application.yaml`**: el default de Boot es `true` y Micrometer cae a `localhost:4318`, así que sin esa línea cada test y cada PR publicarían contra un puerto cerrado |
| Logs por OTLP | nunca | nunca (`management.logging.export.otlp.enabled: false`, §7) |

### Retención, sin inventar infraestructura que este proyecto no tiene

- **Log de aplicación:** a stdout, nunca a fichero desde la aplicación (`logging.file.name` sin usar). La rotación es responsabilidad del runtime: el ancla `x-log-rotation` de `compose.yaml` (`json-file`, `max-size: 10m`, `max-file: 3`, aplicada a los cinco servicios) evita el único riesgo real hoy, que un log llene el disco de la máquina de desarrollo — y pesa más desde que cada línea de producción es un JSON ECS y no el patrón corto. Un agregador (Loki + Grafana, o lo que traiga el destino de despliegue) es decisión de v1.0, no antes.
- **Tabla `audit_log`:** se conserva sin purga automática. A este volumen (pocos miles de filas al año), un job de borrado es más código que el problema que evita. Se revisa el día que la tabla se acerque a ~1M de filas; y si llega ese día, la política será **particionar por `occurred_at`, no borrar** — un rastro de auditoría que se borra solo deja de servir para lo único para lo que sirve.
- **`idempotency_key`:** sí se purga (>24h), pero eso no es auditoría, es estado técnico — ya está en el diseño acordado de `03-analisis-y-patrones.md`.

Ver ADR-0014 para la decisión de auditoría-en-tabla-frente-a-agregador-de-logs con su alternativa descartada.
