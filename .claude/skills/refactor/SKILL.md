---
name: refactor
description: Pasada de refactor antes de cerrar una sesión — compara lo escrito hoy con el código que ya existe para extraer lo reutilizable, aplica las reglas duras del repo y deja el comportamiento intacto (mismos tests, misma UX/UI). Usar al terminar una slice, antes de abrir el PR, o cuando el usuario pida "refactoriza lo que hemos tocado".
---

# Refactor de cierre de sesión

Esta pasada no busca "mejorar el código" —objetivo sin criterio de parada— sino dos cosas acotadas:

1. Que lo escrito hoy **no duplique** lo que ya había en el repo.
2. Que lo que la siguiente slice va a necesitar **quede donde va a buscarlo**.

Todo lo demás (renombrados de gusto, reformateos, "ya que estoy aquí") queda fuera por definición.

> **La invariante: ningún cambio de comportamiento.** El listón ya lo fijó el PR #95 — *«el último commit es un refactor con los mismos 89 tests en verde y sin tocar ninguna aserción»*. Si para que pase un test hay que editar el test, eso no es un refactor: es un cambio funcional, va antes, en su propio commit, y se explica.

Uso: `/refactor` sobre el diff de la rama · `/refactor <ruta>` para acotar a una parte.

## Paso 0 — No se refactoriza sobre rojo

- Lo funcional ya commiteado y la suite en verde. Un refactor mezclado con el cambio funcional en el mismo commit es irrevisable: quien lo mire no puede distinguir lo que cambia comportamiento de lo que solo mueve código.
- **Apunta la cifra de referencia** (cuántos tests pasan ahora, en cuántos ficheros). Es lo que vas a repetir al final y lo que va literal a la sección de verificación del PR.

## Paso 1 — Delimita el diff, y luego mira fuera de él

```bash
git diff main...HEAD --stat
git diff main...HEAD --name-only -- '*.java' '*.tsx' '*.ts'
```

Mirar solo el diff es lo que hace `/simplify`, y es la mitad del trabajo. La otra mitad —la que pide esta skill— es **cruzar lo nuevo contra lo que ya existe**, porque la duplicación cara no está dentro de la slice, está entre la slice y las tres anteriores.

| Señal en el diff | Dónde buscar el gemelo | Dónde acabaría lo extraído |
|---|---|---|
| Un método privado de 5+ líneas que "suena conocido" | `grep -rn "<nombre parecido>" services/*/src/main/java` | El módulo dueño del concepto, no el que lo escribió primero |
| Una constante, un formato, un patrón de validación | `grep -rn "<literal>" services apps` | Junto a su tipo, o `shared/` si de verdad es transversal |
| Un hook o helper de React | `ls apps/ukun-web/src/shared/{ui,api,hooks}` **antes** de escribirlo | `shared/ui` (presentación) · `shared/api` (acceso + query keys) · `shared/hooks` (comportamiento) |
| Una consulta o un `@EntityGraph` calcado | El repositorio del mismo agregado | El repositorio; nunca dos consultas equivalentes en dos sitios |
| Un montaje de test repetido | `test-support/`, los object mothers del módulo | `:ukun-test-support` si cruza servicios; el módulo si no |
| Un literal de ruta o de URL | `grep -rn "'/products" apps/ukun-web/src` | Un `routes.ts` de la feature (precedente: PR #95) |

## Paso 2 — Decidir qué se extrae, y sobre todo qué no

- **Dos usos reales, no uno.** Una abstracción con un solo consumidor es deuda con nombre bonito: cuesta leerla y todavía no sabes por dónde va a variar. Con dos usos idénticos y un tercero previsible, extrae.
- **Si no sabes nombrarlo, no es un concepto.** `Utils`, `Helper`, `Manager`, `Common` son la señal de que estás agrupando por "no sé dónde ponerlo". Espera a que el nombre aparezca.
- **Duplicación aparente ≠ duplicación real.** Dos trozos iguales hoy que cambian por razones distintas mañana no se unifican. Dos módulos de Modulith que manejan el mismo campo no comparten clase por eso.
- **Dónde vive lo extraído**, que es la decisión que más cuesta deshacer:

  | Origen | Si lo usa uno | Si lo usan dos |
  |---|---|---|
  | Módulo de `ukun-api` | Dentro del módulo, en un paquete interno | **No** lo metas en uno e importes desde el otro sin más: `type = OPEN` levanta la encapsulación pero una `allowedDependencies` declarada es lista cerrada (ADR-0017 §1). O declaras la dependencia, o sube a un módulo compartido. `ApplicationModules.verify()` es quien lo dictamina, no tu criterio |
  | `ukun-auth` | Su capa (`domain`/`infra`/`application`) | Igual — **no** reintroduzcas módulos aquí (ADR-0022) |
  | Feature de `ukun-web` | `features/<x>/` y privado | `shared/`. **Nunca** una feature importando de otra (`docs/07-frontend-ux-ui.md`); si dos la necesitan, sube, no cruces |
  | Tests | Junto a su módulo (object mothers) | `:ukun-test-support` solo si cruza servicios |

- **No subas nada a `shared/` "por si acaso".** Lo transversal se gana el sitio con dos usos delante, y bajar algo de `shared/` después es más caro que subirlo tarde.

## Paso 3 — Las reglas duras se verifican en esta pasada

El refactor es el mejor momento para cazarlas porque casi siempre entran por copiar de una fuente vieja, no por descuido. Repaso rápido sobre el diff:

- `@Data` en una entidad JPA · `Float`/`Double` para dinero · mutación por `GET` · consulta en el constructor de un bean.
- DTOs `record` con **MapStruct** (`unmappedTargetPolicy = ERROR`), no factorías estáticas (ADR-0009 ya revirtió eso una vez).
- Errores como `ProblemDetail` con `code` del registro **cerrado** de `docs/12-error-contract.md`; eventos de negocio con nombre del registro cerrado de `docs/11-observabilidad-y-logging.md` §3. Un refactor no es sitio para inventar un valor de ninguno de los dos.
- Ninguna entidad como argumento de `log.*(...)`; ningún DTO con secreto sin `toString()` que lo enmascare.
- Identificadores en inglés (ADR-0018). Si al mover código te encuentras un `Producto` o un `@Table(name = "producto")`, viene de fuente desactualizada: se corrige aquí.
- Un id de la petición **identifica**, no **autoriza**: la propiedad se resuelve del `account_id` en servidor (`docs/08-identidad-y-seguridad.md` §4).

Si el refactor arregla una de estas, deja de ser un refactor puro: dilo en el commit y en el PR, porque cambia comportamiento en un caso de borde.

## Paso 4 — Frontend: el refactor no puede costar UX/UI

Aquí la invariante es más estricta que "los tests pasan", porque los tests no ven el encuadre.

- **Los cuatro estados sobreviven a la extracción.** El fallo típico al sacar un componente es que se lleva solo el camino feliz y deja carga, vacío y error en el sitio de origen a medio pintar.
- **Accesibilidad intacta:** mismos roles y nombres accesibles, `aria-label` en botones de solo icono, `aria-hidden` en iconos decorativos, `aria-live` donde ya lo había, `:focus-visible` y orden de foco. Si el componente extraído es un modal, la trampa de foco sigue siendo de react-bootstrap, no reimplementada.
- **i18n:** extraer un componente **no** renombra claves. Si una clave cambia de namespace, la paridad `es`/`en` se comprueba (ya hay test de paridad) y las claves viejas se borran, no se dejan huérfanas. Ningún texto acaba escrito en el JSX (ADR-0019).
- **Cero cambios visuales de propina.** Si el refactor mueve un espaciado, un color o un encuadre, eso es diseño: va en otro commit y se dice. Un `object-fit` que cambia sin querer es exactamente el defecto que el #95 tuvo que arreglar a mano.
- **Cómo se comprueba:** los tests consultan por rol/label, nunca por clase CSS —así el refactor de estilos no los rompe y, a cambio, tampoco te avisa—, y **la pantalla se mira antes y después** con el dev server o con `/run`. Si no la has mirado, no digas que está verificada: ponlo como pendiente de revisión visual, como hizo el #95.
- Al rediseñar la API de un componente: variantes explícitas, no props booleanas acumuladas.

## Paso 5 — Qué se deja exactamente como estaba

- **Ficheros que la slice no toca, no se tocan.** Ni reformateo, ni reordenar imports, ni "de paso". Ensucia el diff y esconde lo que sí importa.
- **No se renombra API pública** (endpoints, nombres de campo del contrato, query keys, claves i18n ya publicadas) salvo que el renombrado *sea* la tarea.
- **`docs/api/openapi/` no se toca**: el contrato está congelado desde SINC-1 y moverlo invalida tipos y mocks del frontend (`docs/09` §5).
- **Ningún defecto adyacente se arregla aquí.** Si te tropiezas con uno, se abre issue y se enlaza; ampliar el alcance en el último commit de la sesión es cómo un PR revisable deja de serlo.
- **Nada de dependencias nuevas ni de subir versiones.** Eso vive en `gradle/libs.versions.toml` / `package.json` y es una decisión aparte.
- **El legacy no se refactoriza jamás.** `ukun-clientes` y `ukun-empleados` no están en servicio y se borran en v0.6/v0.7: cada minuto invertido ahí se tira.
- **Optimizaciones solo con medición delante.** Las micro-reglas de rendimiento están subordinadas al código limpio; veinte productos por página no justifican ofuscar una función legible.

## Paso 6 — Qué skill cargar según lo que toca el diff

Manda siempre la propia; la externa aporta vocabulario y ejemplos y no decide nada (`.claude/skills/README.md`).

| El diff toca | Skill propia (manda) | Apoyo externo | Lo que de esa externa **no** aplica |
|---|---|---|---|
| `services/ukun-api` | `spring-api` | `springboot-patterns`, `java-springboot` | Su `ApiError` propio y sus factorías estáticas; `@Cacheable`/`@Async`/Kafka |
| `services/ukun-auth` | `spring-auth` | `java-springboot` | Cualquier sugerencia de modularizarlo (ADR-0022) |
| `services/ukun-gateway` | `spring-gateway` | — | — |
| Esquema | `liquibase` | — | Changesets aplicados son inmutables: un refactor **nunca** edita uno |
| `apps/ukun-web` | `react-web` | `vercel-composition-patterns` (API del componente), `web-design-guidelines` (revisión de UI), `vercel-react-best-practices` cat. 5 (re-renders) | Todo lo `server-*`, SSR, `next/dynamic`, y `client-swr-dedup` (aquí es TanStack Query, ADR-0010) |
| Datos de servidor en React | `react-web` §2 | `tanstack-query-best-practices` (`qk-factory-pattern`, `cache-invalidation`) | La categoría SSR entera; `err-fallback-data` |
| Tests | `testing` | `spring-boot-test-patterns` | `@MockBean` (no existe en Boot 4), Testcontainers 1.19 a mano, H2 |

## Paso 7 — Verificación, con las cifras que van al PR

```bash
./gradlew :ukun-api:test :ukun-api:integrationTest     # incluye ArchUnit y ApplicationModules.verify()
npm --prefix apps/ukun-web run lint && npx --prefix apps/ukun-web tsc -b && npm --prefix apps/ukun-web run test
```

Y la comprobación que demuestra que fue un refactor de verdad:

```bash
git diff --stat main...HEAD -- '**/*Test.java' '**/*IT.java' '**/*.test.tsx' '**/*.test.ts'
```

Si sale algo, cada línea necesita justificación (un test que se mueve de fichero con la clase que prueba, sí; una aserción reescrita, no).

## Paso 8 — El commit, y lo que hereda el PR

Un commit propio y **el último** de la rama: `refactor(web): saca las piezas repetidas de la pantalla de tiendas` (así está en el historial: `671e363`, `486571b`).

De ahí sale, tal cual, la sección **«Limpieza (sin cambio de comportamiento)»** de la plantilla de la skill `pr`: una viñeta por extracción diciendo **qué se sacó, desde cuántos sitios, y por qué vive donde vive**. Esa última parte es la que sirve dentro de tres meses; "se extrajo un helper" no sirve.

Si en la pasada aparece una convención que vale para las siguientes slices, se escribe en la skill o en `docs/` **en esta misma sesión** (`docs/09` §7). No se confía en recordarlo.

## Relación con `/simplify` y `/code-review`

- **`/simplify`** aplica limpieza de calidad sobre el diff. Vale como primera pasada mecánica, pero es genérico y mira solo lo cambiado: no conoce Modulith, ni los registros cerrados, ni i18n, ni sabe que hay una clase equivalente en otro servicio. Revisa lo que proponga contra el paso 3.
- **`/code-review`** busca *bugs*, no calidad. Es complementario, no sustituto: pásalo antes del PR si la slice toca concurrencia, dinero, identidad o esquema.
- Si cualquiera de las dos contradice esta skill, `docs/` o un ADR, mandan estos.

## Reglas

- **Refactor y cambio funcional no comparten commit.** Nunca.
- **Si hay que tocar un test para que pase, no es un refactor.** Para y reclasifica el cambio.
- **Dos usos, no uno.** Y con nombre, o no se extrae.
- **Extraer no cruza una frontera de módulo sin declarar la dependencia** — `ApplicationModules.verify()` decide, no el criterio del momento.
- **En frontend, la pantalla se mira antes y después.** Los tests no ven el encuadre.
- **El legacy no se toca.**
- **No se amplía el alcance en el último commit de la sesión.** Lo que aparezca, a un issue.
