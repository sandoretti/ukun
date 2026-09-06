// Infraestructura del despliegue de ukun en Azure Container Apps (ADR-0023, issue #55).
//
// Es EFÍMERA a propósito: `deploy.yml` la levanta entera y `teardown.yml` se lleva todo lo que
// factura —las tres apps, Postgres y el share—, conservando el Container Apps Environment, su Log
// Analytics y el certificado del dominio propio, que no cuestan nada sin réplicas corriendo. Eso
// último no es una excepción a la limpieza sino lo que sostiene el dominio de `gatewayHost`: el
// `defaultDomain` del entorno es lo que apunta el CNAME de Cloudflare, y sobrevive al ciclo.
// La suscripción es Azure for Students —100 USD para doce meses, con límite de gasto que SUSPENDE los
// recursos en vez de facturarlos—, así que apagar no es higiene sino el mecanismo que impide que el
// crédito se agote solo. Se puede hacer porque ADR-0007 declaró los datos desechables: el changelog
// con `context: demo` vuelve a sembrar las bases en cada arranque y no hay nada que preservar.
//
// Lo que NO hay aquí, y cada ausencia es una decisión escrita en ADR-0023: ni ACR (las imágenes están
// en GHCR, que es gratis), ni VNet con enlace privado (coste fijo mensual), ni Key Vault (los
// secretos de la propia container app cubren el caso), ni Redis (ADR-0024 puso el estado compartido
// en Postgres), ni colector OTLP (sin las variables no se instala exportador y los logs van a
// Log Analytics por stdout).

targetScope = 'resourceGroup'

// ---------------------------------------------------------------------------------------------
// Parámetros
// ---------------------------------------------------------------------------------------------

@description('Región de todos los recursos. Por defecto la del grupo, que es lo que fija deploy.yml.')
param location string = resourceGroup().location

@description('Prefijo de todos los nombres. Cambiarlo levanta un entorno paralelo sin colisiones.')
param appName string = 'ukun'

@description('Etiqueta de las tres imágenes en GHCR: el SHA del commit que publicó el job `images` de ci.yml. Siempre un SHA y nunca `main`, porque un despliegue tiene que poder repetirse dando el mismo resultado.')
param imageTag string

@description('Repositorio de las imágenes. Los tres paquetes son públicos, así que el pull no lleva credencial (ADR-0023: el credencial que no existe no se puede filtrar).')
param registry string = 'ghcr.io/sandoretti'

@description('Usuario administrador de Postgres.')
param postgresUser string = 'ukun'

@secure()
@description('Contraseña del administrador de Postgres.')
param postgresPassword string

@secure()
@description('Secreto del cliente OAuth2 `ukun-gateway`. Lo registra ukun-auth y lo presenta el gateway al canjear el código; un desajuste sale como `invalid_client` al final del login, no al arrancar.')
param oauth2GatewaySecret string

@secure()
@description('Secreto del cliente OAuth2 `ukun-api`, con el que AccountsPort pide su token para /internal/accounts.')
param oauth2ApiSecret string

@description('''Contextos de Liquibase de ukun-api y ukun-auth. Por defecto `demo`, que es lo que
ADR-0023 asume de este entorno: se levanta para una demo y sus datos son desechables (ADR-0007), así
que cada arranque siembra el catálogo de 100 productos, las tiendas, el stock y las catorce cuentas.

LO QUE ESO IMPLICA, PORQUE NO ES INOCUO: las contraseñas de esas cuentas están escritas en
`docs/06-entorno-local-tilt.md` y el ingress de este entorno es público. Cualquiera que dé con el
FQDN entra como administrador. Se acepta porque aquí no hay ni un dato real y el entorno se destruye
al terminar; el día que eso deje de ser cierto, este parámetro pasa a `prod` y deja de sembrar.''')
@allowed(['demo', 'prod'])
param liquibaseContexts string = 'demo'

@secure()
@description('Clave RSA con la que ukun-auth firma los access tokens: PKCS#8, base64 del DER, en una línea (ADR-0024). Sin valor por defecto a propósito — un servicio que arranca con una clave conocida es peor que uno que se niega a arrancar.')
param signingKey string

@description('''Subdominio propio con el que se llega al gateway, sin esquema y sin barra:
`tienda.ejemplo.com`. Vacío —el valor por defecto— deja el entorno exactamente como estaba, servido
solo por el FQDN de Azure, y ése es el modo en el que hay que desplegar la PRIMERA vez.

EL ORDEN IMPORTA Y NO SE PUEDE SALTAR, porque el certificado gestionado se emite validando el DNS y
el DNS no se puede escribir hasta que la app existe:

  1. Desplegar con este parámetro vacío. El resumen del workflow imprime los dos registros que hay
     que crear, ya rellenos.
  2. Crearlos en Cloudflare, **con el proxy desactivado** (nube gris). Con la nube naranja Azure
     resuelve el CNAME, encuentra IPs de Cloudflare en vez de la app, y la emisión falla — igual que
     su renovación automática meses después.
  3. Volver a desplegar con este parámetro puesto. Ahora sí: certificado, binding y las URLs que los
     servicios comparan entre sí.

A partir de ahí es un solo paso para siempre, porque `teardown.yml` conserva el Container Apps
Environment y con él su `defaultDomain`: el CNAME apuntará al mismo sitio en el siguiente ciclo.''')
param gatewayHost string = ''

// ---------------------------------------------------------------------------------------------
// Nombres y URLs
// ---------------------------------------------------------------------------------------------

var apiAppName = '${appName}-api'
var authAppName = '${appName}-auth'
var gatewayAppName = '${appName}-gateway'

// Postgres y las cuentas de almacenamiento tienen nombre único GLOBAL, no por grupo de recursos.
var suffix = uniqueString(resourceGroup().id)

// El FQDN de ACA es determinista —`<app>.<defaultDomain>`— y `defaultDomain` se conoce en cuanto
// existe el entorno. Eso es lo que permite componer aquí las URLs que los tres servicios comparan
// entre sí y evitar el despliegue en dos pasadas que ADR-0023 temía.
//
// LA PARTICIÓN QUE IMPORTA es la misma que comenta compose.yaml, y aquí queda más simple: como
// `ukun-auth` lleva ingress EXTERNO, para él desaparece la distinción entre «URL que sigue el
// navegador» y «URL que resuelve el servicio» — issuer, token endpoint y JWKS son el mismo FQDN. El
// único destino que sigue siendo interno es ukun-api, y su URL NO lleva puerto: el ingress interno
// de ACA escucha en 443 y enruta al targetPort. Escribir aquí `:8081` es el error que no da un
// mensaje claro.
var authUrl = 'https://${authAppName}.${environment_.properties.defaultDomain}'
var apiInternalUrl = 'https://${apiAppName}.internal.${environment_.properties.defaultDomain}'

// El dominio propio afecta a UNA de las tres URLs y a ninguna de las otras dos, y merece decir por
// qué: sólo el gateway es un origen que alguien teclea. `ukun-api` no lo alcanza ningún navegador, y
// `ukun-auth` sí, pero sólo llegando de una redirección — así que se queda con su FQDN de Azure y
// `UKUN_AUTH_ISSUER` no se mueve. Eso es lo que hace que un subdominio baste: el `iss` que ukun-api
// compara LITERALMENTE contra el claim del token es el mismo antes y después de poner el dominio, y
// no hay ninguna ventana en la que un token emitido siga siendo válido con un issuer que cambió.
//
// Lo que sí cambia es `UKUN_GATEWAY_BASE_URL`, de donde salen las redirect URIs del cliente OAuth2.
// No hace falta tocar nada por ello: `RegisteredClientSeeder` reescribe el cliente en cada arranque
// reutilizando su clave primaria, así que las URIs viejas se van solas al desplegar.
//
// EL PRECIO, PARA QUE NO SORPRENDA: la pantalla de contraseña se sirve desde `ukun-auth`, así que
// quien inicia sesión ve durante ese salto el FQDN largo de Azure y no el dominio propio. Es
// estético y se arregla dando un segundo subdominio a auth; cuesta un CNAME y un certificado más.
var hasCustomDomain = !empty(gatewayHost)
var gatewayUrl = hasCustomDomain
  ? 'https://${gatewayHost}'
  : 'https://${gatewayAppName}.${environment_.properties.defaultDomain}'

// `sslmode=require` no es opcional: Flexible Server rechaza las conexiones en claro, y el driver JDBC
// no lo negocia solo. El pool se baja de los 10 de serie porque un B1ms admite del orden de 50
// conexiones para TODO: tres servicios que pueden escalar más Liquibase en cada arranque.
var jdbcOptions = '?sslmode=require'
var hikariPoolSize = '5'

// ---------------------------------------------------------------------------------------------
// Log Analytics — Container Apps lo exige para recoger stdout
// ---------------------------------------------------------------------------------------------

// Es el destino de los logs de docs/11 §7: la aplicación escribe a stdout y nunca a fichero, así que
// recogerlos es cosa del runtime. Con `root: WARN` en el documento `!dev` el volumen es bajo, que es
// lo que mantiene la ingesta dentro de la franja gratuita mensual.
resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: '${appName}-logs'
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    // El mínimo. Un entorno que se destruye después de cada demo no tiene a quién servirle 90 días
    // de retención, y la retención es la partida que factura de este recurso.
    retentionInDays: 30
  }
}

// ---------------------------------------------------------------------------------------------
// Container Apps Environment
// ---------------------------------------------------------------------------------------------

// `environment_` con guion bajo: `environment` es una función de Bicep (la nube actual) y usar el
// nombre a secas es un error de compilación que no dice eso.
resource environment_ 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: '${appName}-env'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Certificado del dominio propio
// ---------------------------------------------------------------------------------------------

// Gestionado por Azure: lo emite y lo RENUEVA solo, sin cron, sin secreto y sin coste. Es hijo del
// entorno y no de la container app, y ahí está lo que hace que todo esto funcione con un CNAME
// puesto a mano una sola vez: `teardown.yml` conserva el entorno, así que el certificado sobrevive
// al ciclo de destruir y levantar igual que el `defaultDomain` al que apunta el CNAME.
//
// `domainControlValidation: 'CNAME'` es lo que exige que el registro exista ANTES de este recurso.
// Si no resuelve —o resuelve a Cloudflare porque el proxy está en naranja—, el despliegue falla
// aquí, con la app ya sana y sirviendo por su FQDN de Azure. Es el fallo que se quiere: ruidoso y
// sin consecuencias.
resource gatewayCertificate 'Microsoft.App/managedEnvironments/managedCertificates@2024-03-01' =
  if (hasCustomDomain) {
    parent: environment_
    // Los puntos no valen en un nombre de recurso hijo. Un dominio distinto da un certificado
    // distinto, que es lo correcto: cambiar `gatewayHost` no debe reutilizar el anterior.
    name: replace(gatewayHost, '.', '-')
    location: location
    properties: {
      subjectName: gatewayHost
      domainControlValidation: 'CNAME'
    }
  }

// ---------------------------------------------------------------------------------------------
// Azure Files — las fotos de producto
// ---------------------------------------------------------------------------------------------

// Sustituye al bind mount de `resources/uploads` de compose.yaml. Sigue siendo un sistema de
// ficheros, no almacenamiento de objetos: el adaptador de `FileStoragePort` pasa a S3 en v1.1
// (issue #57), y hasta entonces el módulo `storage` escribe y sirve desde un directorio.
resource storage 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: 'ukun${suffix}'
  location: location
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'StorageV2'
  properties: {
    minimumTlsVersion: 'TLS1_2'
    // Nadie llega al blob directamente: las imágenes se sirven por `GET /media/products/{key}` desde
    // ukun-api (ADR-0017 §1), que es la ruta que el gateway enruta y la que v1.1 sustituirá.
    allowBlobPublicAccess: false
  }
}

resource fileService 'Microsoft.Storage/storageAccounts/fileServices@2023-05-01' = {
  parent: storage
  name: 'default'
}

resource uploadsShare 'Microsoft.Storage/storageAccounts/fileServices/shares@2023-05-01' = {
  parent: fileService
  name: 'uploads'
  properties: {
    shareQuota: 5
  }
}

resource uploadsStorage 'Microsoft.App/managedEnvironments/storages@2024-03-01' = {
  parent: environment_
  name: 'uploads'
  properties: {
    azureFile: {
      accountName: storage.name
      accountKey: storage.listKeys().keys[0].value
      shareName: uploadsShare.name
      accessMode: 'ReadWrite'
    }
  }
}

// ---------------------------------------------------------------------------------------------
// PostgreSQL Flexible Server
// ---------------------------------------------------------------------------------------------

resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: '${appName}-pg-${suffix}'
  location: location
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    version: '17'
    administratorLogin: postgresUser
    administratorLoginPassword: postgresPassword
    storage: {
      storageSizeGB: 32
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: {
      mode: 'Disabled'
    }
  }
}

// EL RECURSO SIN EL CUAL LIQUIBASE NO ARRANCA, y cuyo error no menciona Azure por ninguna parte.
// `CREATE EXTENSION` falla en Flexible Server si la extensión no figura en esta allowlist del
// servidor: el baseline de ukun_app crea `citext` y releases/v0.3/002-unaccent.yaml crea `unaccent`.
// Es un parámetro dinámico, así que no reinicia el servidor — pero tiene que estar aplicado ANTES de
// que arranque ukun-api, y de eso se encarga el dependsOn de las bases y el de la propia app.
resource azureExtensions 'Microsoft.DBforPostgreSQL/flexibleServers/configurations@2024-08-01' = {
  parent: postgres
  name: 'azure.extensions'
  properties: {
    value: 'CITEXT,UNACCENT'
    source: 'user-override'
  }
}

// TRES bases, no dos: ukun_gateway nace en ADR-0024 (issue #118) y es donde vive la sesión del BFF.
// Sin ella el gateway no arranca, porque su `spring.datasource.url` no tiene valor por defecto.
// Aquí no hay equivalente de scripts/postgres-init-databases.sh — las crea la plantilla.
//
// @batchSize(1): el plano de control de Flexible Server serializa mal las creaciones simultáneas
// sobre el mismo servidor y devuelve conflictos que parecen fallos de la plantilla.
@batchSize(1)
resource databases 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = [
  for db in ['ukun_app', 'ukun_auth', 'ukun_gateway']: {
    parent: postgres
    name: db
    properties: {
      charset: 'UTF8'
      collation: 'en_US.utf8'
    }
    dependsOn: [
      azureExtensions
    ]
  }
]

// La regla de ARRANQUE, y solo eso. Existe por una dependencia circular: la app tiene que existir
// para conocer su IP saliente, y necesita la base alcanzable para que Liquibase arranque. El
// 0.0.0.0 que Flexible Server interpreta como «recursos dentro de Azure» —NO como «todo internet»—
// resuelve el arranque, pero deja entrar a cualquier recurso de Azure de cualquier suscripción, y
// eso incluye a los runners de GitHub Actions, que corren en Azure.
//
// Por eso NO se queda: `deploy.yml` la sustituye, en cuanto las tres apps están en marcha, por una
// regla con su IP saliente real, y borra ésta. Si alguien ve esta regla en un entorno desplegado, es
// que ese paso no llegó a correr.
resource allowAzureServices 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
  parent: postgres
  name: 'AllowAzureServices'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

// ---------------------------------------------------------------------------------------------
// Las tres container apps
// ---------------------------------------------------------------------------------------------

// Sondas comunes. `probes.enabled: true` está en los tres application.yaml, así que /readiness y
// /liveness existen además de /health.
//
// La de arranque es la que de verdad hace falta: una JVM de Spring Boot con Liquibase por delante
// tarda bastante más que el umbral de serie, y sin ella ACA mata el contenedor a mitad de migración
// y lo reintenta en bucle — un fallo que parece de la aplicación y es de la plantilla.
func probesFor(port int) array => [
  {
    type: 'Startup'
    httpGet: {
      path: '/actuator/health/readiness'
      port: port
    }
    initialDelaySeconds: 20
    periodSeconds: 10
    failureThreshold: 30
  }
  {
    type: 'Readiness'
    httpGet: {
      path: '/actuator/health/readiness'
      port: port
    }
    periodSeconds: 10
    failureThreshold: 3
  }
  {
    type: 'Liveness'
    httpGet: {
      path: '/actuator/health/liveness'
      port: port
    }
    periodSeconds: 30
    failureThreshold: 3
  }
]

resource api 'Microsoft.App/containerApps@2024-03-01' = {
  name: apiAppName
  location: location
  properties: {
    environmentId: environment_.id
    configuration: {
      // INGRESS INTERNO: este servicio no lo alcanza ningún navegador. Todo lo suyo entra por el
      // gateway, que es el único punto de entrada desde v0.5 (ADR-0003).
      ingress: {
        external: false
        targetPort: 8081
        transport: 'auto'
        allowInsecure: false
      }
      secrets: [
        {
          name: 'postgres-password'
          value: postgresPassword
        }
        {
          name: 'oauth2-api-secret'
          value: oauth2ApiSecret
        }
      ]
    }
    template: {
      containers: [
        {
          name: apiAppName
          image: '${registry}/${apiAppName}:${imageTag}'
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            // Sin esto la JVM se queda con el 25 % de la memoria del contenedor —el valor por
            // defecto de MaxRAMPercentage—, o sea 256 MiB de heap dentro de 1 GiB, y los otros 750
            // se pagan sin usarse. El síntoma no sería un OOM del contenedor sino GC continuo: la
            // aplicación se arrastra mientras el panel de ACA enseña memoria de sobra, que es de los
            // diagnósticos más difíciles. Se deja en 70 y no en 90: fuera del heap están el
            // metaspace, el code cache y las pilas, y quien mata al contenedor es el kernel.
            {
              name: 'JAVA_TOOL_OPTIONS'
              value: '-XX:MaxRAMPercentage=70'
            }
            {
              name: 'SPRING_DATASOURCE_URL'
              value: 'jdbc:postgresql://${postgres.properties.fullyQualifiedDomainName}:5432/ukun_app${jdbcOptions}'
            }
            // Sin esto el valor por defecto de application.yaml es `prod` y la base queda vacía: el
            // catálogo sale sin un solo producto y no hay ninguna cuenta con la que iniciar sesión.
            {
              name: 'SPRING_LIQUIBASE_CONTEXTS'
              value: liquibaseContexts
            }
            {
              name: 'SPRING_DATASOURCE_USERNAME'
              value: postgresUser
            }
            {
              name: 'SPRING_DATASOURCE_PASSWORD'
              secretRef: 'postgres-password'
            }
            {
              name: 'SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE'
              value: hikariPoolSize
            }
            // El `iss` se compara LITERALMENTE contra el claim del token, así que este valor y el que
            // recibe ukun-auth tienen que ser exactamente la misma cadena. Si se desalinean, el
            // síntoma es un 401 aquí y la causa está en el servicio de al lado.
            {
              name: 'UKUN_AUTH_ISSUER'
              value: authUrl
            }
            {
              name: 'UKUN_AUTH_JWK_SET_URI'
              value: '${authUrl}/oauth2/jwks'
            }
            {
              name: 'UKUN_AUTH_TOKEN_URI'
              value: '${authUrl}/oauth2/token'
            }
            // LA CUARTA URL HACIA ukun-auth, y la que se olvida: `ukun.auth.base-url` es contra
            // quien `HttpAccountsAdapter` llama a /internal/accounts para la saga de signup. No está
            // en compose.yaml —allí el default de AuthProperties, `http://ukun-auth:9000`, ya es el
            // bueno— y por eso no aparece en la lista de variables que hay que traducir; en ACA ese
            // nombre no existe y el alta de un cliente se queda sin responder, con el resto del
            // servicio funcionando. Es exactamente el fallo que ADR-0023 anuncia en su última
            // consecuencia, cometido por omisión en vez de por mezclar las dos familias.
            {
              name: 'UKUN_AUTH_BASE_URL'
              value: authUrl
            }
            {
              name: 'UKUN_OAUTH2_API_SECRET'
              secretRef: 'oauth2-api-secret'
            }
          ]
          // El destino que `ukun.storage.local-root` trae por defecto. El adaptador crea el
          // directorio si no existe, pero no puede arreglar los permisos del montaje: eso lo hacen
          // las mountOptions de abajo.
          volumeMounts: [
            {
              volumeName: 'uploads'
              mountPath: '/opt/resources/uploads'
            }
          ]
          probes: probesFor(8081)
        }
      ]
      // LAS mountOptions NO SON AFINADO. Un share SMB montado por ACA aparece con uid/gid 0, y los
      // tres contenedores corren como el usuario `app` que su Dockerfile crea con `adduser -S`:
      // uid=100, gid=101 (comprobado sobre la imagen, no supuesto). Sin esta línea la subida de
      // imágenes de RF-10 falla por permisos sobre un directorio que sí existe, que es de los
      // síntomas más difíciles de atribuir.
      volumes: [
        {
          name: 'uploads'
          storageType: 'AzureFile'
          storageName: uploadsStorage.name
          mountOptions: 'uid=100,gid=101,dir_mode=0755,file_mode=0644'
        }
      ]
      // Sin `max=1` desde ADR-0024: el arbitraje del stock y de la idempotencia del checkout está en
      // Postgres y no en la JVM, así que este servicio ya aguantaba réplicas antes de v1.0.
      scale: {
        minReplicas: 1
        maxReplicas: 3
      }
    }
  }
  dependsOn: [
    databases
    allowAzureServices
  ]
}

resource auth 'Microsoft.App/containerApps@2024-03-01' = {
  name: authAppName
  location: location
  properties: {
    environmentId: environment_.id
    configuration: {
      // INGRESS EXTERNO, y no es una contradicción con el diagrama de docs/01 §2 que lo dibuja
      // detrás del BFF: el navegador tiene que poder seguir la redirección a /oauth2/authorize y
      // teclear ahí la contraseña (ADR-0023). Lo que sí es interno es su contrato /internal/accounts,
      // y eso lo protege `client_credentials` con el scope `accounts:write`, no la topología de red.
      ingress: {
        external: true
        targetPort: 9000
        transport: 'auto'
        allowInsecure: false
      }
      secrets: [
        {
          name: 'postgres-password'
          value: postgresPassword
        }
        {
          name: 'oauth2-gateway-secret'
          value: oauth2GatewaySecret
        }
        {
          name: 'oauth2-api-secret'
          value: oauth2ApiSecret
        }
        {
          name: 'signing-key'
          value: signingKey
        }
      ]
    }
    template: {
      containers: [
        {
          name: authAppName
          image: '${registry}/${authAppName}:${imageTag}'
          // 0,5 vCPU y 1 GiB, no los 0,25/0,5 que ADR-0023 tabulaba. Medido sobre el stack local con
          // `docker stats`: este servicio ocupa ~412 MiB EN REPOSO, o sea el 80 % de un límite de
          // 512 MiB antes de que nadie inicie sesión. Y en ACA pasarse del límite no degrada nada:
          // el contenedor muere. Las combinaciones de Consumption van en pares (0,25/0,5 · 0,5/1 ·
          // 0,75/1,5 …), así que el siguiente escalón es este.
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            // Sin esto la JVM se queda con el 25 % de la memoria del contenedor —el valor por
            // defecto de MaxRAMPercentage—, o sea 256 MiB de heap dentro de 1 GiB, y los otros 750
            // se pagan sin usarse. El síntoma no sería un OOM del contenedor sino GC continuo: la
            // aplicación se arrastra mientras el panel de ACA enseña memoria de sobra, que es de los
            // diagnósticos más difíciles. Se deja en 70 y no en 90: fuera del heap están el
            // metaspace, el code cache y las pilas, y quien mata al contenedor es el kernel.
            {
              name: 'JAVA_TOOL_OPTIONS'
              value: '-XX:MaxRAMPercentage=70'
            }
            {
              name: 'SPRING_DATASOURCE_URL'
              value: 'jdbc:postgresql://${postgres.properties.fullyQualifiedDomainName}:5432/ukun_auth${jdbcOptions}'
            }
            // Sin esto el valor por defecto de application.yaml es `prod` y la base queda vacía: el
            // catálogo sale sin un solo producto y no hay ninguna cuenta con la que iniciar sesión.
            {
              name: 'SPRING_LIQUIBASE_CONTEXTS'
              value: liquibaseContexts
            }
            {
              name: 'SPRING_DATASOURCE_USERNAME'
              value: postgresUser
            }
            {
              name: 'SPRING_DATASOURCE_PASSWORD'
              secretRef: 'postgres-password'
            }
            {
              name: 'SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE'
              value: hikariPoolSize
            }
            {
              name: 'UKUN_AUTH_ISSUER'
              value: authUrl
            }
            // De aquí salen las redirect URIs que RegisteredClientSeeder registra, y ukun-auth
            // compara `post_logout_redirect_uri` como cadena literal — con su barra final, que la
            // pone el propio application.yaml.
            {
              name: 'UKUN_GATEWAY_BASE_URL'
              value: gatewayUrl
            }
            {
              name: 'UKUN_OAUTH2_GATEWAY_SECRET'
              secretRef: 'oauth2-gateway-secret'
            }
            {
              name: 'UKUN_OAUTH2_API_SECRET'
              secretRef: 'oauth2-api-secret'
            }
            // Desde ADR-0024 sale de configuración y no del arranque, y eso es lo que hace que un
            // despliegue nuevo no cierre la sesión de nadie: el `kid` se deriva de la propia clave
            // (thumbprint RFC 7638), así que dos réplicas publican el mismo JWKS sin coordinarse.
            {
              name: 'UKUN_SIGNING_KEY'
              secretRef: 'signing-key'
            }
          ]
          probes: probesFor(9000)
        }
      ]
      // Sin `max=1` desde ADR-0024: su sesión vive en ukun_auth y el presupuesto del limitador de
      // login también.
      scale: {
        minReplicas: 1
        maxReplicas: 3
      }
    }
  }
  dependsOn: [
    databases
    allowAzureServices
  ]
}

resource gateway 'Microsoft.App/containerApps@2024-03-01' = {
  name: gatewayAppName
  location: location
  properties: {
    environmentId: environment_.id
    configuration: {
      // El único origen que ve el navegador, y el que sirve el SPA estático desde su propia imagen
      // (skill `spring-gateway` §6). Que sea un solo origen es lo que sostiene la cookie de sesión
      // `SameSite` de ADR-0003 — es la razón por la que el SPA no se sirve desde Static Web Apps.
      ingress: {
        external: true
        targetPort: 8080
        transport: 'auto'
        allowInsecure: false
        // El FQDN de Azure NO desaparece al añadir el dominio propio: el ingress responde por los
        // dos. Eso es lo que deja una vía de entrada cuando el DNS o el certificado están a medias,
        // y lo que permite desplegar la primera vez sin dominio ninguno.
        customDomains: hasCustomDomain
          ? [
              {
                name: gatewayHost
                // SNI, que es el único modo que admite un certificado gestionado.
                bindingType: 'SniEnabled'
                certificateId: gatewayCertificate.id
              }
            ]
          : []
      }
      secrets: [
        {
          name: 'postgres-password'
          value: postgresPassword
        }
        {
          name: 'oauth2-gateway-secret'
          value: oauth2GatewaySecret
        }
      ]
    }
    template: {
      containers: [
        {
          name: gatewayAppName
          image: '${registry}/${gatewayAppName}:${imageTag}'
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            // Sin esto la JVM se queda con el 25 % de la memoria del contenedor —el valor por
            // defecto de MaxRAMPercentage—, o sea 256 MiB de heap dentro de 1 GiB, y los otros 750
            // se pagan sin usarse. El síntoma no sería un OOM del contenedor sino GC continuo: la
            // aplicación se arrastra mientras el panel de ACA enseña memoria de sobra, que es de los
            // diagnósticos más difíciles. Se deja en 70 y no en 90: fuera del heap están el
            // metaspace, el code cache y las pilas, y quien mata al contenedor es el kernel.
            {
              name: 'JAVA_TOOL_OPTIONS'
              value: '-XX:MaxRAMPercentage=70'
            }
            {
              name: 'SPRING_DATASOURCE_URL'
              value: 'jdbc:postgresql://${postgres.properties.fullyQualifiedDomainName}:5432/ukun_gateway${jdbcOptions}'
            }
            {
              name: 'SPRING_DATASOURCE_USERNAME'
              value: postgresUser
            }
            {
              name: 'SPRING_DATASOURCE_PASSWORD'
              secretRef: 'postgres-password'
            }
            {
              name: 'SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE'
              value: hikariPoolSize
            }
            // El destino interno, SIN puerto. En compose.yaml esto era `http://ukun-api:8081`, un
            // nombre de la red de Docker; aquí el ingress interno escucha en 443 y enruta al
            // targetPort de la app.
            {
              name: 'UKUN_API_URI'
              value: apiInternalUrl
            }
            {
              name: 'UKUN_AUTH_URI'
              value: authUrl
            }
            {
              name: 'UKUN_AUTH_ISSUER'
              value: authUrl
            }
            {
              name: 'UKUN_GATEWAY_BASE_URL'
              value: gatewayUrl
            }
            {
              name: 'UKUN_AUTH_TOKEN_URI'
              value: '${authUrl}/oauth2/token'
            }
            {
              name: 'UKUN_AUTH_JWK_SET_URI'
              value: '${authUrl}/oauth2/jwks'
            }
            {
              name: 'UKUN_OAUTH2_GATEWAY_SECRET'
              secretRef: 'oauth2-gateway-secret'
            }
            // application.yaml NO fija `Secure` a propósito, porque `tilt up` sirve por HTTP y una
            // cookie `Secure` incondicional dejaría la sesión sin funcionar en el único entorno que
            // se puede probar. Detrás de un ingress con TLS lo tiene que poner el despliegue, y no
            // lo pone nadie más que esta línea.
            {
              name: 'SERVER_REACTIVE_SESSION_COOKIE_SECURE'
              value: 'true'
            }
          ]
          probes: probesFor(8080)
        }
      ]
      // LA ÚNICA APP QUE CONSERVA max=1, Y EL MOTIVO VA AQUÍ para que nadie lo suba «porque los
      // otros dos ya se puede»: su limitador de tasa de /api/** guarda los buckets en memoria a
      // propósito (ADR-0024, tabla de estado compartido). Con dos réplicas el presupuesto real sería
      // el doble del configurado.
      //
      // Ojo a lo que esto NO impide: al actualizar, ACA arranca la revisión nueva antes de retirar
      // la vieja, así que hay un momento con dos. No rompe el login porque la sesión del BFF vive en
      // ukun_gateway desde ADR-0024; lo único que se duplica un instante es ese presupuesto.
      scale: {
        minReplicas: 1
        maxReplicas: 1
      }
    }
  }
  dependsOn: [
    databases
    allowAzureServices
  ]
}

// ---------------------------------------------------------------------------------------------
// Salidas
// ---------------------------------------------------------------------------------------------

@description('La URL que se abre en el navegador: el dominio propio si `gatewayHost` viene puesto, y el FQDN de Azure si no.')
output gatewayUrl string = gatewayUrl

@description('''Los dos valores con los que se escriben los registros de Cloudflare, y la razón de
que sean una salida en vez de algo que buscar en el portal: hasta que la app no existe no se conocen,
y son exactamente lo que hay que copiar entre la primera pasada y la segunda.

`gatewayFqdn` es el destino del CNAME. `customDomainVerificationId` es el contenido del TXT en
`asuid.<subdominio>`, y es estable por suscripción: se escribe una vez y no cambia aunque se borre y
se vuelva a crear todo.''')
output gatewayFqdn string = gateway.properties.configuration.ingress.fqdn

@description('Contenido del registro TXT `asuid.<subdominio>` que prueba a Azure que el dominio es tuyo.')
output customDomainVerificationId string = gateway.properties.customDomainVerificationId

@description('El origen del Authorization Server, que es también el `iss` de todos los tokens.')
output authUrl string = authUrl

@description('FQDN del servidor de Postgres, para conectarse con psql desde una IP autorizada.')
output postgresFqdn string = postgres.properties.fullyQualifiedDomainName

@description('''IP estática de ENTRADA del entorno. NO sirve para el firewall de Postgres, y decirlo
importa porque es el error natural: comprobado sobre un entorno real, la de entrada y la saliente son
direcciones distintas (51.11.234.46 frente a 20.74.0.153 en el primer despliegue). Lo que Postgres ve
es `properties.outboundIpAddresses` de cada container app, que `deploy.yml` lee después de
desplegar.''')
output environmentStaticIp string = environment_.properties.staticIp

@description('Nombre del servidor de Postgres, que es lo que piden los comandos `az postgres flexible-server` — distinto de su FQDN.')
output postgresServerName string = postgres.name

@description('Cuenta de almacenamiento del share de uploads.')
output storageAccountName string = storage.name
