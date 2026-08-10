# ADR-0022: `ukun-auth` sale de Spring Modulith y se aplana a capas

## Estado
Aceptado — revierte parcialmente ADR-0017 (§4), decisión previamente aceptada de este mismo repositorio

## Contexto
ADR-0017 §4 descompuso `ukun-auth` en tres módulos de Spring Modulith (`accounts`, `registration`, `oauth2`) y fue honesto sobre el motivo: *«aquí el beneficio es honestamente menor… declarar módulos aquí es coherencia de herramienta con `ukun-api` más que una frontera real que necesite hacerse cumplir»*. Descartó explícitamente la alternativa de dejar este servicio sin modularizar, con el argumento de que Modulith hace cumplir esa separación **con coste marginal**.

Ese ADR se escribió antes de que existiera una línea de código de `ukun-auth`. Con el servicio ya implementado (issue #22), dos de sus premisas no sobreviven al contacto con el resultado:

1. **El propósito de Modulith no aplica aquí.** El propio ADR-0017 justifica la adopción por «dejar la extracción a microservicios preparada el día que haga falta». `ukun-auth` **ya es esa extracción**: un servicio aparte, con su propia base de datos, separado por seguridad y no por escala (ADR-0004, ADR-0003). Preparar para extraer lo que ya está extraído no compra nada.
2. **La alternativa se descartó por un coste que ya no existe.** ADR-0017 rechazó quedarse con ArchUnit porque implicaba «una regla escrita a mano», que en la práctica habría que duplicar en cada servicio. Al implementar el issue #22 apareció `:ukun-test-support`, un proyecto Gradle que aloja las siete reglas de `03-analisis-y-patrones.md` §3 compartidas entre los servicios backend. El hogar que faltaba existe.

A lo que hay que añadir el tamaño real medido: tres módulos, uno de ellos vacío hasta el issue #25, y un grafo lineal (`accounts` ← `registration`, `accounts` ← `oauth2`) en el que la detección de ciclos —lo único que `ApplicationModules.verify()` aporta por encima de ArchUnit— no tiene nada que detectar.

El coste sí era marginal, y conviene no fingir lo contrario: cinco líneas de dependencia, tres anotaciones y 89 líneas de `ModulithTest`. Lo que se revisa no es el precio, es qué se compraba con él.

## Decisión
`ukun-auth` deja de usar Spring Modulith y abandona la descomposición en módulos. Pasa a una **estructura por capas convencional**, con el mismo vocabulario que `ukun-api` ya usa dentro de sus módulos, para que moverse entre los dos servicios no exija aprender dos nomenclaturas:

```
dev.sandoretti.ukun.auth/
  domain/       Account, Role
  infra/        AccountRepository
  application/  AccountsService, AccountCredentials
  config/       AuthorizationServerConfig, JwkSourceConfig, PasswordEncoderConfig,
                RegisteredClientProperties, RegisteredClientSeeder
  security/     AccountUserDetailsService, TokenClaimsCustomizer, LoginEventListener,
                BusinessEvents, LoginAttemptBuckets, LoginRateLimiter
```

Cinco paquetes, quince clases. `web/` aparece con `/internal/accounts` en el issue #25.

Desaparece también la interfaz `AccountsApi`, que existía como puerta pública del módulo `accounts` y sin módulos no encapsula nada: `AccountsService` es una clase concreta. **No** desaparece `AccountCredentials`: la entidad sigue sin salir de la capa de servicio, y eso lo garantiza la cuarta de las siete reglas ArchUnit («ningún método público de un `@Service` devuelve una `@Entity`»), no Modulith.

**`ukun-api` no cambia en absoluto.** Sus diez módulos, su ciclo `catalog`↔`inventory` resuelto (ADR-0017 §3) y su `ModulithTest` siguen exactamente igual. Este ADR revierte §4 y nada más: §1, §2, §3 y §5 de ADR-0017 quedan intactos.

## Consecuencias
- **Dos mecanismos de fronteras distintos en los dos servicios backend.** Es el coste real de esta decisión y se acepta explícitamente, porque los dos servicios son distintos en naturaleza: uno es un monolito de dominio con diez módulos y un ciclo que hubo que romper; el otro tiene tres tablas y quince clases. Aplicarles la misma ceremonia era la coherencia que se buscaba en ADR-0017 y es la que se abandona aquí.
- **Nada impide ya que una clase de `config/` o `security/` inyecte `AccountRepository` directamente.** Con quince clases la capa se lee sin herramienta. Si la saga de alta del issue #25 hace que deje de leerse, la respuesta es un ADR nuevo —previsiblemente el que reintroduce `AccountsApi` con una regla ArchUnit de frontera, ver alternativas— y no una reintroducción silenciosa.
- Las siete reglas de `03-analisis-y-patrones.md` §3 pasan a ser **la única verificación arquitectónica** de este servicio, y siguen siendo las que más valen aquí: es el servicio que guarda hashes de contraseña, así que una entidad con `toString` generado o pasada a `log.*(...)` filtra una credencial y no el nombre de un producto.
- `ukun-auth` deja de arrastrar ArchUnit de forma transitiva vía `spring-modulith-core` y lo recibe limpio de `:ukun-test-support`. Desaparece para este servicio la comprobación de resolución de conflicto que `05-stack-y-versiones.md` documenta para `ukun-api`.
- Se pierden `/actuator/modulith` en el perfil `dev` y el diagrama que generaba `Documenter`. Para un grafo de tres nodos en línea recta, ninguno de los dos se echará de menos; en `ukun-api`, donde sí importan, siguen.
- `docs/14-modules.md` pasa a ser un documento **solo de `ukun-api`**.

## Alternativas descartadas
- **Mantener ADR-0017 §4 tal cual.** Su argumento sigue siendo cierto en lo que afirma —el coste *es* marginal— y la consistencia entre servicios tiene valor real. Se descarta porque el precio no era el problema: lo que se compraba (preparar una extracción, detectar ciclos) no aplica a un servicio ya extraído con un grafo lineal.
- **Quitar Modulith pero conservar los tres paquetes, `AccountsApi` y una regla ArchUnit de frontera** que prohíba a nadie fuera de `accounts` tocar sus subpaquetes. Es la opción intermedia y la más defendible de las tres si el servicio creciera: mantiene la frontera verificada en cada build cambiando solo la herramienta. Se descarta hoy por desproporción —tres módulos y una interfaz de un método para quince clases— y **es a la que hay que volver** si la saga del issue #25 hace ilegible el servicio. Queda escrita aquí para no tener que redescubrirla.
- **Aplanar también `ukun-api`.** Fuera del alcance de este ADR y contra su propio razonamiento: allí Modulith hace cumplir un grafo de diez módulos donde ya apareció un ciclo real que obligó a cambiar la forma del contrato REST (ADR-0017 §3). Es exactamente el caso para el que la herramienta existe.
