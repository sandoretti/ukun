# Despliegue en Azure Container Apps

> **Fuente de verdad de:** cómo se levanta, se actualiza, se apaga y se publica bajo un dominio propio el entorno desplegado, qué hay que preparar una vez a mano, y de dónde sale cada variable en producción.
> **Léelo si:** vas a desplegar, a cambiar la plantilla de Bicep, o estás mirando por qué el entorno desplegado se comporta distinto que `tilt up`.
> **No decide:** por qué el destino es Container Apps y no otra cosa (ADR-0023), ni el entorno local (`06-entorno-local-tilt.md`), ni el reparto de tests en CI (`13-testing.md` §3).
> **Idioma:** los nombres de recurso, variable y base de datos, en inglés (ADR-0018).

El entorno es **efímero a propósito**. La suscripción es Azure for Students: 100 USD para doce meses, sin tarjeta y con límite de gasto activado, de modo que agotar el crédito no genera una factura — **suspende los recursos**. Apagar no es higiene, es el mecanismo que impide que el crédito se acabe solo. Se puede destruir sin pensarlo porque ADR-0007 declaró los datos desechables y el changelog con `context: demo` vuelve a sembrar las bases en cada arranque.

Todo vive en tres sitios: `deploy/azure/main.bicep` (los recursos), `.github/workflows/deploy.yml` (levantar y actualizar) y `.github/workflows/teardown.yml` (apagar).

## 1. Qué se despliega

Tres contenedores Java y una base de datos — **no cuatro contenedores**: el gateway sirve el SPA estático desde su propia imagen (skill `spring-gateway` §6), y `ukun-web` solo existe como contenedor propio en desarrollo, donde Vite sirve su dev server con HMR.

| Recurso | Perfil | Notas |
|---|---|---|
| Container Apps Environment | Consumption | Con su Log Analytics, que ACA exige |
| `ukun-gateway` | 0,5 vCPU / 1 GiB, `min=1`, **`max=1`** | Ingress **externo**. El único origen que ve el navegador |
| `ukun-auth` | 0,5 vCPU / 1 GiB, `min=1`, `max=3` | Ingress **externo** también — el navegador teclea ahí la contraseña |
| `ukun-api` | 0,5 vCPU / 1 GiB, `min=1`, `max=3` | Ingress **interno**. Solo lo alcanza el gateway |
| PostgreSQL Flexible Server | Burstable B1ms, 32 GB, sin HA | Un servidor, **tres** bases: `ukun_app`, `ukun_auth`, `ukun_gateway` |
| Azure Files | Standard, 5 GB | Montado en `ukun-api`, sustituye el bind mount de `resources/uploads` |
| Certificado gestionado | — | Solo si hay dominio propio (§6). Hijo del entorno, emitido y renovado por Azure |

**`max=1` es solo del gateway**, y el motivo está escrito al lado en la plantilla para que nadie lo suba «porque los otros dos ya se puede»: su limitador de tasa de `/api/**` guarda los buckets en memoria a propósito (ADR-0024). Todo lo demás que impedía desplegar con más de una réplica —clave de firma, sesión, purga, jobs y limitador de login— se resolvió en la issue #118 y vive en Postgres.

**Las imágenes están en GHCR y sus tres paquetes son públicos**, así que ACA hace el pull sin credencial. Si un despliegue deja las apps en `ImagePullBackOff`, lo primero que hay que mirar es si el paquete sigue privado: se crean así en su primer push y hay que marcarlos públicos una vez a mano en la página del paquete.

## 2. Preparación, una sola vez

Nada de esto lo puede hacer un workflow: es lo que crea la identidad con la que el workflow se autentica. Hace falta `az` en la máquina (ADR-0023 ya lo anota como requisito) y ser Owner de la suscripción, que es lo normal en Azure for Students.

### Instalar `az`

En Fedora está en los repositorios de la distribución y no hace falta añadir el de Microsoft:

```bash
sudo dnf install azure-cli
```

La extensión de Container Apps **no hace falta para desplegar** —el despliegue es Bicep, y `az deployment group create` es un comando del núcleo—, sí para diagnosticar (`az containerapp logs show`, `az containerapp revision list`):

```bash
az extension add --name containerapp
```

El compilador de Bicep se descarga solo la primera vez que se invoca (`az bicep build`). En CI eso lo hace el job `infra` de `ci.yml`.

### Autenticarse y elegir la suscripción

```bash
az login
az account set --subscription <ID de la suscripción>
az account show --output table          # confirma que es la de estudiante y está Enabled

# Sin esto, el primer despliegue falla al crear el recurso de un proveedor no registrado.
for p in Microsoft.App Microsoft.OperationalInsights Microsoft.DBforPostgreSQL Microsoft.Storage; do
  az provider register --namespace "$p"
done
```

### La región no se elige libremente

La suscripción de estudiante lleva una política **«Allowed resource deployment regions»** que restringe dónde se puede desplegar. `westeurope` **no** está permitida, que es la primera región que uno escribe. Consúltala antes de elegir, porque el error no llega al crear el grupo de recursos —eso funciona— sino al crear el primer recurso dentro, como `RequestDisallowedByAzure`:

```bash
az policy assignment list --query "[].{nombre:displayName, params:parameters}" -o json
```

Si la región elegida resulta prohibida, el grupo ya creado se queda con la ubicación mala: hay que borrarlo y rehacerlo, porque la ubicación de un grupo de recursos no se cambia.

### La identidad va en su propio grupo de recursos

`teardown.yml` borra el grupo entero, así que la managed identity **no puede vivir dentro**: se llevaría por delante el permiso con el que se vuelve a desplegar.

```bash
az group create --name ukun-identity --location westeurope
az identity create --name ukun-github --resource-group ukun-identity

CLIENT_ID=$(az identity show -n ukun-github -g ukun-identity --query clientId -o tsv)
PRINCIPAL_ID=$(az identity show -n ukun-github -g ukun-identity --query principalId -o tsv)
SUB_ID=$(az account show --query id -o tsv)
TENANT_ID=$(az account show --query tenantId -o tsv)
```

**Credencial federada de OIDC**, que es lo que sustituye a un secreto de service principal guardado en el repo. Es también la razón por la que ADR-0023 elige una managed identity y no un registro de aplicación: crear una identidad es un permiso de Azure, no de Entra ID, y en una cuenta de estudiante con el tenant de la universidad el segundo puede no estar disponible.

```bash
az identity federated-credential create \
  --name github-main \
  --identity-name ukun-github \
  --resource-group ukun-identity \
  --issuer https://token.actions.githubusercontent.com \
  --subject repo:sandoretti/ukun:ref:refs/heads/main \
  --audiences api://AzureADTokenExchange
```

**El `--subject` es literal y es el fallo número uno de este montaje.** Autoriza exactamente a los workflows que corren *desde la rama `main`*. Lanzar `deploy.yml` desde otra rama falla en `azure/login` con un error que habla de credenciales y no de la rama; si hace falta, se añade otra credencial federada con su propio sujeto.

**Rol a nivel de suscripción**, porque `deploy.yml` crea el grupo de recursos y `teardown.yml` lo borra:

```bash
az role assignment create \
  --assignee-object-id "$PRINCIPAL_ID" --assignee-principal-type ServicePrincipal \
  --role Contributor \
  --scope "/subscriptions/$SUB_ID"
```

### Variables y secretos del repositorio

Los tres identificadores de Azure **no son secretos** —identifican, no autorizan; sin la credencial federada no sirven de nada— y por eso van como variables, donde se pueden leer al depurar:

```bash
gh variable set AZURE_CLIENT_ID       --body "$CLIENT_ID"
gh variable set AZURE_TENANT_ID       --body "$TENANT_ID"
gh variable set AZURE_SUBSCRIPTION_ID --body "$SUB_ID"
gh variable set AZURE_RESOURCE_GROUP  --body ukun-demo
gh variable set AZURE_LOCATION        --body westeurope
```

Y una quinta, **opcional y que no se define todavía**: `UKUN_GATEWAY_HOST`, el subdominio propio del gateway. Tampoco es un secreto —un nombre de dominio no autoriza nada— y sin ella el entorno se sirve solo por su FQDN de Azure. Se define **después** del primer despliegue, porque el certificado se emite validando un DNS que hasta entonces no se puede escribir: el procedimiento entero está en §6.

Los cuatro valores sensibles, como secretos. Son los mismos que `.env.example` explica para el entorno local, generados igual:

La contraseña de Postgres **no se puede generar con `openssl rand -hex`**, que es lo que usa el resto: Azure exige caracteres de al menos **tres** de las cuatro categorías (mayúsculas, minúsculas, dígitos, no alfanuméricos) y un hexadecimal solo tiene dos. El servidor se rechaza al crearlo, a mitad del despliegue y con el grupo de recursos ya montado. Alfanumérico de 32 con las tres categorías comprobadas:

```bash
until PW=$(openssl rand -base64 48 | tr -d '\n=+/' | cut -c1-32) &&
      printf '%s' "$PW" | grep -q '[A-Z]' &&
      printf '%s' "$PW" | grep -q '[a-z]' &&
      printf '%s' "$PW" | grep -q '[0-9]'; do :; done
gh secret set POSTGRES_PASSWORD --body "$PW"
```

Los dos secretos de OAuth2 sí van en hexadecimal: no los valida Azure, solo tienen que coincidir entre sus dos lectores.

```bash
gh secret set UKUN_OAUTH2_GATEWAY_SECRET   --body "$(openssl rand -hex 32)"
gh secret set UKUN_OAUTH2_API_SECRET       --body "$(openssl rand -hex 32)"

# La clave de firma: PKCS#8, base64 del DER, en una línea (ADR-0024).
gh secret set UKUN_SIGNING_KEY --body "$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 -w0)"
```

Los dos secretos de OAuth2 tienen **dos lectores cada uno** y los dos tienen que coincidir, igual que en local: `UKUN_OAUTH2_GATEWAY_SECRET` lo registra `ukun-auth` y lo presenta el gateway al canjear el código —un desajuste sale como `invalid_client` al final del login, no al arrancar—, y `UKUN_OAUTH2_API_SECRET` es el mismo emparejamiento un salto más allá. La plantilla los pasa a las dos apps de cada par desde el mismo parámetro, así que no se pueden desalinear por descuido.

## 3. Levantar y actualizar

**Antes del primer despliegue, dos cosas tienen que haber pasado ya, y las dos son consecuencia de cómo funciona GitHub, no del diseño.** Una: `workflow_dispatch` solo aparece en la pestaña Actions si el workflow está en la **rama por defecto**, así que mientras `deploy.yml` viva en una rama de trabajo, el botón «Run workflow» no existe. Dos: el job `images` solo corre en `push` a `main`, así que hasta que eso ocurra no hay ninguna imagen que desplegar — y sus tres paquetes de GHCR se crean **privados**, hay que marcarlos públicos una vez a mano. El orden, entonces, es: mergear a `main` → esperar a que CI publique → marcar los paquetes públicos → desplegar.

Son la misma operación. `deploy.yml` ejecuta el Bicep completo, que es idempotente, y ACA crea una revisión nueva por cada cambio de imagen:

```
Actions → Deploy → Run workflow → image-tag: <SHA del commit>
```

El SHA es el de un commit cuyas imágenes publicó el job `images` de `ci.yml`, que solo corre en `push` a `main`. **Nunca `main` como etiqueta:** un despliegue tiene que poder repetirse dando el mismo resultado, y `main` se mueve.

Lo que **no** se hace es `az containerapp update --image`. Es más rápido y deja la plantilla desincronizada del entorno, que con infraestructura efímera es exactamente lo que hace que el siguiente despliegue desde cero se comporte distinto del que está corriendo.

El workflow comprueba antes de tocar Azure que las tres imágenes existen y se bajan sin credencial, espera a que el gateway responda `UP`, corre tres peticiones de humo y deja las URLs en el resumen de la ejecución. Lo que **no** valida es el recorrido de login → catálogo → carrito → pedido: eso se hace a mano contra la URL que devuelve, y es la condición de salida de la issue #55.

## 4. De dónde sale cada variable

Es la tabla que evita el fallo que no da un error claro. La partición que manda es la de `compose.yaml` —qué URL **sigue el navegador** y qué URL **resuelve el servicio**— y en Azure se simplifica: como `ukun-auth` lleva ingress externo, para él las dos son la misma. **El único destino que sigue siendo interno es `ukun-api`.**

| Variable | Quién la lee | En Azure | En `tilt up` |
|---|---|---|---|
| `UKUN_AUTH_ISSUER` | los tres | `https://ukun-auth.<defaultDomain>` | `http://localhost:9000` |
| `UKUN_GATEWAY_BASE_URL` | auth, gateway | el dominio propio si `UKUN_GATEWAY_HOST` está definida (§6), y si no `https://ukun-gateway.<defaultDomain>` | `http://localhost:8080` |
| `UKUN_AUTH_TOKEN_URI` / `_JWK_SET_URI` | api, gateway | el mismo FQDN de auth | nombre de contenedor `ukun-auth:9000` |
| `UKUN_API_URI` | gateway | `https://ukun-api.internal.<defaultDomain>`, **sin puerto** | `http://ukun-api:8081` |
| `UKUN_AUTH_BASE_URL` | api | el FQDN de auth | `http://ukun-auth:9000` (default del código) |
| `SPRING_DATASOURCE_URL` | los tres | FQDN de Postgres + **`?sslmode=require`** | `db:5432` |
| `SERVER_REACTIVE_SESSION_COOKIE_SECURE` | gateway | `true` | sin definir |
| `SPRING_LIQUIBASE_CONTEXTS` | api, auth | **`demo`** — parámetro `liquibaseContexts` | lo pone el perfil `dev` |
| `UKUN_SIGNING_KEY` | auth | secreto de la container app | `.env` |
| `MANAGEMENT_*_OTLP_*` | los tres | **sin definir** | las pone `compose.dev.yaml` |

### El entorno se siembra con los datos de prueba

`SPRING_LIQUIBASE_CONTEXTS=demo` es lo que hace que este despliegue **no** arranque vacío: el mismo changelog que puebla `tilt up` siembra aquí los 100 productos con sus tipos y su stock, las tiendas, y las **14 cuentas demo, que comparten la contraseña `ukun-demo-2026`** (`06-entorno-local-tilt.md`). Una de cada rol para entrar rápido: `joel.sandoval@email.com` (cliente), `laura.martinez@ukun.com` (empleada), `salvador.oton@ukun.com` (empleado + admin).

Es lo que ADR-0023 asume de este entorno —se levanta para una demo, sus datos son desechables (ADR-0007)—, pero conviene decir lo que implica: **la contraseña está escrita en este repositorio y el ingress es público**, así que quien dé con el FQDN entra como administrador. Se acepta porque aquí no hay ni un dato real y el entorno se destruye al terminar. El día que eso deje de ser cierto, el parámetro `liquibaseContexts` pasa a `prod` y deja de sembrar; el valor por defecto de `application.yaml` ya es ese, así que el cambio es quitar la variable.

**Las fotos de producto no viajan dentro de ninguna imagen.** `product.image_key` es una clave de almacenamiento y no una ruta (`08-identidad-y-seguridad.md` §5), así que las 24 de `resources/uploads` —a las que apuntan las `image_key` de los 100 productos, reutilizadas por tipo— las sube `deploy.yml` al share de Azure Files después de desplegar, con `az storage file upload-batch`. Meterlas en la imagen las volvería inmutables y convertiría la subida de RF-10 en una excepción al mismo mecanismo. Sin ese paso el catálogo se pinta entero y cada ficha da un 404 en su foto.

Cuatro cosas de esa tabla que no son evidentes:

- **Ninguna URL se escribe a mano.** El FQDN de ACA es `<app>.<defaultDomain>` y `defaultDomain` se conoce en cuanto existe el entorno, así que la plantilla las compone sola. Escribirlas obligaría a desplegar en dos pasadas y a mantener sincronizado a mano el `iss`, que `ukun-api` compara **literalmente** contra el claim del token.
- **`UKUN_AUTH_BASE_URL` es la cuarta URL hacia `ukun-auth` y la que se olvida.** Es contra quien `HttpAccountsAdapter` llama a `/internal/accounts` para la saga de signup, y **no aparece en `compose.yaml`**: allí el valor por defecto de `AuthProperties` (`http://ukun-auth:9000`) ya es el bueno, así que no está en la lista de nombres que hay que traducir y se cae por omisión, no por confundir las dos familias de variables. El síntoma es que todo funciona menos el alta de un cliente, que se queda sin responder.
- **`UKUN_API_URI` no lleva puerto.** El ingress interno de ACA escucha en 443 y enruta al `targetPort` de la app. Poner `:8081` es el error clásico de traducir el nombre de la red de Docker.
- **`?sslmode=require` no es opcional.** Flexible Server rechaza las conexiones en claro y el driver JDBC no lo negocia solo. Va con un `maximum-pool-size` de 5, porque un B1ms admite del orden de 50 conexiones para todo: tres servicios que pueden escalar, más Liquibase en cada arranque.
- **Las variables de OTLP se quedan sin definir, y esa ausencia es la configuración** (`docs/11` §5): sin ellas no se instala exportador y los tres arrancan igual. Los logs van a stdout y los recoge Log Analytics, que es lo que ACA hace de serie. No hay colector desplegado.

## 5. Apagar

```
Actions → Teardown → Run workflow → confirm: <el nombre del grupo de recursos>
```

Hay que escribir el nombre del grupo a mano: es lo único que separa este botón de un borrado por accidente.

**Se lleva todo lo que factura y conserva lo que no.** Fuera las tres container apps, el servidor de Postgres con sus tres bases y la cuenta de almacenamiento con el share de uploads. Se quedan el **Container Apps Environment**, su **Log Analytics** y el **certificado gestionado del dominio propio**, que sin réplicas corriendo no tienen medidor: el cargo de Container Apps es por vCPU-segundo y GiB-segundo de las réplicas, y este entorno es Consumption puro (§8).

Conservarlos no es un descuido, es lo que sostiene el dominio propio: el CNAME de Cloudflare apunta al `defaultDomain` del entorno, que lleva un sufijo aleatorio y cambiaría si el entorno se recreara. Con esto, el registro DNS se escribe **una vez** y el ciclo de apagar y volver a levantar no lo toca.

**El borrado es por exclusión y no por lista**, que era la propiedad de borrar el grupo entero y había que no perderla: se va todo lo que hay en el grupo salvo esos dos tipos, así que un recurso que la plantilla gane en el futuro se borra sin tocar el workflow. Y el último paso **lo comprueba** en vez de suponerlo — si queda algo que debería haberse ido, el workflow sale en rojo y lo nombra.

Para liquidar de verdad, incluido el entorno, está el input **`borrar_todo`**. Úsalo solo si dejas de usar el despliegue: el siguiente `deploy.yml` creará un entorno con otro `defaultDomain` y habrá que reescribir el CNAME y esperar a que se reemita el certificado. Ese camino sí borra el grupo entero con `--no-wait`, así que **compruébalo en el portal**: no espera a verlo terminar y un borrado a medias deja recursos facturando.

## 6. El dominio propio

El gateway se sirve además por un subdominio tuyo —`tienda.ejemplo.com`— con certificado emitido y renovado por Azure, gratis. Lo aporta el parámetro `gatewayHost` de la plantilla, que `deploy.yml` rellena desde la variable de repositorio **`UKUN_GATEWAY_HOST`**. Sin esa variable no cambia nada: el entorno se sirve solo por su FQDN de Azure, que es como estaba antes de que esto existiera y como hay que desplegar la primera vez.

### Un subdominio basta, y por qué

De las tres URLs, la única que alguien teclea es la del gateway. `ukun-api` tiene ingress interno y no lo alcanza ningún navegador; `ukun-auth` sí lo alcanza, pero solo llegando de una redirección, así que **se queda con su FQDN de Azure y `UKUN_AUTH_ISSUER` no se mueve**. Eso es lo que hace barato el montaje: el `iss` que `ukun-api` compara **literalmente** contra el claim del token es el mismo antes y después de poner el dominio, y no hay ninguna ventana en la que un token emitido siga vivo con un issuer que cambió.

Lo que sí cambia es `UKUN_GATEWAY_BASE_URL`, de donde salen las redirect URIs del cliente OAuth2. No hay que hacer nada por ello: `RegisteredClientSeeder` reescribe el cliente en cada arranque reutilizando su clave primaria, así que las URIs viejas se van solas.

**El precio, para que no sorprenda:** la pantalla de contraseña la sirve `ukun-auth`, así que durante el login se ve el FQDN largo de Azure y no tu dominio. Es estético, y se arregla dando un segundo subdominio a auth — un CNAME y un certificado más, con `UKUN_AUTH_ISSUER` cambiando de valor.

### El bootstrap, una vez

El orden no se puede saltar, porque el certificado se emite validando el DNS y el DNS no se puede escribir hasta que la app existe:

1. **Desplegar sin la variable.** El resumen del workflow imprime, ya rellenos, los dos registros que hay que crear.
2. **Crearlos en Cloudflare**, con el proxy **desactivado** (nube gris):

   | Tipo | Nombre | Contenido |
   |---|---|---|
   | `CNAME` | `tienda` | `ukun-gateway.<defaultDomain>` — la salida `gatewayFqdn` |
   | `TXT` | `asuid.tienda` | la salida `customDomainVerificationId` |

3. **Definir la variable y volver a desplegar.** Ahora la plantilla da de alta el hostname, emite el certificado, lo ata al ingress y compone las URLs con tu dominio:

   ```bash
   gh variable set UKUN_GATEWAY_HOST --body tienda.ejemplo.com
   ```

A partir de ahí es un solo paso para siempre: apagar y volver a levantar no toca el DNS.

#### Por qué ese tercer paso despliega dos veces por dentro

Azure impone un orden que no cabe en una sola pasada de Bicep, y el error que da si se ignora nombra la causa pero no la salida:

```
RequireCustomHostnameInEnvironment: Creating managed certificate requires hostname
'tienda.ejemplo.com' added as a custom hostname to a container app or route in
environment 'ukun-env'
```

El certificado no se puede emitir hasta que el hostname consta en el ingress, y el binding con certificado no se puede escribir hasta que el certificado existe. **Son tres estados, no dos:** el ingress declara el hostname con `bindingType: Disabled` —Azure valida ahí el CNAME y el `asuid`, y el dominio pasa a constar en el entorno—, después se emite el certificado y el binding pasa a `SniEnabled`.

Lo recorre `deploy.yml`: un paso previo mira en qué fase está el dominio y, si hace falta, ejecuta la plantilla dos veces con `certificateReady` en `false` y luego en `true`. Quien despliega no ve la distinción — es el mismo botón. Y no se repite: en las ejecuciones siguientes el binding ya es `SniEnabled`, el paso lo detecta y solo hay una pasada.

### La nube tiene que estar gris

Con el proxy de Cloudflare activado (nube naranja) **el certificado no se emite**: Azure resuelve el CNAME, encuentra IPs de Cloudflare en vez de tu app y la validación falla. Y no es solo la emisión — la renovación automática vuelve a validar meses después, así que activar el proxy más tarde rompe el dominio cuando ya nadie se acuerda de haberlo tocado.

Hay una segunda razón, y es de este código: `RequestRateLimitWebFilter` usa la dirección remota como clave y **no lee `X-Forwarded-For` a propósito** (es una cabecera que pone quien llama). Detrás del proxy, todos los visitantes llegarían con la IP de Cloudflare y compartirían un solo presupuesto — el límite de `/api/**` y los 5 cada 10 minutos de `/api/signup` saltarían para todo el mundo a la vez.

Lo que se pierde con la nube gris es el CDN y el ocultamiento de la IP de origen. Para un entorno de demo con datos desechables, es el intercambio correcto.

### El FQDN de Azure no desaparece

El ingress responde por los dos nombres. Eso es lo que deja una vía de entrada cuando el DNS o el certificado están a medias, y lo que permite que el paso 1 del bootstrap sea un despliegue normal y completo.

### Si el dominio deja de responder

El paso «Esperar a que el gateway esté sano» va contra la URL pública, así que con la variable puesta es él quien comprueba que el CNAME resuelve y el certificado sirve. Cuando falla lo dice y nombra el registro a revisar. Los dos valores correctos están en el resumen de **cualquier** ejecución de `deploy.yml`, no solo la primera: se imprimen siempre para poder cotejarlos con lo que haya en Cloudflare. Mientras tanto, la aplicación sigue en pie por su FQDN de Azure.

## 7. El firewall de Postgres

`deploy.yml` deja la base accesible **solo desde la IP saliente de las container apps**. No es lo que hace la plantilla: ésta crea una regla de arranque `AllowAzureServices` porque hay una dependencia circular —la app tiene que existir para conocer su IP saliente, y no arranca sin base—, y el workflow la sustituye en cuanto las tres apps están en marcha.

**Esa regla de arranque no es inocua y por eso no se queda.** El `0.0.0.0` de Flexible Server significa «cualquier recurso dentro de Azure», de cualquier suscripción, y eso incluye a los runners de GitHub Actions, que corren en Azure. Si ves `AllowAzureServices` en un entorno desplegado, es que ese paso no llegó a correr.

**La IP que hay que autorizar es `outboundIpAddresses` de las container apps, NO `staticIp` del entorno.** Son direcciones distintas y confundirlas es el error natural, porque la segunda es la que suena a «la IP del entorno»: en el primer despliegue fueron `51.11.234.46` (entrada) y `20.74.0.153` (salida). La salida `environmentStaticIp` de la plantilla es la de entrada y lleva escrito que no sirve para esto.

El paso se verifica a sí mismo: después de estrechar, comprueba que el propio runner ya **no** alcanza el 5432. Antes lo alcanzaba, precisamente por la regla de arranque.

### Lo que esto no resuelve

En un entorno Consumption **sin VNet, Azure no garantiza que la IP saliente sea estable.** Si cambia en caliente, las tres apps pierden la base hasta el siguiente despliegue, que vuelve a estrechar sobre la nueva. En un entorno que se levanta para una demo y se destruye, es asumible; el síntoma es claro (todo responde menos lo que toca la base) y el arreglo es volver a desplegar.

La salida robusta es la **integración con VNet** de Flexible Server, y conviene decir por qué no está hecha: ADR-0023 descartó la red privada citando el coste del enlace privado, lo cual es cierto de un **Private Endpoint** pero no del modo *private access* con subnet delegada, que **no cuesta nada extra**. Lo que cuesta es complejidad —una VNet con subnet dedicada para el entorno, la delegación de Postgres, DNS privado para su FQDN— y que quien despliega pierde el `psql` desde su máquina sin un salto intermedio. Para un entorno efímero de estudio con datos desechables es desproporcionado; es la respuesta el día que deje de serlo.

### A mano, si hace falta

```bash
SERVER=$(az postgres flexible-server list -g ukun-demo --query '[0].name' -o tsv)
IP=$(az containerapp show -n ukun-api -g ukun-demo --query 'properties.outboundIpAddresses' -o tsv)
BASE="https://management.azure.com/subscriptions/<sub>/resourceGroups/ukun-demo"
BASE="$BASE/providers/Microsoft.DBforPostgreSQL/flexibleServers/$SERVER/firewallRules"

az rest --method put --url "$BASE/aca-outbound-$(echo $IP | tr . -)?api-version=2024-08-01" \
  --body "{\"properties\":{\"startIpAddress\":\"$IP\",\"endIpAddress\":\"$IP\"}}"
```

La API REST y no `az postgres flexible-server firewall-rule`: ese comando cambia el significado de `--name` y retira `--rule-name` en la CLI 2.86, así que la sintaxis que funciona en una máquina rompe en otra más nueva.

## 8. Coste

**Verificado el 2026-09-01** contra la [API de precios de Azure](https://prices.azure.com/api/retail/prices) para `francecentral`, en USD y a precio de lista al contado. Los precios se mueven: la forma de rehacer esta tabla es consultar esa misma API, no la calculadora, porque así queda reproducible.

| Partida | Precio unitario | En este despliegue |
|---|---|---|
| Postgres B1ms (cómputo) | 0,019 USD/hora | 0,019 USD/h encendido |
| Postgres, disco de 32 GB | 0,133 USD/GB/mes | 4,26 USD/mes → 0,0058 USD/h |
| Postgres, backup 7 días | incluido hasta el tamaño aprovisionado | 0 |
| Container Apps, vCPU en reposo | 0,000003 USD/vCPU-segundo | 1,5 vCPU entre las tres |
| Container Apps, memoria | 0,000003 USD/GiB-segundo | 3 GiB entre las tres |
| Log Analytics, ingesta | **5 GB/mes gratis**, después 2,76 USD/GB | ~0 con `root: WARN` |
| Azure Files LRS | 0,075 USD/GB/mes | céntimos |

**La franja gratuita mensual de Container Apps decide casi todo.** Son 180 000 vCPU-segundos y 360 000 GiB-segundos por suscripción y mes, y con el reparto de recursos de este despliegue —1,5 vCPU y 3 GiB sumando las tres apps— las dos se agotan a las **33 horas de entorno encendido al mes**. Por debajo de esa marca, Container Apps no cuesta nada y lo único que corre el contador es Postgres.

| Escenario | Horas/mes | Coste |
|---|--:|--:|
| Una demo de 3 h | 3 | **0,07 USD** |
| Una tarde de trabajo | 8 | **0,20 USD** |
| Cuatro demos de 3 h | 12 | **0,30 USD** |
| Una semana laboral de 8 h | 40 | **1,32 USD** |
| Un fin de semana entero encendido | 48 | 1,91 USD |
| Dos semanas laborales | 80 | 4,26 USD |
| **Un mes 24/7** | 730 | **52,06 USD** |

O sea: **el uso puntual para el que está pensado esto cuesta céntimos**, del orden de 0,025 USD por hora encendido mientras se esté por debajo de las 33 h/mes. Y dejarlo encendido sin querer cuesta 1,71 USD al día, con lo que el crédito de 100 USD se agota en **1,9 meses**. Esa diferencia —dos órdenes de magnitud— es toda la justificación del entorno efímero y de que `teardown.yml` exista.

### Lo que cuesta el entorno apagado

Cero, y de ese supuesto depende que `teardown.yml` conserve el Container Apps Environment (§5). Las tres partidas que sobreviven al apagado no tienen medidor sin carga:

- **Container Apps Environment**: se factura por vCPU-segundo y GiB-segundo *de las réplicas*. Sin container apps no hay réplicas. El único cargo fijo que existe en ACA es `Environment Management Hour` (0,125 USD/hora, 91 USD/mes), y es de los entornos con *workload profiles*: `main.bicep` no declara ninguno a propósito, así que este entorno es Consumption puro y no lo tiene.
- **Log Analytics**: cobra por ingesta, y sin apps no se ingiere nada. Los datos ya recogidos entran en los 5 GB/mes gratis, y `retentionInDays: 30` cae dentro de la retención sin coste.
- **Certificado gestionado**: gratis, emisión y renovación incluidas.

**Compruébalo en el portal la primera vez que apagues**, en Cost analysis del grupo, y si aparece cualquier cargo con el entorno apagado, esta decisión se cae y hay que volver a borrar el grupo entero (`borrar_todo`) a costa de reescribir el CNAME en cada ciclo.

### El dimensionado, y por qué es el que es

Las cifras de arriba salen de 1,5 vCPU y 3 GiB repartidos en tres apps de 0,5/1 GiB cada una. **`ukun-auth` no lleva los 0,25 vCPU / 0,5 GiB que tabulaba ADR-0023**, y el motivo es una medida y no una precaución: sobre el stack local en reposo, `docker stats` da **368 MiB al gateway, 412 MiB a auth y 603 MiB a api**, sin nadie usando el sistema. Un límite de 512 MiB para algo que ocupa 412 en vacío no deja sitio para el primer login, y en Container Apps pasarse del límite no degrada el servicio: mata el contenedor.

Los tres llevan además `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70`. Sin él la JVM se queda con el 25 % de la memoria del contenedor —256 MiB de heap dentro de 1 GiB— y el resto se paga sin usarse; el síntoma no sería un contenedor muerto sino GC continuo mientras el panel enseña memoria libre, que es de los diagnósticos más difíciles de atribuir.

**Para cuatro personas a la vez esto sobra**, y el cuello de botella no sería ninguno de los tres servicios: Postgres B1ms da 1 vCore y admite del orden de 50 conexiones, contra las 15 como mucho que abren los tres pools juntos (5 cada uno). Lo que hay que vigilar con varias personas no es la capacidad sino el **limitador de tasa**, que cuenta por IP: si todos navegan desde la misma red con NAT, el gateway ve una sola dirección y los 120 req/min de `/api/**` se reparten entre todos. Ver §9.

### El cargo que convertiría esto en otra cosa

Container Apps tiene un medidor **`Environment Management Hour` de 0,125 USD/hora**, que son 91 USD al mes y se comería el crédito entero. **No aplica a este despliegue** y no es casualidad: ese cargo es de los entornos con *workload profiles*, y `main.bicep` no declara ninguno, así que el entorno es Consumption puro. Es la razón por la que añadir un `workloadProfiles` a la plantilla no es una mejora inocente.

### Lo que estas cifras no incluyen

Salida de datos y transacciones de almacenamiento, que a esta escala son ruido. Tampoco impuestos, que en una suscripción de estudiante no se aplican: se consume crédito, no se factura. Y la franja gratuita de Container Apps no viene en la API de precios —es una franja de producto, no un precio—, así que si algún día desapareciera habría que sumar 0,0486 USD por hora encendido.

## 9. Fallos que no dicen su causa

| Síntoma | Causa |
|---|---|
| `ukun-api` no arranca y Liquibase falla con `CREATE EXTENSION` | La extensión no está en `azure.extensions`. El mensaje no menciona Azure. La plantilla lo configura; si falla, comprueba que el parámetro se aplicó **antes** de que arrancara la app |
| Las tres apps en `ImagePullBackOff` | El paquete de GHCR sigue privado. Se crean así en su primer push |
| El login se rompe **después** de teclear la contraseña, con `redirect_uri` en `http://` | Alguien puede llegar a `ukun-auth` sin pasar por el ingress. `forward-headers-strategy: native` confía en los rangos privados, que es por donde llega el ingress |
| `invalid_client` al final del login | `UKUN_OAUTH2_GATEWAY_SECRET` no coincide entre los dos lectores |
| `401` intermitentes en `ukun-api` | `UKUN_AUTH_ISSUER` no es exactamente la misma cadena en los dos lados. Se compara literalmente |
| La subida de imágenes falla por permisos sobre un directorio que existe | Las `mountOptions` del volumen de Azure Files. Los contenedores corren como `app` (uid=100, gid=101) y un share SMB aparece con uid/gid 0 |
| La sesión se pierde al navegar | Falta `SERVER_REACTIVE_SESSION_COOKIE_SECURE=true`, o el navegador está descartando la cookie |
| El alta de un cliente no responde, con todo lo demás funcionando | `UKUN_AUTH_BASE_URL`. La saga es el único camino que hace hablar a `ukun-api` con `ukun-auth`, así que es lo único que se rompe |
| El catálogo sale vacío y no hay con qué iniciar sesión | `SPRING_LIQUIBASE_CONTEXTS` no llegó a `demo`, y `application.yaml` trae `prod` por defecto |
| El catálogo se pinta pero cada foto da 404 | No se subieron las imágenes al share, o se subieron a un subdirectorio: van a la raíz, que es lo que se monta en `/opt/resources/uploads` |
| Un contenedor se reinicia solo, sin excepción en el log | Se pasó del límite de memoria y ACA lo mató. Mira el dimensionado de §8 antes de buscar una fuga |
| `429` durante una demo con varias personas | El limitador cuenta por IP y todos salen por la misma NAT. Se sube con `UKUN_GATEWAY_RATELIMIT_API_CAPACITY` (y `UKUN_GATEWAY_RATELIMIT_SIGNUP_CAPACITY`, que son 5 cada 10 minutos y los agotan cinco altas seguidas desde la misma red) |
| El despliegue falla creando el certificado gestionado, con la app ya sana | El CNAME no resuelve todavía, o resuelve a Cloudflare porque el proxy está en **naranja**. La validación es por CNAME y tiene que apuntar al FQDN de la app (§6) |
| `RequireCustomHostnameInEnvironment` al crear el certificado | El hostname no está todavía en el ingress. Lo resuelve el paso «En qué fase está el dominio propio» de `deploy.yml`, que despliega dos veces la primera vez (§6); si sale a mano, es que se ejecutó el Bicep sin pasar por el workflow |
| El dominio propio da un aviso de certificado en el navegador | Se llegó a él antes de que Azure terminara de emitirlo. Tarda unos minutos desde que el binding existe; el FQDN de Azure sirve mientras tanto |
| El dominio dejó de resolver tras un apagado | Se usó `borrar_todo`, que se lleva el entorno y con él el `defaultDomain` al que apunta el CNAME. El valor nuevo está en el resumen del siguiente `deploy.yml` (§5) |
| El `429` llega a todo el mundo a la vez, no a una persona | El proxy de Cloudflare está activado: el limitador ve una sola IP para todos los visitantes porque no lee `X-Forwarded-For` a propósito (§6) |
| El servicio arranca y no dice «Started …» | Es lo esperado: `root: WARN` en producción apaga los INFO de arranque de Spring (`docs/11` §7). Quien responde por él es `/actuator/health` |
