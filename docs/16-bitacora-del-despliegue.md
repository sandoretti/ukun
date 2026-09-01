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

Se cierra el punto del checklist de la issue #55 que pedía «el coste verificado en la calculadora». No se usó la calculadora sino la **API de precios de Azure** (`prices.azure.com/api/retail/prices`), que es pública, no necesita autenticación y devuelve el precio por medidor y región — de modo que la comprobación es reproducible con un comando en vez de con capturas de una web. Las cifras y los escenarios están en `15-despliegue.md` §7.

El resultado en una línea: **el uso puntual sale por céntimos** —0,07 USD una demo de tres horas, 0,30 USD un mes de cuatro demos— y **24/7 sale por 46,15 USD/mes**, que agota el crédito de estudiante en 2,2 meses. Los dos números que explican esa diferencia:

- La **franja gratuita mensual de Container Apps** (180 000 vCPU-s y 360 000 GiB-s) se agota, con el reparto de recursos de esta plantilla, exactamente a las **40 horas encendido al mes**. Por debajo de eso el cómputo de las tres apps es gratis y solo corre el contador de Postgres, a 0,025 USD/h entre cómputo y disco.
- Existe un medidor **`Environment Management Hour` de 0,125 USD/h** —91 USD/mes— que **no** aplica aquí porque es de los entornos con *workload profiles*, y `main.bicep` no declara ninguno. Queda anotado porque añadir un `workloadProfiles` parecería una mejora y sería el cambio más caro que se le puede hacer a esta plantilla.

Nada de esto sustituye a mirar el consumo real: el primer despliegue es también la primera medida de verdad, y si difiere, la cifra que manda es la del portal y se corrige aquí.
