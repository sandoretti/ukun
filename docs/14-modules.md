# Módulos: cómo se escribe un `@ApplicationModule` en este repo

> **Fuente de verdad de:** la forma de un módulo, la regla de no exponer entidades, el ejemplo del cruce `inventory`→`catalog` y qué hacer cuando `verify()` falla.
> **Léelo si:** vas a crear un paquete de nivel superior en `ukun-api`, o te falla `ApplicationModules.verify()`.
> **No decide:** qué módulos existen y por qué (ADR-0017).
> **Alcance:** **solo `ukun-api`.** `ukun-auth` no usa Spring Modulith — ADR-0022 lo aplanó a una estructura por capas; sus convenciones están en la skill `spring-auth`.

ADR-0017 decide **qué** módulos existen y **por qué**. Este documento es el «cómo se escribe» — la contraparte operativa, en el mismo espíritu que las skills son el «cómo» de `docs/03-analisis-y-patrones.md`. Léelo antes de crear un paquete nuevo de nivel superior en `ukun-api`.

## 1. Forma de un módulo

Cada módulo es un paquete de nivel superior bajo el paquete raíz de la aplicación, con un `package-info.java`:

```java
// dev.sandoretti.ukun.api.inventory.package-info
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = { "catalog", "stores", "identity", "shared" }
)
package dev.sandoretti.ukun.api.inventory;
```

**`shared` se declara aunque sea un módulo abierto.** Es la trampa más fácil de este fichero, y se descubrió con el primer módulo que tuvo código (v0.3): `type = OPEN` y `allowedDependencies` son dos mecanismos distintos. El primero decide **qué se ve** —los subpaquetes de un módulo abierto son importables sin publicarlos como `NamedInterface`—; el segundo decide **a quién se puede llamar**, y declarar una lista la vuelve **cerrada**: cualquier destino que no esté en ella es una violación, incluido un módulo abierto. El síntoma es exactamente este:

```
Module 'catalog' depends on module 'shared' via
  …catalog.application.ProductQueryService -> …shared.web.PageResponse.
  Allowed targets: storage.
```

Un módulo que **no** declara lista (`stores`, `storage`, `identity`, `audit`) la tiene sin restringir y no necesita nombrar a nadie. Verificado contra Modulith 2.1.0; ver ADR-0017 §1.

Estructura interna de un módulo, coherente con lo que ya prescribe la skill `spring-api`:

```
inventory/
  InventoryApi.java          <- API pública: interfaz + DTOs record. Lo único importable desde fuera.
  web/                        <- @RestController de este módulo
  application/                <- casos de uso (cuando la orquestación lo justifique)
  domain/                     <- StoreStock (agregado)
  infra/                      <- StoreStockRepository (Spring Data JPA)
  internal/                   <- cualquier clase que no deba ser pública ni siquiera dentro del módulo
```

Todo lo que no sea la interfaz `InventoryApi` (o equivalente) y sus DTOs vive en un subpaquete — Modulith trata el paquete raíz del módulo como su API pública y cualquier subpaquete como interno, invisible para otros módulos salvo que se declare explícitamente `NamedInterface`.

## 2. Regla de oro: nunca se expone una `@Entity`

Ni como parámetro ni como retorno de la API pública de un módulo. `InventoryApi.summariesByIds(Set<Long>)` devuelve `Map<Long, ProductSummary>` (un `record`), nunca `Map<Long, Product>`. Es la misma regla ArchUnit que ya existe para servicios («los métodos públicos de un servicio no devuelven tipos `@Entity`»), aplicada ahora también en la frontera entre módulos.

## 3. Ejemplo completo: el cruce `inventory` → `catalog`

Es el único cruce de lectura no trivial entre módulos (ADR-0017 §2 y §3). Así se ve de un lado y del otro:

```java
// catalog/CatalogApi.java — API pública del módulo catalog
public interface CatalogApi {
    Map<Long, ProductSummary> summariesByIds(Set<Long> productIds);
}

public record ProductSummary(
    Long id, String sku, String name, String imageUrl, BigDecimal price, boolean active) {}
```

```java
// inventory/application/AvailabilityQueryService.java
@Service
class AvailabilityQueryService {

    private final StoreStockRepository stockRepository;
    private final CatalogApi catalogApi; // dependencia declarada: inventory -> catalog

    List<AvailabilityLine> availabilityFor(Long storeId, Set<Long> productIds) {
        var stock = stockRepository.findByStoreIdAndProductIdIn(storeId, productIds);
        var products = catalogApi.summariesByIds(productIds); // UNA llamada en lote, nunca en bucle
        return stock.stream()
            .map(s -> new AvailabilityLine(products.get(s.getProductId()), s.getQuantity()))
            .toList();
    }
}
```

Nótese lo que **no** hay: ningún `for (var id : productIds) catalogApi.summaryById(id)`. La API del módulo `catalog` solo expone la versión en lote precisamente para que ese bucle sea imposible de escribir sin violar la firma del método — es la comprobación anti-N+1 de ADR-0017 §2 hecha estructural, no solo una promesa de code review.

## 4. Verificación en CI

Un solo `ModulithTest`, el de `ukun-api`, desde el primer commit del skeleton (no se añade después). Hubo un segundo en `ukun-auth` durante v0.4 y ADR-0022 lo retiró junto con los módulos de ese servicio:

```java
class ModulithTest {

    private static final ApplicationModules modules =
        ApplicationModules.of(UkunApiApplication.class);

    @Test
    void verifyModuleStructure() {
        modules.verify();
    }

    @Test
    void writeDocumentationSnippets() {
        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml();
    }
}
```

`verify()` falla el build si: un módulo importa una clase interna de otro sin pasar por su API pública, o si aparece un ciclo de dependencias entre módulos. `Documenter` genera el diagrama de módulos en cada ejecución — **sustituye** a mantener un diagrama de paquetes a mano en `01-arquitectura.md`; ese documento enlaza aquí en vez de duplicar el grafo.

**Lo que `verify()` no cubre mientras los módulos están vacíos**, comprobado al implementarlo en v0.2: como su análisis parte de las dependencias **reales entre tipos**, con paquetes que solo contienen `package-info.java` no tiene nada que rechazar — pasa en verde igual con un ciclo declarado en `allowedDependencies` que sin él. Por eso `ModulithTest` lleva además una aserción del **grafo declarado** contra la tabla de ADR-0017 §1 (`module.getAllowedDependencies(modules)`, comparado como mapa módulo→dependencias) y otra de que `shared` es el único módulo abierto. Esa aserción sí falla hoy: un nombre mal escrito revienta con `Invalid explicit module dependency in catalog! No module found with name 'storagee'`. Cuando los módulos tengan código, ambas capas se complementan — una vigila lo declarado, la otra lo que el código hace de verdad.

## 5. Qué hacer cuando `verify()` falla por un ciclo

El caso ya resuelto es `catalog` ↔ `inventory` (ADR-0017 §3): se rompe eligiendo un sentido de dependencia (`inventory → catalog`) y separando en dos endpoints lo que antes hubiera sido una sola respuesta. La regla general, para el próximo ciclo que aparezca:

1. Identificar qué módulo es el "consumidor" natural del caso de uso que motiva el ciclo (aquí, `inventory` necesita el nombre del producto para pintar el stock; `catalog` no necesita nada de `inventory` para pintar el catálogo).
2. El módulo consumidor declara la dependencia; el otro se queda limpio.
3. Si eso obliga a que el frontend haga dos llamadas donde antes hubiera hecho una, se documenta como cambio de contrato **antes** de congelar el OpenAPI si el ciclo se descubre en v0.1-v0.2, o como un ADR si se descubre más tarde y ya hay contrato en producción.

### Cuando el que necesita el dato es el módulo del que ya dependen todos

El caso de v0.7 (RF-10, borrado de producto) no encaja en los tres pasos de arriba, y por eso está escrito aparte. `DeleteProductUseCase` vive en `catalog` y tiene que contar filas de `order_line`, `store_stock` y `cart_item` para responder `409 PRODUCT_IN_USE` diciendo **dónde** están las referencias. Aquí el consumidor natural del caso de uso es `catalog`, pero declarar `catalog → inventory/cart/orders` cerraría exactamente el ciclo que ADR-0017 §3 rompió eligiendo el sentido contrario.

**Se invierte la dependencia en vez de añadirla.** `catalog` publica lo que necesita saber (`ProductReferenceCounter`, `ProductReferenceKind`), cada dueño de tabla lo implementa sobre la suya, y Spring le entrega las implementaciones al caso de uso. Todas las flechas siguen apuntando a `catalog`, el grafo declarado de ADR-0017 §1 no cambia y `verify()` no ve nada nuevo — que es la comprobación de que esto resuelve el problema en vez de reformularlo.

Dos propiedades que hacen que la inversión no se degrade con el tiempo:

- Lo que cruza la frontera es un **número**, nunca una fila: `cart` responde "en dos carritos" sin decir de quién, y su repositorio mantiene la regla de que ninguna línea de carrito es alcanzable sin nombrar a su dueño.
- El agregador (`ProductReferences`) **falla al arrancar** si algún `ProductReferenceKind` se queda sin implementación. Un módulo que dejara de registrar la suya haría el borrado silenciosamente más permisivo, y la base de datos lo rechazaría después con un `23503` pelado mientras la respuesta afirmaba que no hay referencias.

## 6. Actuator de módulos

`management.endpoints.web.exposure.include: health,modulith` solo en el perfil `dev` (mismo criterio que springdoc/Swagger UI, `06-entorno-local-tilt.md`) — expone la estructura de módulos y sus dependencias en `/actuator/modulith`, útil para depurar un `verify()` que falla sin tener que leer el mensaje de excepción completo. Requiere la dependencia `spring-modulith-actuator` en alcance `runtime`.

La exposición es lo único que hace falta; sin el perfil `dev`, la ruta devuelve 404. **No** uses `management.endpoint.modulith.enabled` — esa propiedad ya no existe en Spring Boot 4 (se sustituyó por `management.endpoint.<id>.access`, verificado contra los metadatos de configuración de `spring-boot-actuator` 4.1.0), y como Boot ignora en silencio una propiedad desconocida, escribirla da la falsa impresión de estar controlando algo.

## 7. Lo que Modulith no hace aquí

No hay `@ApplicationModuleListener` ni *Event Publication Registry* en este repo — decisión explícita de ADR-0017 §5, coherente con el descarte de "domain events + outbox" de `03-analisis-y-patrones.md` §4. Si en algún punto de `v1.x` aparece una razón de negocio real para eventos asíncronos entre módulos, ese es un ADR nuevo, no una extensión silenciosa de este documento.
