# Despliegue en Azure Container Apps

> **Fuente de verdad de:** cómo se levanta, se actualiza y se destruye el entorno desplegado, qué hay que preparar una vez a mano, y de dónde sale cada variable en producción.
> **Léelo si:** vas a desplegar, a cambiar la plantilla de Bicep, o estás mirando por qué el entorno desplegado se comporta distinto que `tilt up`.
> **No decide:** por qué el destino es Container Apps y no otra cosa (ADR-0023), ni el entorno local (`06-entorno-local-tilt.md`), ni el reparto de tests en CI (`13-testing.md` §3).
> **Idioma:** los nombres de recurso, variable y base de datos, en inglés (ADR-0018).

El entorno es **efímero a propósito**. La suscripción es Azure for Students: 100 USD para doce meses, sin tarjeta y con límite de gasto activado, de modo que agotar el crédito no genera una factura — **suspende los recursos**. Apagar no es higiene, es el mecanismo que impide que el crédito se acabe solo. Se puede destruir sin pensarlo porque ADR-0007 declaró los datos desechables y el changelog con `context: demo` vuelve a sembrar las bases en cada arranque.

Todo vive en tres sitios: `deploy/azure/main.bicep` (los recursos), `.github/workflows/deploy.yml` (levantar y actualizar) y `.github/workflows/teardown.yml` (destruir).

## 1. Qué se despliega

Tres contenedores Java y una base de datos — **no cuatro contenedores**: el gateway sirve el SPA estático desde su propia imagen (skill `spring-gateway` §6), y `ukun-web` solo existe como contenedor propio en desarrollo, donde Vite sirve su dev server con HMR.

| Recurso | Perfil | Notas |
|---|---|---|
| Container Apps Environment | Consumption | Con su Log Analytics, que ACA exige |
| `ukun-gateway` | 0,5 vCPU / 1 GiB, `min=1`, **`max=1`** | Ingress **externo**. El único origen que ve el navegador |
| `ukun-auth` | 0,25 vCPU / 0,5 GiB, `min=1`, `max=3` | Ingress **externo** también — el navegador teclea ahí la contraseña |
| `ukun-api` | 0,5 vCPU / 1 GiB, `min=1`, `max=3` | Ingress **interno**. Solo lo alcanza el gateway |
| PostgreSQL Flexible Server | Burstable B1ms, 32 GB, sin HA | Un servidor, **tres** bases: `ukun_app`, `ukun_auth`, `ukun_gateway` |
| Azure Files | Standard, 5 GB | Montado en `ukun-api`, sustituye el bind mount de `resources/uploads` |

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

# Sin esto, el primer despliegue falla al crear el recurso de un proveedor no registrado.
for p in Microsoft.App Microsoft.OperationalInsights Microsoft.DBforPostgreSQL Microsoft.Storage; do
  az provider register --namespace "$p"
done
```

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

Los cuatro valores sensibles, como secretos. Son los mismos que `.env.example` explica para el entorno local, generados igual:

```bash
gh secret set POSTGRES_PASSWORD            --body "$(openssl rand -hex 32)"
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
| `UKUN_GATEWAY_BASE_URL` | auth, gateway | `https://ukun-gateway.<defaultDomain>` | `http://localhost:8080` |
| `UKUN_AUTH_TOKEN_URI` / `_JWK_SET_URI` | api, gateway | el mismo FQDN de auth | nombre de contenedor `ukun-auth:9000` |
| `UKUN_API_URI` | gateway | `https://ukun-api.internal.<defaultDomain>`, **sin puerto** | `http://ukun-api:8081` |
| `SPRING_DATASOURCE_URL` | los tres | FQDN de Postgres + **`?sslmode=require`** | `db:5432` |
| `SERVER_REACTIVE_SESSION_COOKIE_SECURE` | gateway | `true` | sin definir |
| `UKUN_SIGNING_KEY` | auth | secreto de la container app | `.env` |
| `MANAGEMENT_*_OTLP_*` | los tres | **sin definir** | las pone `compose.dev.yaml` |

Cuatro cosas de esa tabla que no son evidentes:

- **Ninguna URL se escribe a mano.** El FQDN de ACA es `<app>.<defaultDomain>` y `defaultDomain` se conoce en cuanto existe el entorno, así que la plantilla las compone sola. Escribirlas obligaría a desplegar en dos pasadas y a mantener sincronizado a mano el `iss`, que `ukun-api` compara **literalmente** contra el claim del token.
- **`UKUN_API_URI` no lleva puerto.** El ingress interno de ACA escucha en 443 y enruta al `targetPort` de la app. Poner `:8081` es el error clásico de traducir el nombre de la red de Docker.
- **`?sslmode=require` no es opcional.** Flexible Server rechaza las conexiones en claro y el driver JDBC no lo negocia solo. Va con un `maximum-pool-size` de 5, porque un B1ms admite del orden de 50 conexiones para todo: tres servicios que pueden escalar, más Liquibase en cada arranque.
- **Las variables de OTLP se quedan sin definir, y esa ausencia es la configuración** (`docs/11` §5): sin ellas no se instala exportador y los tres arrancan igual. Los logs van a stdout y los recoge Log Analytics, que es lo que ACA hace de serie. No hay colector desplegado.

## 5. Destruir

```
Actions → Teardown → Run workflow → confirm: <el nombre del grupo de recursos>
```

Hay que escribir el nombre del grupo a mano: es lo único que separa este botón de un borrado por accidente. Borra el grupo **entero** —las tres apps, el entorno, Log Analytics, el share de uploads y el servidor de Postgres con sus tres bases—, y lo hace con `--no-wait` porque Azure tarda del orden de diez minutos.

**Compruébalo en el portal antes de darlo por hecho.** El workflow no espera a verlo terminar, y un borrado que falla a medias deja recursos facturando, que es justo lo que este paso existe para evitar.

## 6. El firewall de Postgres, y por qué queda abierto

El despliegue deja una regla `AllowAzureServices` (el `0.0.0.0` que Flexible Server interpreta como «recursos dentro de Azure», **no** como «todo internet»). No es la intención final de ADR-0023, que pide restringir a la IP saliente del Container Apps Environment; hay una **dependencia circular** que lo impide en la primera pasada: la app tiene que existir para conocer su IP saliente, y necesita la base alcanzable para que Liquibase arranque.

Para estrecharla después, con la IP que el despliegue devuelve como salida:

```bash
az postgres flexible-server firewall-rule create \
  --resource-group ukun-demo --name <servidor> \
  --rule-name aca-outbound --start-ip-address <IP> --end-ip-address <IP>

az postgres flexible-server firewall-rule delete \
  --resource-group ukun-demo --name <servidor> --rule-name AllowAzureServices --yes
```

**Verifica antes que esa IP es la que Postgres ve.** `environmentStaticIp` es la IP de *entrada* del entorno; que coincida con la saliente hay que comprobarlo contra el entorno de verdad y no darlo por bueno leyendo documentación. Lo que resulte se escribe aquí.

Lo que esto **no** es: acceso público con contraseña. Lo que sí es: cualquier recurso de Azure, de cualquier suscripción, puede intentar conectarse. Se acepta en un entorno efímero de estudio con datos desechables (ADR-0007), y es lo primero que se revisaría si dejara de serlo.

## 7. Coste

**Sin verificar todavía.** ADR-0023 da órdenes de magnitud de agosto de 2026 y dice expresamente que hay que comprobarlas en la calculadora de Azure antes de desplegar; los precios de ACA y de los tier Burstable se mueven. Las partidas a mirar, en este orden:

1. **Postgres B1ms**, que es la dominante y la única que corre encendida sin tráfico.
2. **Container Apps**: con las tres a réplica mínima y mayormente ociosas, el consumo se apoya en la tarifa reducida de inactividad y en la franja gratuita mensual de vCPU-segundo y GiB-segundo.
3. **Log Analytics**, que factura por GB ingerido con una franja gratuita mensual. Con `root: WARN` en el documento `!dev` (`docs/11` §7) el volumen es bajo.
4. Azure Files y el tráfico de salida, que a esta escala son ruido.

Encendido de forma continua, el conjunto consumiría el crédito en unos meses; encendido solo para las demos, no debería costar nada apreciable. Esa diferencia es toda la justificación del entorno efímero. **Cuando se verifique, las cifras van aquí con su fecha.**

## 8. Fallos que no dicen su causa

| Síntoma | Causa |
|---|---|
| `ukun-api` no arranca y Liquibase falla con `CREATE EXTENSION` | La extensión no está en `azure.extensions`. El mensaje no menciona Azure. La plantilla lo configura; si falla, comprueba que el parámetro se aplicó **antes** de que arrancara la app |
| Las tres apps en `ImagePullBackOff` | El paquete de GHCR sigue privado. Se crean así en su primer push |
| El login se rompe **después** de teclear la contraseña, con `redirect_uri` en `http://` | Alguien puede llegar a `ukun-auth` sin pasar por el ingress. `forward-headers-strategy: native` confía en los rangos privados, que es por donde llega el ingress |
| `invalid_client` al final del login | `UKUN_OAUTH2_GATEWAY_SECRET` no coincide entre los dos lectores |
| `401` intermitentes en `ukun-api` | `UKUN_AUTH_ISSUER` no es exactamente la misma cadena en los dos lados. Se compara literalmente |
| La subida de imágenes falla por permisos sobre un directorio que existe | Las `mountOptions` del volumen de Azure Files. Los contenedores corren como `app` (uid=100, gid=101) y un share SMB aparece con uid/gid 0 |
| La sesión se pierde al navegar | Falta `SERVER_REACTIVE_SESSION_COOKIE_SECURE=true`, o el navegador está descartando la cookie |
| El servicio arranca y no dice «Started …» | Es lo esperado: `root: WARN` en producción apaga los INFO de arranque de Spring (`docs/11` §7). Quien responde por él es `/actuator/health` |
