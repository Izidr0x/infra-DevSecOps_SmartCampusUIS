# Jenkins - Infraestructura CI/CD

Este directorio contiene la configuración utilizada para desplegar Jenkins como orquestador principal del flujo CI/CD del proyecto **Smart Campus UIS**.

Jenkins se ejecuta como servicio contenerizado y se publica detrás de un proxy reverso Nginx con HTTPS interno. Desde este componente se coordinan las etapas de compilación, pruebas, análisis de calidad, análisis de seguridad, construcción de imágenes y despliegue en el ambiente de pruebas.

## Índice

- [Rol dentro del proyecto](#rol-dentro-del-proyecto)
- [Arquitectura del componente](#arquitectura-del-componente)
- [Estructura del directorio](#estructura-del-directorio)
- [Archivos principales](#archivos-principales)
- [Variables de entorno](#variables-de-entorno)
- [Despliegue](#despliegue)
- [Acceso](#acceso)
- [Configuración como código](#configuración-como-código)
- [Plugins utilizados](#plugins-utilizados)
- [Docker desde Jenkins](#docker-desde-jenkins)
- [Integración con SonarQube](#integración-con-sonarqube)
- [Operación básica](#operación-básica)
- [Problemas comunes](#problemas-comunes)
- [Consideraciones de seguridad](#consideraciones-de-seguridad)
- [Relación con los pipelines](#relación-con-los-pipelines)

## Rol dentro del proyecto

Jenkins cumple la función de coordinar las etapas de integración continua, análisis de seguridad, construcción de imágenes y despliegue de servicios.

Desde este componente se ejecutan los pipelines definidos para:

- compilar los microservicios;
- ejecutar pruebas unitarias;
- enviar análisis a SonarQube;
- validar Quality Gate;
- realizar verificaciones con OWASP Dependency-Check;
- construir imágenes Docker;
- analizarlas con Trivy;
- publicarlas en Docker Hub;
- desplegar la aplicación en el ambiente de pruebas.

En esta implementación, Jenkins quedó como el único servicio publicado detrás de Nginx. SonarQube se desplegó como servicio independiente y Trivy no se ejecutó como servicio permanente, sino de forma temporal durante los pipelines.

## Arquitectura del componente

```text
Cliente/Navegador
      │
      ▼
Nginx HTTPS
      │
      ▼
Jenkins Controller
      │
      ├── Git
      ├── Maven / JDK
      ├── SonarQube
      ├── OWASP Dependency-Check
      ├── Docker
      └── Docker Compose
```

Jenkins y Nginx comparten la red Docker `jenkins_net`, que en el host suele verse como:

```text
jenkins_jenkins_net
```

Esa red también es usada por SonarQube para que Jenkins pueda enviar análisis al servidor SonarQube mediante una URL interna.

## Estructura del directorio

```text
jenkins/
├── README.md
├── compose.yml
├── Dockerfile
├── plugins.txt
├── .env.example
├── casc/
│   └── jenkins.yaml
├── nginx/
│   └── default.conf
└── certs/
    ├── README.md
    ├── gen_cert.sh
    └── openssl-ip.cnf.example
```

## Archivos principales

| Archivo | Descripción |
|---|---|
| `compose.yml` | Define los servicios `jenkins` y `nginx`, volúmenes, red interna, variables de entorno y restricciones básicas. |
| `Dockerfile` | Construye la imagen personalizada de Jenkins con paquetes base, plugins y configuración inicial. |
| `plugins.txt` | Lista los plugins requeridos por Jenkins para ejecutar los pipelines. |
| `.env.example` | Plantilla de variables de entorno necesarias para levantar Jenkins sin exponer credenciales reales. |
| `casc/jenkins.yaml` | Configuración inicial de Jenkins mediante Jenkins Configuration as Code. |
| `nginx/default.conf` | Configuración del proxy reverso Nginx para publicar Jenkins por HTTPS interno. |
| `certs/` | Archivos auxiliares para generar certificados autofirmados usados por Nginx. |

## Variables de entorno

Antes de levantar Jenkins se debe crear un archivo `.env` a partir de `.env.example`:

```bash
cp .env.example .env
```

Ejemplo de variables requeridas:

```env
JENKINS_URL=https://192.168.x.x/
JENKINS_ADMIN_ID=admin
JENKINS_ADMIN_PASSWORD=change_me
TZ=America/Bogota
```

El archivo `.env` no debe subirse al repositorio porque puede contener credenciales o datos propios del ambiente de pruebas.

## Despliegue

Desde el directorio `jenkins/`:

```bash
docker compose up -d --build
```

Para validar el estado de los servicios:

```bash
docker compose ps
```

Para revisar logs de Jenkins:

```bash
docker compose logs -f jenkins
```

Para revisar logs del proxy Nginx:

```bash
docker compose logs -f nginx
```

Para verificar la red creada:

```bash
docker network ls
```

Si el proyecto Compose se ejecuta desde el directorio `jenkins`, la red normalmente queda con el nombre:

```text
jenkins_jenkins_net
```

## Acceso

Jenkins queda disponible a través del proxy Nginx usando HTTPS interno:

```text
https://IP_O_DNS_JENKINS/
```

Validación por consola:

```bash
curl -k https://IP_O_DNS_JENKINS/login
```

Cuando se usan certificados autofirmados, el navegador puede mostrar una advertencia de confianza. Esto es esperado en el ambiente de pruebas, a menos que el certificado sea instalado como confiable en los equipos cliente.

## Configuración como código

El archivo `casc/jenkins.yaml` define una configuración inicial para Jenkins, incluyendo:

- mensaje del sistema;
- número de ejecutores;
- desactivación del puerto de agentes;
- usuario administrador local;
- estrategia básica de autorización;
- URL base de Jenkins.

Esta configuración permite que el despliegue inicial sea más reproducible y reduce la configuración manual después de levantar el servicio.

## Plugins utilizados

Los plugins se instalan durante la construcción de la imagen personalizada de Jenkins a partir del archivo `plugins.txt`.

Plugins principales:

| Plugin | Uso |
|---|---|
| Pipeline | Crear y ejecutar pipelines. |
| Git | Obtener código fuente desde GitHub. |
| JUnit | Publicar resultados de pruebas unitarias. |
| Copy Artifact | Copiar artefactos entre jobs. |
| SonarQube Scanner for Jenkins | Integrar análisis de SonarQube. |
| OWASP Dependency-Check | Ejecutar análisis SCA. |
| Credentials Binding | Inyectar credenciales de forma segura. |
| Configuration as Code | Soportar configuración declarativa inicial. |
| Matrix Authorization | Definir permisos básicos de acceso. |
| Pipeline Stage View | Visualizar etapas de ejecución. |

## Docker desde Jenkins

El montaje permite que Jenkins interactúe con Docker del host para:

- construir imágenes;
- ejecutar Trivy como contenedor temporal;
- publicar imágenes;
- ejecutar Docker Compose;
- desplegar servicios del ambiente de pruebas.

Esto se realiza montando el socket:

```text
/var/run/docker.sock:/var/run/docker.sock
```

También se montan binarios del cliente Docker y plugins de Docker Compose/Buildx.

Validación dentro del contenedor:

```bash
docker exec -it jenkins-controller bash
docker ps
docker compose version
```

Esta configuración es funcional para el ambiente de pruebas, pero debe considerarse sensible desde el punto de vista de seguridad porque otorga a Jenkins capacidad de operar sobre Docker del host.

## Integración con SonarQube

Para que Jenkins pueda enviar análisis a SonarQube, ambos servicios deben compartir una red Docker.

En esta implementación, SonarQube se conecta a la red externa de Jenkins:

```text
jenkins_jenkins_net
```

Desde Jenkins, la URL interna recomendada para SonarQube es:

```text
http://sonarqube:9000
```

No se debe usar:

```text
http://localhost:9000
```

porque desde el contenedor de Jenkins, `localhost` apunta al propio contenedor de Jenkins.

En Jenkins se debe configurar el servidor SonarQube con el nombre usado por los pipelines:

```text
Sonarqube-server
```

El pipeline espera usar:

```groovy
withSonarQubeEnv('Sonarqube-server')
```

## Operación básica

Detener servicios:

```bash
docker compose down
```

Recrear servicios después de cambios en configuración:

```bash
docker compose up -d --build
```

Verificar contenedores:

```bash
docker ps
```

Revisar logs recientes de Jenkins:

```bash
docker compose logs --tail=100 jenkins
```

Revisar logs recientes de Nginx:

```bash
docker compose logs --tail=100 nginx
```

Validar conectividad HTTPS:

```bash
curl -k https://IP_O_DNS_JENKINS/login
```

## Problemas comunes

### Jenkins no levanta

Revisar logs:

```bash
docker compose logs -f jenkins
```

Validar:

- variables del archivo `.env`;
- permisos sobre el volumen `jenkins_home`;
- plugins definidos en `plugins.txt`;
- sintaxis del archivo `casc/jenkins.yaml`.

### Nginx no levanta

Revisar logs:

```bash
docker compose logs -f nginx
```

Validar:

- existencia de `certs/jenkins.crt`;
- existencia de `certs/jenkins.key`;
- sintaxis de `nginx/default.conf`;
- puerto `443` disponible en el host.

### Jenkins no puede ejecutar Docker

Validar dentro del contenedor:

```bash
docker exec -it jenkins-controller bash
docker ps
```

Revisar:

- montaje de `/var/run/docker.sock`;
- ruta de `/usr/bin/docker`;
- permisos del grupo Docker;
- `group_add` configurado en `compose.yml`.

### Jenkins no alcanza SonarQube

Validar que ambos estén en la red compartida:

```bash
docker network inspect jenkins_jenkins_net
```

Desde Jenkins, probar:

```bash
docker exec -it jenkins-controller bash
curl -I http://sonarqube:9000
```

## Consideraciones de seguridad

- El archivo `.env` no debe versionarse.
- Las credenciales deben gestionarse desde Jenkins Credentials o variables de entorno protegidas.
- Los certificados y llaves privadas reales no deben almacenarse en el repositorio.
- El proxy Nginx debe restringir el acceso únicamente a redes autorizadas.
- El montaje de `/var/run/docker.sock` permite que Jenkins controle Docker en el host, por lo que debe considerarse un permiso sensible.
- El usuario administrador inicial debe cambiarse por una contraseña segura en ambientes reales.
- Se recomienda limitar el acceso administrativo solo a usuarios autorizados.

## Relación con los pipelines

Los pipelines no fueron almacenados como `Jenkinsfile` dentro del repositorio de aplicación. En esta implementación fueron creados directamente desde la interfaz de Jenkins como `Pipeline script`.

Para mantener trazabilidad, las definiciones utilizadas se documentan en el directorio `pipelines/` del repositorio de infraestructura.
