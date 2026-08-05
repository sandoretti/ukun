# ADR-0019: i18n del frontend con react-i18next, backend agnóstico de idioma

## Estado
Aceptado

## Contexto
Con ADR-0018 fijando que el backend nunca emite texto de usuario en español ni en ningún otro idioma —solo `code`s machine-readable, ver `docs/12-error-contract.md`—, **todo** el texto que ve un cliente o un empleado vive en `apps/ukun-web`. Eso convierte la internacionalización en una pieza estructural del frontend, no en un extra de última hora, y hoy ni `05-stack-y-versiones.md` ni `07-frontend-ux-ui.md` dicen nada al respecto.

## Decisión

### Librería
**`react-i18next`** (v17.x a fecha de este ADR, verificado por búsqueda web — confirmar la línea vigente antes de fijar versión exacta, según la política de `05-stack-y-versiones.md`) + `i18next-browser-languagedetector`. Es el estándar de facto en el ecosistema React: hook `useTranslation()`, interpolación y pluralización incluidas de fábrica, carga perezosa de namespaces por ruta. Fila nueva en `05-stack-y-versiones.md`.

Alternativas consideradas y descartadas: LinguiJS (añade un paso de extracción al build de Vite, comunidad notablemente menor); FormatJS/react-intl (ICU MessageFormat es más riguroso de lo que este dominio necesita con solo dos idiomas y sin reglas de plural complejas — ceremonia de más por mensaje).

### Estructura de traducciones
Namespaces por feature, alineados con la estructura por feature del propio frontend (skill `react-web`): `common`, `catalog`, `cart`, `checkout`, `profile`, `backoffice`, `errors`. Un fichero JSON por namespace y por idioma:

```
src/i18n/
  es/common.json  es/catalog.json  es/cart.json  es/checkout.json  es/profile.json  es/backoffice.json  es/errors.json
  en/common.json  en/catalog.json  en/cart.json  en/checkout.json  en/profile.json  en/backoffice.json  en/errors.json
```

Carga perezosa por ruta (namespace del feature activo), no todos los ficheros de golpe.

### `errors.json`, indexado por el registro de `docs/12-error-contract.md`
Cada `code` del registro tiene una entrada en `errors.json` de **ambos** idiomas, con las extensiones del `ProblemDetail` como parámetros de interpolación:

```jsonc
// es/errors.json
{ "INSUFFICIENT_STOCK": "Solo quedan {{available}} unidades de {{sku}}" }
// en/errors.json
{ "INSUFFICIENT_STOCK": "Only {{available}} units of {{sku}} left" }
```

```ts
const { t } = useTranslation('errors');
t('INSUFFICIENT_STOCK', { sku, available, requested });
```

El interceptor de axios (ADR-0010) traduce la respuesta `ProblemDetail` a `{ code, extensions }` y se lo pasa a `t(...)`. El campo `detail` del `ProblemDetail` **nunca** se muestra en pantalla — es texto de operador en inglés fijo, para logs y depuración (`docs/12-error-contract.md`).

### Idioma activo: perfil con navegador de respaldo
- **Anónimo**: `localStorage` (si el usuario ya eligió idioma antes) ?? `navigator.language` ?? `es` por defecto.
- **Autenticado**: `customer.locale`, que se sincroniza al iniciar sesión y viaja entre dispositivos.

```sql
-- customer, v0.4 (release en el que la tabla ya se reescribe a fondo — account_id, name, last_name, email)
locale CHAR(2) NOT NULL DEFAULT 'es' CHECK (locale IN ('es', 'en'))
```

Campo `locale` en `GET /api/me` y `PUT /api/me/profile` (petición y respuesta). **Es un cambio de contrato y entra antes de SINC-1** — es el único punto de esta decisión que toca al backend; el resto es exclusivamente frontend.

Selector de idioma visible en la barra de navegación en todo momento, no escondido en un submenú de preferencias — cambiar de idioma es una acción frecuente en un catálogo con clientes de dos países, no una configuración de una vez.

### Formatos numéricos y de fecha: siempre en el cliente
`Intl.NumberFormat(locale, { style: 'currency', currency: 'EUR' })` e `Intl.DateTimeFormat(locale, ...)` en React. El backend serializa `BigDecimal` como string sin formatear y `TIMESTAMPTZ` en ISO-8601 — nunca formatea dinero ni fechas para mostrar. Esto refuerza, no solo complementa, la regla ya fijada de `BigDecimal` para dinero (`03-analisis-y-patrones.md` §2): un número ya formateado con separadores de miles o símbolo de moneda es un string, y un string no se sigue pudiendo sumar ni comparar en el cliente sin volver a parsearlo.

### Accesibilidad
El atributo `<html lang>` se actualiza al cambiar de idioma activo. No es cosmética: un lector de pantalla necesita saber en qué idioma pronunciar el contenido, y es requisito ya cubierto en la sección de accesibilidad de `07-frontend-ux-ui.md`, a la que esta decisión añade el disparador concreto.

### Se hace cumplir en build, no se confía en la disciplina
Regla de ESLint (`i18next/no-literal-string` o equivalente) activa sobre `src/`: un string literal en JSX que debería ser una clave de traducción rompe el build. Mismo criterio que ArchUnit en el backend (ADR-0017): una convención que depende de que alguien se acuerde en cada PR no es una convención, es una esperanza.

### Test de cobertura de traducción
Un test de Vitest verifica dos cosas en cada build:
1. Las claves de `es/*.json` y `en/*.json` coinciden exactamente, namespace por namespace — ninguna clave huérfana en un idioma.
2. Todo `code` del registro de `docs/12-error-contract.md` tiene entrada en `es/errors.json` **y** `en/errors.json`.

El segundo punto es el que cierra el ciclo entre el registro de errores del backend y la UI del frontend: un `code` nuevo que no llegue a `errors.json` en ambos idiomas rompe CI en vez de mostrar la clave cruda en pantalla en producción.

## Consecuencias
- Todo texto de usuario vive en `apps/ukun-web`, indexado por namespace y por `code` donde aplica; el backend queda libre de cualquier responsabilidad de traducción.
- Cambio de contrato aceptado antes de SINC-1: el campo `locale` en `/api/me`.
- Coste de mantenimiento: un fichero JSON más por namespace y por idioma cada vez que se añade una pantalla, y una entrada más en `errors.json` cada vez que se añade un `code` — ambos verificados por CI, no por revisión manual.
- Fuera de alcance explícito: los monolitos legacy (`ukun-clientes`/`ukun-empleados`) y sus plantillas Thymeleaf no se traducen; se borran en v0.6/v0.7 (`04-roadmap.md`).

## Alternativas descartadas
- **El backend traduce vía `Accept-Language` + `MessageSource`**: un único sitio donde vive cada mensaje, y potencialmente útil si algún día hay un consumidor del API que no sea este SPA. Se descarta porque duplica el sistema de traducción (properties en Java + JSON en React) y ata los textos de producto al ciclo de despliegue del backend, sin que exista hoy ningún consumidor adicional que lo justifique.
- **Selector por prefijo de URL** (`/es/...`, `/en/...`): da URLs compartibles e indexables por idioma, pero obliga a que todas las rutas de react-router lleven parámetro de idioma y a que el gateway enrute ambos prefijos al SPA — coste de infraestructura no justificado para un catálogo que no depende de SEO multi-idioma.
- **Solo `localStorage`/`navigator.language`, sin persistencia en perfil**: más simple y sin cambio de contrato, pero la preferencia no viaja entre dispositivos ni sobrevive a limpiar el navegador — peor experiencia para un cliente habitual con sesión en más de un dispositivo.
