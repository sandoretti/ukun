# ADR-0023: Despliegue en Azure Container Apps, con infraestructura efímera

## Estado
Aceptado

## Contexto
El sistema legacy se desplegaba en Azure con `docker-compose-azure.yml`: imágenes construidas y subidas a mano a un `sandoretti/*` de Docker Hub, nginx delante y una base de datos apuntada por variable de entorno. Al planificar la refactorización se declaró **Azure fuera de alcance** (`docs/04-roadmap.md`) — pero lo que sobraba era *ese* despliegue, no el proveedor, y nunca se eligió sustituto. La consecuencia es que dos documentos se quedaron afirmando que el destino estaba sin decidir: la issue #55 (*«el destino no está fijado — merece un ADR nuevo cuando se elija»*) y el Contexto de ADR-0005.

Este ADR cierra esa decisión, y lo hace bajo una restricción que no existía cuando se escribió el roadmap: **la suscripción es Azure for Students**. Son 100 USD para doce meses, sin tarjeta asociada y con el límite de gasto activado, así que agotar el crédito no genera una factura — **suspende los recursos**. El presupuesto no es una preferencia de diseño que se pueda incumplir con un aviso en la factura: es un interruptor que apaga el entorno a mitad de curso.

Lo que hay que desplegar son **tres contenedores Java y una base de datos**, no cuatro contenedores: el gateway sirve el SPA estático en producción (skill `spring-gateway` §6), y `ukun-web` solo existe como contenedor propio en desarrollo, donde Vite sirve su dev server con HMR (`docs/06-entorno-local-tilt.md`).

## Decisión
**Azure Container Apps** en perfil Consumption para los tres servicios, **Azure Database for PostgreSQL Flexible Server** para los datos, e **infraestructura efímera** descrita en Bicep y gobernada desde GitHub Actions.

### Topología

| Recurso | SKU / perfil | Notas |
|---|---|---|
| Container Apps Environment | Consumption | Uno solo, con su Log Analytics workspace (que ACA exige) |
| `ukun-gateway` | 0,5 vCPU / 1 GiB, `min=max=1` | Ingress **externo**. Es el único origen que ve el navegador |
| `ukun-auth` | 0,25 vCPU / 0,5 GiB, `min=max=1` | Ingress **externo** también |
| `ukun-api` | 0,5 vCPU / 1 GiB, `min=max=1` | Ingress **interno** |
| PostgreSQL Flexible Server | Burstable **B1ms**, 32 GB, sin HA, backup 7 días | Un servidor, las dos bases: `ukun_app` y `ukun_auth` |
| Azure Files share | Standard | Montado en `ukun-api`, sustituye el bind mount de `resources/uploads` |

Tres puntos de esa tabla no son evidentes y son justamente los que se decidieron:

- **`ukun-auth` necesita ingress externo**, igual que el gateway. No es un servicio interno aunque el diagrama de `docs/01-arquitectura.md` §2 lo dibuje detrás del BFF: el navegador tiene que poder seguir la redirección a `/oauth2/authorize` y teclear ahí la contraseña. Lo que sí es interno es su contrato `/internal/accounts` (`docs/08-identidad-y-seguridad.md` §3), y eso lo protege `client_credentials` con el scope `accounts:write`, no la topología de red.
- **Una sola réplica en todos, y `max=1` explícito**, no por coste sino porque la sesión vive en memoria en el gateway *y* en `ukun-auth` (ADR-0003, skill `spring-gateway` §5, issue #118). Un `max=2` no daría un error: daría logins que fallan de forma intermitente cuando el callback del código cae en la réplica que no inició el flujo. Que el límite esté escrito en la plantilla de Bicep es la mitad de esta decisión.
- **El acceso a Postgres es público con regla de firewall**, restringido a la IP saliente estática del Container Apps Environment, no un Private Endpoint sobre VNet. La red privada obliga a integrar el entorno en una VNet propia y a pagar el enlace privado, que es coste fijo mensual sobre un crédito de 100 USD. Se acepta conscientemente: es la decisión que se revisaría primero si esto dejara de ser un proyecto de estudio.

### El entorno es efímero a propósito

`deploy.yml` levanta el grupo de recursos completo desde Bicep; `teardown.yml` lo borra. Se paga por los días que dura una demo, no por meses de un entorno que nadie mira. Con el límite de gasto de la suscripción, apagar deja de ser higiene y pasa a ser el mecanismo que impide que el crédito se agote solo.

Esto se puede hacer porque **ADR-0007** ya declaró los datos desechables: no hay nada que preservar entre despliegues, y el changelog con `context: demo` (`docs/10-modelo-de-datos.md` §8, skill `liquibase`) vuelve a sembrar la base en cada arranque. La misma premisa que hizo innecesaria la migración del legacy hace barato destruir el entorno.

El corolario incómodo hay que decirlo: **la URL cambia en cada despliegue**, porque el FQDN que asigna ACA depende del nombre del entorno. Eso obliga a que `UKUN_AUTH_ISSUER` y `UKUN_GATEWAY_BASE_URL` salgan de la salida del despliegue de Bicep y se inyecten, nunca de un valor escrito a mano.

### El registro de imágenes es GHCR, no ACR

Las imágenes se construyen en GitHub Actions y se publican en **GitHub Container Registry**, etiquetadas por SHA del commit. ACR Basic cuesta del orden de 5 USD/mes, que sobre 100 USD de crédito anual es más del 60 % del presupuesto gastado en alojar imágenes que GitHub aloja gratis junto al código que las produce.

### Autenticación del pipeline: OIDC federado

GitHub Actions se autentica contra Azure con **credenciales federadas de OpenID Connect** sobre una managed identity de usuario, no con un secreto de service principal guardado en el repo. No hay ninguna credencial de Azure de larga duración en GitHub, y es la misma clase de decisión que ADR-0003 toma para el navegador: el credencial que no existe no se puede filtrar.

### Lo que este ADR no resucita

Volver a Azure **no** es volver al despliegue de Azure. `docker-compose-azure.yml`, `docker-compose-sandoretti.yml`, `docker-compose.yml` y `nginx/` se siguen borrando en v0.7 tal y como estaba planificado, y ninguno es punto de partida de nada: el reverse proxy lo sustituyó el gateway en v0.5, y las imágenes de Docker Hub las sustituye GHCR aquí. Si alguien se encuentra editando uno de esos ficheros para desplegar, ha copiado de una fuente vieja.

## Consecuencias
- Aparece `deploy/azure/` con la plantilla de Bicep, y `.github/workflows/` gana `deploy.yml` y `teardown.yml` además del `ci.yml` de la issue #51. El runbook (levantar, destruir, qué variable sale de dónde) será `docs/15-despliegue.md`.
- **El entorno local no cambia.** ADR-0005 eligió Tilt sobre docker-compose en vez de sobre Kubernetes local, y una de sus dudas era qué pediría el destino final: el destino elegido no es Kubernetes, así que no hay manifiestos que traducir ni presión para acercar el sustrato local al remoto.
- Requisito nuevo de la máquina de quien despliega a mano: `az` CLI y las extensiones de Container Apps. El camino normal, aun así, es el workflow.
- **Los nombres internos de `compose.yaml` no valen tal cual.** `UKUN_API_URI=http://ukun-api:8081` resuelve dentro de la red de Docker; dentro de un Container Apps Environment el destino interno es el FQDN que ACA asigna a la app. Es la traducción que hay que hacer con cuidado, porque el comentario de `compose.yaml` que distingue las variables «que sigue el navegador» de las «que resuelve el servicio» sigue mandando: mezclarlas es el fallo que no da un error claro.

Y seis cosas del código de hoy que el despliegue destapa. Ninguna se arregla en este ADR: se documentan aquí y se ejecutan en la issue #55.

1. **`CREATE EXTENSION` falla si la extensión no está en la allowlist del servidor.** El baseline crea `citext` y `releases/v0.3/002-unaccent.yaml` crea `unaccent`; en Flexible Server las dos exigen figurar en el parámetro de servidor `azure.extensions`. El síntoma es que Liquibase no arranca, y el mensaje no menciona Azure por ninguna parte.
2. **La clave de firma de `ukun-auth` se genera al arrancar.** `JwkSourceConfig` hace `KeyPairGenerator.getInstance("RSA")` con un `keyID` aleatorio, así que cada revisión nueva de la container app invalida todos los tokens emitidos. Pasa a ser un PEM en un secreto de la aplicación. Es el punto 1 de la issue #118, y aquí deja de ser deuda teórica: cada despliegue la ejerce.
3. **La sesión en memoria fija el número de réplicas en uno**, en gateway y en auth. Escrito en la plantilla, no confiado a la configuración por defecto.
4. **`forward-headers-strategy` no está configurado en ningún servicio.** Detrás del ingress de ACA, que termina el TLS, Spring construiría el `redirect_uri` del login sobre `http://` y el flujo OAuth2 se rompe. Va junto con el punto 5 de la issue #118: el limitador de intentos de login usa `getRemoteAddr()`, que detrás de un proxy es la misma IP para todo el mundo, y `LoginEventListener` ya razona por qué hoy no se lee `X-Forwarded-For` — con un balanceador delante, esa decisión correcta pasa a ser un requisito de configuración.
5. **`UKUN_AUTH_ISSUER` y `UKUN_GATEWAY_BASE_URL` dejan de ser `localhost`.** El `iss` se compara literalmente contra el claim del token en `ukun-api`, así que los dos lados tienen que recibir exactamente la misma cadena `https://…`.
6. **No hay imagen de producción del SPA.** `apps/ukun-web` solo tiene `Dockerfile.dev`, que arranca el dev server de Vite. Para que el gateway sirva el estático que la skill `spring-gateway` §6 promete, hace falta el build de producción y una etapa que lo copie dentro de la imagen del gateway.

## Alternativas descartadas
- **AKS.** El control plane en tier Free no cuesta nada, pero los nodos sí, y por debajo de dos `B2s` no hay clúster que sostenga tres servicios y su Ingress. A eso se suma mantener Ingress controller y cert-manager para obtener el HTTPS que ACA da hecho. Es la opción que más se aprende y la que antes agota los 100 USD; no se descarta por mala, se descarta por cara.
- **App Service para contenedores.** Tres apps en un plan B1 comparten 1,75 GB de RAM entre tres JVM. Subir a un plan que respire lo pone al nivel de coste de AKS sin darle sus ventajas.
- **Una VM única con `docker compose`.** Es lo que más RAM da por euro y lo más parecido al `compose.yaml` que ya existe. Se descarta por lo que arrastra: parcheado del sistema operativo, certificados TLS a mano, y un CI/CD que termina siendo `ssh` + `docker pull`, sin revisiones ni rollback.
- **Azure Static Web Apps para el SPA** (que además tiene plan gratuito). Serviría el estático desde otro origen, y con ello reintroduce CORS y rompe la premisa de ADR-0003: el navegador ve un solo origen y una cookie de sesión `SameSite`. La cookie del BFF es la razón por la que el gateway sirve el estático, no una comodidad de empaquetado.
- **Azure Container Registry Basic.** Coste fijo mensual por alojar lo que GHCR aloja gratis. Se reconsideraría si hiciera falta pull con managed identity sin credenciales, que hoy no hace falta.
- **Postgres como una container app más**, con la imagen `postgres:17` y un volumen de Azure Files. Es lo más barato y ADR-0007 lo haría defendible. Se descarta porque el servicio gestionado es exactamente donde muerden los detalles que hay que probar —`azure.extensions`, los límites de conexiones del tier Burstable, el `SELECT … FOR UPDATE` del checkout contra un servidor que no controlas—, y un Postgres en contenedor los escondería hasta el día en que dejara de ser un proyecto de estudio.
- **Azure Key Vault** para los secretos. Los secretos de la propia container app cubren el caso, se inyectan como variables de entorno y no añaden un recurso más al presupuesto. Key Vault entra cuando haya rotación real que gestionar.
- **Dejar el entorno siempre encendido.** Es la opción cómoda y la que hace que el crédito se agote sin que nadie lo note, porque el límite de gasto suspende los recursos sin avisar antes.

## Notas de coste
Las cifras de esta sección son órdenes de magnitud de agosto de 2026 y **hay que verificarlas en la calculadora de Azure antes de desplegar**; los precios de ACA y de los tier Burstable se mueven. Con las tres apps a réplica mínima y mayormente ociosas, ACA se apoya en la tarifa reducida de inactividad y en la franja gratuita mensual de vCPU-segundo y GiB-segundo, y la partida dominante pasa a ser el B1ms de Postgres. Encendido de forma continua, el conjunto consumiría el crédito de estudiante en un puñado de meses; encendido solo para las demos, no debería costar nada apreciable. Esa diferencia es toda la justificación del entorno efímero.
