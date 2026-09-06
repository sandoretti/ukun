# Bitácora del despliegue

> **Fuente de verdad de:** qué está provisionado hoy en Azure, cuándo se hizo y qué se descubrió al hacerlo.
> **Léelo si:** vas a desplegar y quieres saber qué existe ya, o te has topado con un error y quieres ver si es conocido.
> **No decide:** cómo se despliega (`15-despliegue.md`), ni por qué el destino es Container Apps (ADR-0023).
> **Idioma:** nombres de recurso y de variable en inglés (ADR-0018).

Este documento se **añade**, no se reescribe: cada entrada lleva su fecha y lo que se descubrió ese día se queda escrito aunque después se corrija. Es lo que evita volver a tropezar con lo mismo dentro de tres meses.

**Aquí no hay identificadores ni secretos.** Ni el ID de suscripción, ni el de tenant, ni el `clientId` de la identidad: no son credenciales —sin la credencial federada no autorizan nada— pero tampoco aportan nada en un documento, se leen con `az account show` y ya viven donde tienen que vivir, en las variables del repositorio. `ci.yml` contempla que este repositorio llegue a ser público; los valores que estén en `docs/` seguirán ahí cuando eso pase.

## 2026-09-01 · Preparación de Azure

Ejecutado desde una sesión con `az login` ya hecho, sobre la suscripción **Azure for Students** del tenant de la Universidad de Alcalá. Corresponde a `15-despliegue.md` §2 completo.

### Lo que quedó creado

| Recurso | Nombre | Dónde |
|---|---|---|
| Grupo de recursos de la identidad | `ukun-identity` | `francecentral` |
| Managed identity de usuario | `ukun-github` | dentro de `ukun-identity` |
| Credencial federada de OIDC | `github-main` | sobre `ukun-github` |
| Asignación de rol | `Contributor` | **ámbito de suscripción** |

La credencial federada tiene como sujeto `repo:sandoretti/ukun:ref:refs/heads/main`, así que autoriza únicamente a los workflows que corran **desde `main`**. Lanzar `deploy.yml` desde otra rama falla en `azure/login` con un error que habla de credenciales y no de la rama.

El rol es de ámbito de suscripción y la identidad vive **fuera** del grupo que se despliega, las dos cosas por el mismo motivo: `teardown.yml` borra `ukun-demo` entero, y una identidad o una asignación dentro de él se llevarían por delante el permiso para volver a desplegar.

Providers registrados: `Microsoft.App`, `Microsoft.OperationalInsights`, `Microsoft.DBforPostgreSQL`, `Microsoft.Storage`. Los cuatro en `Registered`.

### Variables y secretos del repositorio

Puestos los cinco variables (`AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `AZURE_RESOURCE_GROUP=ukun-demo`, `AZURE_LOCATION=francecentral`) y los cuatro secretos (`POSTGRES_PASSWORD`, `UKUN_OAUTH2_GATEWAY_SECRET`, `UKUN_OAUTH2_API_SECRET`, `UKUN_SIGNING_KEY`).

Los cuatro secretos son **de este entorno y de nadie más**: no coinciden con los del `.env` local ni tienen por qué. La clave de firma sí importa que sea estable entre despliegues, y por eso vive en el secreto y no se regenera: cambiarla invalida todos los tokens emitidos (ADR-0024).

### Dos cosas que se descubrieron aquí

**1. La suscripción restringe las regiones, y `westeurope` no está permitida.** Hay una política «Allowed resource deployment regions» que solo admite `germanywestcentral`, `francecentral`, `polandcentral`, `swedencentral` y `switzerlandnorth`. Lo desagradable es *cuándo* falla: `az group create` funciona con una región prohibida y el error llega al crear el primer recurso dentro, como `RequestDisallowedByAzure`, dejando un grupo vacío con la ubicación mala que hay que borrar y rehacer — la ubicación de un grupo no se cambia.

Se eligió **`francecentral`** por cercanía. Consecuencia en el código: el valor de respaldo de `LOCATION` en `deploy.yml` era `westeurope` y se cambió, porque un default que la política prohíbe es una trampa para quien despliegue sin poner la variable.

**2. La contraseña de Postgres no se puede generar con `openssl rand -hex`.** Azure exige caracteres de al menos tres de las cuatro categorías (mayúsculas, minúsculas, dígitos, no alfanuméricos) y un hexadecimal solo tiene dos. Se detectó antes de desplegar, pero el fallo habría llegado a mitad del despliegue y con el grupo de recursos ya montado. El runbook lleva ahora el generador correcto; los dos secretos de OAuth2 siguen en hexadecimal, que a esos no los valida nadie más que sus dos lectores.

### Qué queda para el primer despliegue

Nada de Azure. Lo que falta es de GitHub y va en este orden:

1. Mergear a `main` los PR del despliegue. `workflow_dispatch` no aparece en Actions mientras el workflow viva fuera de la rama por defecto.
2. Esperar a que el job `images` de CI publique las tres imágenes — solo corre en `push` a `main`.
3. **Marcar los tres paquetes de GHCR como públicos.** Se crean privados. `deploy.yml` lo comprueba en su primer paso, antes de tocar Azure.
4. Actions → Deploy → `image-tag`: el SHA del merge.

Y lo que sigue sin comprobarse porque solo se comprueba desplegando: que `environmentStaticIp` sirva para estrechar el firewall de Postgres, que las `mountOptions` basten para que `ukun-api` escriba en el share de Azure Files, que `azure.extensions` esté aplicado a tiempo para el primer Liquibase, si los tiempos de la sonda de arranque son suficientes, y el coste real. Cada uno de esos tiene su fila pendiente en `15-despliegue.md`.

## 2026-09-01 · Coste verificado

Se cierra el punto del checklist de la issue #55 que pedía «el coste verificado en la calculadora». No se usó la calculadora sino la **API de precios de Azure** (`prices.azure.com/api/retail/prices`), que es pública, no necesita autenticación y devuelve el precio por medidor y región — de modo que la comprobación es reproducible con un comando en vez de con capturas de una web. Las cifras y los escenarios están en `15-despliegue.md` §8.

El resultado en una línea: **el uso puntual sale por céntimos** —0,07 USD una demo de tres horas, 0,30 USD un mes de cuatro demos— y **24/7 sale por 46,15 USD/mes**, que agota el crédito de estudiante en 2,2 meses. Los dos números que explican esa diferencia:

- La **franja gratuita mensual de Container Apps** (180 000 vCPU-s y 360 000 GiB-s) se agota, con el reparto de recursos de esta plantilla, exactamente a las **40 horas encendido al mes**. Por debajo de eso el cómputo de las tres apps es gratis y solo corre el contador de Postgres, a 0,025 USD/h entre cómputo y disco.
- Existe un medidor **`Environment Management Hour` de 0,125 USD/h** —91 USD/mes— que **no** aplica aquí porque es de los entornos con *workload profiles*, y `main.bicep` no declara ninguno. Queda anotado porque añadir un `workloadProfiles` parecería una mejora y sería el cambio más caro que se le puede hacer a esta plantilla.

Nada de esto sustituye a mirar el consumo real: el primer despliegue es también la primera medida de verdad, y si difiere, la cifra que manda es la del portal y se corrige aquí.

## 2026-09-01 · Dimensionado corregido antes del primer despliegue

Al preguntarse si el entorno aguanta a cuatro personas a la vez salió que el problema no era ese: **`ukun-auth` no cabía en su propio límite ni vacío.** `docker stats` sobre el stack local en reposo da 368 MiB al gateway, **412 MiB a auth** y 603 MiB a api, y ADR-0023 tabulaba 0,5 GiB para auth — el 80 % ocupado antes de que nadie iniciara sesión. En Container Apps eso no se degrada, se muere: pasarse del límite mata el contenedor.

Cambios en `main.bicep`:

- `ukun-auth` pasa de 0,25 vCPU / 0,5 GiB a **0,5 vCPU / 1 GiB**. Las combinaciones de Consumption van en pares fijos, así que ese es el escalón siguiente.
- Los tres reciben **`JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70`**. Por defecto la JVM toma el 25 % de la memoria del contenedor, o sea 256 MiB de heap dentro de 1 GiB: el resto se paga sin usarse y el síntoma sería GC continuo con memoria libre en el panel.

Efecto en el coste: **ninguno para el uso puntual** —una demo de tres horas sigue costando 0,07 USD, porque sigue dentro de la franja gratuita de Container Apps—. Lo que se estrecha es el margen: la franja pasa de cubrir 40 horas encendido al mes a cubrir 33, y el escenario 24/7 sube de 46,15 a 52,06 USD/mes. `15-despliegue.md` §8 lleva las cifras rehechas.

Y la respuesta a la pregunta que originó todo: **para cuatro personas concurrentes sobra**. Postgres B1ms admite del orden de 50 conexiones contra las 15 que abren los tres pools juntos, y ninguno de los servicios se acerca a su techo. Lo único que hay que vigilar es el limitador de tasa, que cuenta **por IP**: varias personas detrás de la misma NAT comparten los 120 req/min de `/api/**` y las 5 altas cada 10 minutos. No se ha cambiado el valor por defecto —el límite existe por algo (`08-identidad-y-seguridad.md` §8)— pero §8 del runbook dice ahora qué variable subir si aparece un `429` en mitad de una demo.

## 2026-09-01 · La plantilla, validada contra Azure de verdad

Sin desplegar nada y sin coste. Es lo más lejos que se puede llegar antes de que los paquetes de GHCR sean públicos, y cubre una clase de fallo que `az bicep build` no ve: aquel valida contra los tipos que trae el compilador, esto valida contra el ARM real de la suscripción.

```bash
az group create --name ukun-demo --location francecentral
az deployment group validate --resource-group ukun-demo --template-file deploy/azure/main.bicep --parameters …
az deployment group what-if  --resource-group ukun-demo --template-file deploy/azure/main.bicep --parameters …
```

- **`validate` → `Succeeded`, sin errores.** ARM acepta la plantilla, sus `apiVersion`, sus propiedades y sus parámetros.
- **`what-if` → 15 recursos, todos `Create`** y ninguno de más: el entorno y su almacenamiento, las tres apps, el servidor de Postgres con sus tres bases, `azure.extensions` y la regla de firewall, Log Analytics, y la cuenta de almacenamiento con su share.
- **Disponibilidad en `francecentral` comprobada**, que es lo que la política de regiones obligaba a mirar: `Microsoft.App/managedEnvironments` está en France Central, `Standard_B1ms` está entre los SKU Burstable, y PostgreSQL 17 entre las versiones ofrecidas (18 también existe ya; se mantiene 17 por paridad con el `postgres:17` del entorno local).

El grupo `ukun-demo` queda creado y **vacío**, que no cuesta nada: `deploy.yml` lo crea de forma idempotente, así que encontrárselo hecho no cambia nada.

### Lo que bloquea el primer despliegue, y no es Azure

Las tres imágenes están publicadas en GHCR desde el commit `c4dee40` —el job `images` corrió en verde en el CI de `main`— pero **los tres paquetes son privados**: un `GET` anónimo a su manifiesto devuelve `403`. Container Apps no podría bajarlas. Cambiar la visibilidad de un paquete **no tiene endpoint en la API REST de GitHub**: se hace en la página del paquete, una vez por paquete y para siempre.

Y falta que el contenido del despliegue llegue a la rama por defecto, que es de lo que va el PR #178.

## 2026-09-01 · Primer despliegue, y el entorno sembrado

**El despliegue funcionó a la primera**, con los nueve pasos de `deploy.yml` en verde. Lo que eso valida de golpe, y no se podía comprobar de otra forma:

- **El OIDC federado.** `azure/login` en verde es la credencial federada, su `--subject` sobre `main` y el rol Contributor, los tres a la vez.
- **`azure.extensions` llegó a tiempo.** Si no, Liquibase no habría arrancado y el gateway no habría respondido `UP`.
- **Los tiempos de la sonda de arranque bastaron** con los valores puestos a ojo (20 s de gracia, 30 fallos a 10 s).
- **El firewall a los servicios de Azure deja pasar a las apps**, así que no hizo falta el rodeo por la IP saliente para el primer arranque.
- **El `iss` compuesto por Bicep desde `defaultDomain` coincide** con el que publica el Authorization Server en su documento de descubrimiento. Era el punto donde ADR-0023 temía tener que desplegar dos veces, y no hizo falta.

URLs del entorno (cambian con cada despliegue, que es el corolario incómodo de ADR-0023):

| | |
|---|---|
| Aplicación | `https://ukun-gateway.ashydesert-78fc288a.francecentral.azurecontainerapps.io` |
| Authorization Server | `https://ukun-auth.ashydesert-78fc288a.francecentral.azurecontainerapps.io` |
| `ukun-api` | `ukun-api.internal.…` — interno, no alcanzable desde fuera |

Comprobado a mano contra ese entorno: `/` y `/catalog` devuelven el SPA, `/api/products` responde 200 anónimo, `/internal/accounts` sigue en 404 y `http://` redirige a HTTPS con un 301.

### Lo que faltaba para poder probarlo de verdad

El primer despliegue salió **vacío**: `application.yaml` trae `contexts: prod` por defecto —y con razón, porque el modo de fallo de no fijarlo es que producción se siembre sola con cuentas cuya contraseña está escrita en `docs/06`— así que el catálogo no tenía un solo producto y no había con qué iniciar sesión. Dos cambios:

- **`SPRING_LIQUIBASE_CONTEXTS=demo`** en `ukun-api` y `ukun-auth`, desde un parámetro `liquibaseContexts` de la plantilla que admite `demo` o `prod`. Es lo que ADR-0023 ya asumía de este entorno. **Con el aviso escrito al lado:** la contraseña de las catorce cuentas está en el repositorio y el ingress es público, así que quien dé con el FQDN entra como administrador. Se acepta porque no hay ni un dato real y el entorno se destruye al terminar.
- **Las 24 fotos de producto**, subidas al share de Azure Files por `deploy.yml` con `az storage file upload-batch`, después de desplegar. No van dentro de ninguna imagen a propósito: `image_key` es una clave de almacenamiento y no una ruta, y el adaptador la resuelve contra un directorio que en v1.1 pasa a ser un bucket (issue #57).

El humo gana además una petición que recorre entera la cadena que nada más prueba: `GET /media/products/mesa-madera.jpg` tiene que devolver `image/jpeg`. Eso valida el share montado, sus permisos de uid/gid —el riesgo que quedaba pendiente de las `mountOptions`— y la ruta `/media/**` del gateway hacia el módulo `storage`, todo de una vez.

## 2026-09-01 · El entorno sembrado, y el fallo que el humo no veía

Segundo despliegue completo, esta vez desde cero (un `teardown` en curso hizo fallar el intento anterior con `ResourceGroupBeingDeleted` — ver más abajo). En verde, y con el entorno ya poblado: **100 productos, 4 tiendas, 10 tipos**, y las **24 imágenes servidas**, comprobadas una a una contra `/media/products/{key}` y no solo la del humo. Con eso cae el último riesgo abierto de la lista original: las `mountOptions` con `uid=100,gid=101` bastan para que `ukun-api` lea del share.

### Y entonces los recorridos de Playwright encontraron lo que el humo no

Lanzados contra la URL de ACA con `E2E_BASE_URL`: **pasó el de empleada, fallaron el de cliente y el de administrador.** Los dos que fallan son los que dan de alta a alguien; el que pasa es el único que no.

La primera hipótesis —el limitador de tasa por IP, que es lo que uno espera cuando un cliente automatizado dispara desde una sola dirección— **quedó descartada** al lanzar el recorrido de cliente aislado: falló igual. El trace de red de Playwright dio la respuesta: `POST /api/signup` con estado `-1`, o sea una petición que nunca se completa. Y el `429` del limitador, cuando de verdad ocurre, llega instantáneo y con `retryAfterSeconds` — otro síntoma, comprobado a mano.

**La causa es una variable que falta en la plantilla, y es la cuarta URL hacia `ukun-auth`:** `ukun.auth.base-url`, contra quien `HttpAccountsAdapter` llama a `/internal/accounts`. Su valor por defecto en `AuthProperties` es `http://ukun-auth:9000`, un nombre de la red de Docker, y **no aparece en `compose.yaml`** —allí ese default ya es correcto—, así que no estaba en la lista de nombres que había que traducir. Se cayó por omisión, no por mezclar las dos familias de variables que `compose.yaml` distingue con tanto cuidado. Es la última consecuencia de ADR-0023, cometida de la única forma que ese aviso no cubría.

Dos correcciones, y la segunda importa más que la primera:

- **`UKUN_AUTH_BASE_URL` en `ukun-api`**, apuntando al FQDN de auth.
- **El humo hace ahora un alta de cliente de verdad.** Las cuatro comprobaciones que tenía —el SPA, el catálogo, el documento de descubrimiento y una foto— pasaban todas con la saga rota, porque ninguna hace hablar a `ukun-api` con `ukun-auth`. Un despliegue en verde con el alta caída es precisamente lo que un humo tiene que impedir, y este no lo impedía.

### Nota de operación: `teardown` y `deploy` se pisan

`teardown.yml` usa `--no-wait` y termina en verde mientras Azure sigue borrando unos diez minutos. Desplegar en esa ventana falla con `ResourceGroupBeingDeleted`, en el paso 5 de 9. El mensaje es claro pero el fallo llega tarde; merece o una espera en `deploy.yml` o una línea en el resumen del teardown diciendo desde cuándo se puede volver a desplegar.

## 2026-09-01 · Los tres recorridos, en verde contra Azure

```
✓ admin    · da de alta un tipo, un producto con foto y un empleado   27,9 s
✓ cliente  · se da de alta, compra y encuentra su pedido              14,9 s
✓ empleada · gestiona el stock y no puede entrar en administración    13,9 s
```

Lanzados con `E2E_BASE_URL` apuntando al FQDN de ACA. Es la condición de salida de la issue #55, y el recorrido de admin cubre además la **escritura** en el share de Azure Files —sube una foto de producto—, que era la mitad que faltaba de las `mountOptions`: hasta aquí solo se había probado la lectura.

`UKUN_AUTH_BASE_URL` era la causa, confirmada en los logs de `ukun-api` antes de que llegara el arreglo:

```
ResourceAccessException: I/O error on POST request for "http://ukun-auth:9000/internal/accounts"
```

### El humo falló con la plantilla ya correcta, y eso era otro defecto

El despliegue que llevaba el arreglo se declaró **fallido**: el humo corrió 32 segundos después de crearse la revisión nueva de `ukun-api`, con la JVM todavía arrancando. Pasó la espera previa porque **esa espera solo miraba el gateway**, y ese despliegue no lo tocaba —solo cambiaba una variable del API—, así que estaba sano desde el primer segundo.

Es el mismo tipo de error que el de la variable: comprobar una cosa y creer que se ha comprobado otra. `deploy.yml` gana una espera anterior a la de salud, que aguarda a que `latestRevisionName` sea también `latestReadyRevisionName` **en las tres apps**. Son dos esperas porque comprueban cosas distintas: que el contenedor nuevo está en marcha, y que el stack responde.

### Lo que queda de la lista de riesgos original

Ninguno. Los cinco que ADR-0023 y la issue #55 dejaban abiertos —`azure.extensions` a tiempo, los tiempos de la sonda de arranque, el firewall, las `mountOptions` en lectura y en escritura, y el `iss` compuesto desde `defaultDomain`— están comprobados contra el entorno real. Y el coste quedó verificado antes contra la API de precios.

## 2026-09-01 · El firewall, estrechado de verdad

Era el único punto del checklist de la issue #55 que quedaba a medias, y arreglarlo destapó dos cosas.

**La salida `environmentStaticIp` de la plantilla era la IP equivocada.** El runbook decía «verifica antes que sea también la saliente, que es lo que Postgres ve», y al verificarlo resultó que no: `staticIp` del entorno es `51.11.234.46` y lo que Postgres ve es `20.74.0.153`, el `outboundIpAddresses` de las container apps —una sola dirección compartida por las tres—. La salida sigue existiendo pero lleva escrito que no sirve para el firewall.

**La regla de arranque dejaba entrar a los runners de GitHub.** El `0.0.0.0` de Flexible Server significa «cualquier recurso dentro de Azure» de cualquier suscripción, y los runners de Actions corren en Azure. Eso convierte el estrechamiento en algo más que higiene formal.

`deploy.yml` gana un paso que lee la IP saliente real, crea su regla y borra la de arranque; después **comprueba que el propio runner ya no alcanza el 5432**, que es lo que separa una verificación de una intención. Tres detalles que costaron descubrirlos:

- **Se usa la API REST y no `az postgres flexible-server firewall-rule`**: ese comando cambia el significado de `--name` y retira `--rule-name` en la CLI 2.86, así que la sintaxis que funciona en esta máquina (2.83) rompería en un runner más nuevo. La API no se mueve.
- **El borrado de una regla es asíncrono.** ARM responde 202 y `az rest` no espera, de modo que comprobar el acceso justo después daba un falso negativo. Hay un bucle hasta que la regla desaparece de verdad.
- Si ninguna app declara IP saliente, el paso falla en vez de continuar: quedarse sin regla dejaría la base inalcanzable.

Probado a mano contra el entorno antes de escribirlo: con el firewall ya estrechado a `20.74.0.153`, el catálogo responde 200 y **un alta de cliente responde 201 en 0,6 s** — que es la operación que escribe en las dos bases.

Y lo que este arreglo no resuelve, escrito en `15-despliegue.md` §7: en Consumption sin VNet, Azure no garantiza que esa IP saliente sea estable. La salida robusta es la integración con VNet, que **no cuesta dinero** —ADR-0023 descartó la red privada por el coste de un Private Endpoint, que no es lo mismo que el modo *private access* con subnet delegada— sino complejidad.

## 2026-09-06 · El dominio propio, y el orden que Azure impone

El entorno se levanta bajo `ukun.sandoretti.dev`, un subdominio de Cloudflare apuntando al gateway. El entorno de este ciclo es `victoriousbeach-ebf83981.francecentral`, y ese sufijo es justamente lo que el `teardown.yml` nuevo conserva para que el CNAME no haya que reescribirlo nunca más.

**Solo el gateway lleva dominio.** `ukun-auth` se queda en su FQDN de Azure, así que `UKUN_AUTH_ISSUER` no se mueve y no hay ninguna ventana con tokens vivos cuyo `iss` haya cambiado. El precio, visible: durante el login se ve la URL larga de Azure en la pantalla de contraseña.

### El primer despliegue con dominio falló, y el mensaje sí decía por qué

```
RequireCustomHostnameInEnvironment: Creating managed certificate requires hostname
'ukun.sandoretti.dev' added as a custom hostname to a container app or route in
environment 'ukun-env'
```

La plantilla tenía la dependencia invertida: el binding del ingress pedía el `certificateId`, así que Bicep creaba el certificado **antes** de tocar la app. Azure exige lo contrario — el hostname tiene que constar ya en el ingress para emitir el certificado —, y como el binding con certificado no se puede escribir sin certificado, **son tres estados y no dos**:

1. Ingress con el hostname y `bindingType: Disabled`. Aquí Azure valida el CNAME y el TXT `asuid`.
2. Certificado emitido, ya con el hostname constando en el entorno.
3. Binding a `SniEnabled` apuntando al certificado.

No cabe en una pasada de Bicep, así que la plantilla gana el parámetro `certificateReady` y `deploy.yml` recorre las dos primeras fases en una sola ejecución: un paso previo mira el `bindingType` actual y, si no es `SniEnabled`, despliega dos veces. Las ejecuciones siguientes hacen una sola pasada.

**El fallo no dejó nada a medias**, que era lo que la plantilla prometía en su comentario: ARM creaba el certificado antes que la app, así que al fallar el certificado la app ni se tocó. Comprobado en caliente — `customDomains: null`, ningún certificado en el entorno, y el gateway respondiendo `200` en `/actuator/health` por su FQDN de Azure con el despliegue anterior intacto.

### Lo que sigue sin comprobarse

- Que el certificado se emita de verdad con las dos pasadas, y que el dominio sirva por HTTPS.
- Que el apagado deje el grupo con los dos recursos que conserva y que el paso de verificación pase.
- Que el despliegue siguiente reutilice el entorno conservado y el dominio responda **sin tocar Cloudflare**, que es la afirmación central del montaje.
- Que Cost analysis no muestre ningún cargo con el entorno apagado. De ese supuesto depende conservar el entorno; si resulta falso, se vuelve a `borrar_todo` y el CNAME pasa a reescribirse en cada ciclo.

## 2026-09-06 (tarde) · El dominio, servido; y el apagado, atascado en la única app que lo llevaba

`https://ukun.sandoretti.dev` sirve el stack entero. Comprobado contra el entorno real: binding `SniEnabled` sobre el certificado `ukun-sandoretti-dev` en estado `Succeeded`, cadena TLS válida desde fuera (`ssl_verify_result: 0`, emitido por GeoTrust/DigiCert, hasta 2027-03-06), `/actuator/health` en `UP`, el SPA, `/api/products` en 200, una foto de producto en `image/jpeg` —o sea el share montado y servido— y la redirección de login llevando `redirect_uri=https://ukun.sandoretti.dev/login/oauth2/code/ukun`, que es la prueba de que `RegisteredClientSeeder` reescribió las URIs sin que nadie tocara nada.

El certificado se emitió a la segunda: el `certificateReady` de esta misma tarde hizo su trabajo.

### El teardown se quedó colgado, y solo podía pasarle al gateway

El apagado borró `ukun-auth` y `ukun-api` y se quedó esperando indefinidamente en `ukun-gateway`, que quedó en `provisioningState: Failed` con `customDomains: null`.

La causa es el `az resource delete --ids` genérico del workflow. Sobre una container app **con un dominio propio atado**, la operación genérica de ARM hace una actualización parcial en vez de un borrado. Las otras dos cayeron sin problema porque no tenían dominio, y ahí está lo que lo hizo invisible: **el fallo no se reproduce en un entorno sin `gatewayHost`**, que es como se había probado todo hasta hoy. La primera vez que hubo un dominio fue también la primera vez que el teardown tuvo que borrarlo.

El paso pasa a `az containerapp delete`, que es el comando específico del proveedor. El borrado por exclusión del resto se queda igual: esto no es una lista de recursos que mantener al día, sino un tipo tratado aparte porque su proveedor lo exige.

**Lo que esto confirma de paso:** el certificado gestionado aparece en `az resource list` como recurso propio, de tipo `Microsoft.App/managedEnvironments/managedCertificates`. El filtro de conservación usa `starts_with` y no igualdad justamente por eso — con igualdad exacta, el apagado se habría llevado por delante el certificado del dominio.

### El comando específico tampoco lo borró, y eso deja el diagnóstico a medias

`az containerapp delete` **no arregló el caso ya atascado**. Tres intentos —el comando específico dos veces y un `DELETE` por la API REST— fueron aceptados por ARM (`Microsoft.App/containerApps/delete`, estado `Accepted` a las 22:04 UTC) y ninguno completó en más de media hora. No hay locks en el grupo ni en el recurso, y el certificado no era el culpable: la app ya tenía `customDomains: null`, así que no lo referenciaba.

Lo que sí se sabe es que **la app ya no existe en el plano de Container Apps**: `az containerapp revision list` responde `ContainerAppNotFound`. Lo que queda es un registro huérfano en ARM, sin revisiones ni réplicas, y por tanto **sin coste**.

Eso obliga a separar dos cosas que es cómodo confundir: el cambio a `az containerapp delete` está justificado —la operación genérica dejó el recurso en `Failed` con `customDomains: null`, que es una actualización parcial y no un borrado—, pero **no está demostrado que evite el atasco**, sólo que no lo provoca de la misma manera. Puede que lo que bloquea sea el estado `Failed` previo, el dominio atado, o algo del proveedor que no se ha visto. Se sabrá en el primer ciclo Deploy → Teardown completo con dominio, que es lo que hay que hacer antes de dar el arreglo por bueno.

El resto del apagado se completó a mano con los comandos específicos: `az storage account delete` y `az postgres flexible-server delete`, las dos únicas partidas que facturaban. El grupo queda con `ukun-env`, `ukun-logs`, el certificado y el registro huérfano.

### Sigue sin comprobarse

- Si `az containerapp delete` basta en un ciclo limpio, o si el atasco se repite con dominio atado.
- Que el apagado termine solo, sin intervención, y que su paso de verificación pase — hoy ese paso no llegó a ejecutarse.
- Que el despliegue siguiente reutilice el entorno y el dominio responda **sin tocar Cloudflare**.
- Que Cost analysis no muestre cargos con el entorno apagado. Los cargos de Azure tardan horas en consolidarse, así que esto se mira al día siguiente.
