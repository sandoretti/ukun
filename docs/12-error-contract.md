# Contrato de errores: registro cerrado de `code`s

> **Fuente de verdad de:** la lista completa de `code`s de error, sus status y extensiones, la jerarquía de excepciones y el proceso para añadir uno nuevo.
> **Léelo si:** vas a lanzar un error nuevo o a mapear un `ProblemDetail` en el frontend.
> **No decide:** el texto que ve el usuario — eso vive en `apps/ukun-web/src/i18n/*/errors.json` (ADR-0019).
> **Registro cerrado:** si el `code` que necesitas no está aquí, se añade siguiendo §4, no se inventa en el código.

`03-analisis-y-patrones.md` §2 ya fijó el mecanismo — `ProblemDetail` (RFC 9457) vía `@RestControllerAdvice`, con un campo `code` estable y machine-readable. Este documento es el **registro cerrado** de esos `code`s: la lista completa, no ejemplos sueltos. React hace `switch`/lookup sobre `code`, nunca sobre `title` o `detail` (que son texto de operador en inglés, ver más abajo) — ver ADR-0019 para cómo el frontend traduce cada `code` a los dos idiomas soportados.

Aplica a `ukun-api` y a `ukun-auth`; el registro es único y compartido entre los dos servicios para que React no tenga que distinguir de qué backend vino un error.

## 1. Forma de una respuesta de error

```json
{
  "type": "https://ukun.dev/errors/insufficient-stock",
  "title": "Insufficient stock",
  "status": 409,
  "code": "INSUFFICIENT_STOCK",
  "detail": "sku ABC-123: 2 available, 5 requested",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "stockLineId": 42,
  "productId": 14,
  "sku": "ABC-123",
  "available": 2,
  "requested": 5
}
```

- **`code`** es el campo estable y machine-readable. Es lo único sobre lo que el frontend decide qué pintar.
- **`title`/`detail` están siempre en inglés fijo**, dirigidos al operador y a los logs — **nunca se muestran en pantalla**. `detail` no lleva stack trace, SQL ni nombres de tabla; describe el hecho en una línea (ver ejemplo arriba).
- **`traceId`** correlaciona con `docs/11-observabilidad-y-logging.md`, presente en toda respuesta de error.
- El resto de campos son **extensiones específicas del `code`**, listadas en el registro de abajo — es lo que el frontend usa para interpolar el mensaje traducido (`t('INSUFFICIENT_STOCK', { sku, available, requested })`).

## 2. Registro

| `code` | Status | Extensiones | Origen | Release |
|---|:-:|---|---|:-:|
| `VALIDATION_FAILED` | 400 | `errors[]` (`field`, `message`) | `MethodArgumentNotValidException` | v0.3 |
| `MALFORMED_REQUEST` | 400 | — | `HttpMessageNotReadableException` | v0.3 |
| `UNAUTHENTICATED` | 401 | — | resource server (JWT ausente/inválido) | v0.4 |
| `FORBIDDEN` | 403 | — | `@PreAuthorize` / autorización de recurso | v0.4 |
| `RESOURCE_NOT_FOUND` | 404 | `resource`, `id` | `orElseThrow` en servicio | v0.3 |
| `CHECKOUT_EXPIRED` | 410 | `attemptId`, `expiredAt` | checkout — ADR-0016 | v0.1 (contrato) / v0.6 (implementación) |
| `INSUFFICIENT_STOCK` | 409 | `stockLineId`, `productId`, `sku`, `available`, `requested` | checkout — ADR-0008, ADR-0015 | v0.1 (contrato) / v0.6 (implementación) |
| `PRODUCT_IN_USE` | 409 | `inOrders`, `inStock`, `inCarts` | `DeleteProductUseCase` (RF-10) | v0.7 |
| `PRODUCT_TYPE_IN_USE` | 409 | `inProducts` | `DeleteProductTypeUseCase` (RF-13) | v0.7 |
| `DUPLICATE_VALUE` | 409 | `resource`, `field` | violación de un `UNIQUE` de negocio comprobada antes de escribir: `product.sku`, `product_type.name` (RF-10, RF-13) | v0.7 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | `allowed` | subida de imagen de producto: el contenido real del fichero no es JPEG/PNG/WebP (`08-identidad-y-seguridad.md` §9) | v0.7 |
| `PAYLOAD_TOO_LARGE` | 413 | `maxBytes` | subida de imagen de producto por encima del límite, rechazada por el gateway o por `MaxUploadSizeExceededException` | v0.7 |
| `VERSION_CONFLICT` | 409 | `resource`, `id` | `OptimisticLockingFailureException` (edición manual de stock, ADR-0008) | v0.7 |
| `IDEMPOTENCY_IN_PROGRESS` | 409 | `key` | checkout — ADR-0016 | v0.1 (contrato) / v0.6 (implementación) |
| `IDEMPOTENCY_MISMATCH` | 422 | `key` | checkout — ADR-0016, misma key con payload distinto | v0.1 (contrato) / v0.6 (implementación) |
| `EMPTY_CART` | 422 | — | checkout, carrito vacío (RF-04; bug del legacy, no requisito) | v0.6 |
| `EMAIL_ALREADY_EXISTS` | 409 | — | `ukun-auth`, saga de signup | v0.4 |
| `REFERENTIAL_INTEGRITY` | 409 | — | red de seguridad: `DataIntegrityViolationException` (SQLState `23503`) que se cuela sin pasar por una comprobación explícita | v0.3 |
| `INTERNAL_ERROR` | 500 | — | fallback genérico, sin detalle al cliente | v0.3 |

La columna *Release* dice cuándo el `code` **se emite por primera vez**; todos están en el OpenAPI congelado en SINC-1 desde v0.1, porque el frontend genera de ahí el `enum` con el que hace el lookup.

### Tres fronteras que es fácil confundir

- **`DUPLICATE_VALUE` frente a `REFERENTIAL_INTEGRITY`**: el segundo es la red de seguridad para una violación de **clave ajena** (`SQLState 23503`) que se cuela sin comprobación previa; el primero es una violación de **unicidad** (`23505`) de un dato de negocio que el caso de uso comprueba *antes* de escribir para poder decir qué campo choca. No son el mismo error con distinto nombre: uno dice "esto está referenciado", el otro "esto ya existe".
- **`DUPLICATE_VALUE` frente a `EMAIL_ALREADY_EXISTS`**: el correo tiene `code` propio porque su unicidad la impone **otro servicio** (`ukun-auth`) y llega propagada a través de la saga de alta, no de una comprobación local. Ambos se pintan igual en pantalla —bajo el campo del formulario—, pero su origen es distinto y el registro lo refleja.
- **`PAYLOAD_TOO_LARGE` puede no nacer en `ukun-api`.** Un fichero por encima del límite lo corta el gateway antes de que llegue al servicio, así que ese `ProblemDetail` lo emite el gateway; es el único `code` de esta tabla con dos emisores posibles, y los dos deben producir exactamente la misma forma de respuesta (skill `spring-gateway`). Que el límite del gateway y el de `spring.servlet.multipart.max-file-size` valgan lo mismo es lo que evita reproducir el defecto #17.

`type` sigue el patrón `https://ukun.dev/errors/<kebab-case-del-code>` (p. ej. `INSUFFICIENT_STOCK` → `https://ukun.dev/errors/insufficient-stock`); no hace falta declararlo fila a fila, se deriva mecánicamente del `code`.

## 3. Jerarquía de excepciones

```java
public abstract class UkunException extends RuntimeException {
    public abstract String getCode();
    public abstract HttpStatus getStatus();
    public Map<String, Object> getExtensions() { return Map.of(); }
}

public final class ResourceNotFoundException extends UkunException {
    // resource, id -> extensions
}

public final class InsufficientStockException extends UkunException {
    // stockLineId, productId, sku, available, requested -> extensions
}
```

Un único `@RestControllerAdvice` por servicio traduce cualquier `UkunException` de forma genérica (usa `getCode()`/`getStatus()`/`getExtensions()` para construir el `ProblemDetail`), más handlers explícitos para las excepciones de Spring que no son `UkunException` (`MethodArgumentNotValidException`, `OptimisticLockingFailureException`, `DataIntegrityViolationException`, `AccessDeniedException`, `AuthenticationException`). **Ninguna rama del advice construye un `ProblemDetail` campo a campo fuera de estos dos caminos.**

## 4. Regla de proceso para un `code` nuevo

Un `code` nuevo entra, en este orden, y nunca en otro:

1. **Este registro** — fila nueva con status, extensiones y origen.
2. **El OpenAPI** (`docs/api/openapi/ukun-api.yaml` o `ukun-auth.yaml`) — como valor del `enum` del schema `ProblemDetail`. El check de deriva de CI (`03-analisis-y-patrones.md` §3) falla si el código implementa un `code` que no está en el YAML.
3. **El código** — la excepción tipada y, si aplica, el handler.
4. **`errors.json` de los dos idiomas** en `apps/ukun-web` (ADR-0019) — el test de paridad de traducciones falla si un `code` de este registro no tiene entrada en `es/errors.json` **y** `en/errors.json`.

Saltarse un paso es detectable mecánicamente en los tres casos (pasos 2 y 4 por CI, paso 3 porque el código simplemente no compila o no tiene excepción que lanzar) — no depende de que quien añade el `code` se acuerde de los otros tres sitios.

## 5. Qué no vive aquí

Los textos de usuario en español/inglés viven en `apps/ukun-web/src/i18n/*/errors.json` (ADR-0019), indexados por `code`. Este documento no contiene ni un solo mensaje para pantalla — solo status, extensiones y origen.
