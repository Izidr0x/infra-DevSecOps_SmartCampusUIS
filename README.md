# Infraestructura DevSecOps Smart Campus UIS

Este repositorio contiene la configuración y documentación de la infraestructura utilizada para soportar el flujo CI/CD con enfoque DevSecOps aplicado a la plataforma **Smart Campus UIS**.

La infraestructura contempla Jenkins como orquestador principal de los pipelines, Nginx como proxy reverso para Jenkins, SonarQube como plataforma de análisis de calidad y seguridad de código, PostgreSQL como base de datos dedicada de SonarQube, OWASP Dependency-Check para análisis de dependencias y Trivy como herramienta temporal de escaneo de imágenes Docker dentro del pipeline de despliegue.

## Índice

- [Alcance del repositorio](#alcance-del-repositorio)
- [Arquitectura general](#arquitectura-general)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Componentes principales](#componentes-principales)
- [Redes Docker](#redes-docker)
- [Requisitos previos del servidor](#requisitos-previos-del-servidor)
  - [Dependencias requeridas](#dependencias-requeridas)
  - [Instalación de dependencias en Rocky Linux 9](#instalación-de-dependencias-en-rocky-linux-9)
  - [Ajustes recomendados para SonarQube](#ajustes-recomendados-para-sonarqube)
  - [Validación de permisos de Docker para Jenkins](#validación-de-permisos-de-docker-para-jenkins)
- [Receta de montaje](#receta-de-montaje)
  - [1. Clonar el repositorio](#1-clonar-el-repositorio)
  - [2. Preparar archivos de variables](#2-preparar-archivos-de-variables)
  - [3. Generar certificados para Jenkins](#3-generar-certificados-para-jenkins)
  - [4. Levantar Jenkins con Nginx](#4-levantar-jenkins-con-nginx)
  - [5. Levantar SonarQube con PostgreSQL](#5-levantar-sonarqube-con-postgresql)
  - [6. Configurar Jenkins](#6-configurar-jenkins)
  - [7. Configurar SonarQube](#7-configurar-sonarqube)
  - [8. Crear los jobs de pipeline](#8-crear-los-jobs-de-pipeline)
  - [9. Ejecutar validación por bloques](#9-ejecutar-validación-por-bloques)
- [Pipelines implementados](#pipelines-implementados)
- [Credenciales requeridas](#credenciales-requeridas)
  - [Obtención del token NVD](#obtención-del-token-nvd)
  - [Registro del token NVD en Jenkins](#registro-del-token-nvd-en-jenkins)
- [Variables y valores que deben ajustarse](#variables-y-valores-que-deben-ajustarse)
- [Operación básica](#operación-básica)
- [Evidencias a conservar](#evidencias-a-conservar)
- [Problemas comunes](#problemas-comunes)
- [Consideraciones de seguridad](#consideraciones-de-seguridad)
- [Alcance de esta receta](#alcance-de-esta-receta)

## Alcance del repositorio

Este repositorio documenta y organiza los componentes de infraestructura utilizados durante el desarrollo del proyecto de grado. No contiene el código fuente principal de los microservicios de Smart Campus UIS; contiene los archivos relacionados con el despliegue, configuración y operación de las herramientas DevSecOps.

En esta implementación:

- Jenkins fue desplegado como servicio contenerizado.
- Jenkins quedó publicado detrás de un proxy reverso Nginx.
- Nginx fue utilizado únicamente para el acceso HTTPS a Jenkins.
- SonarQube fue desplegado como servicio independiente.
- SonarQube no quedó publicado detrás de Nginx.
- SonarQube despliega su propio PostgreSQL dedicado dentro del mismo `compose.yml`.
- PostgreSQL de SonarQube queda aislado en una red interna.
- Jenkins y SonarQube comparten una red Docker para permitir el envío de análisis desde los pipelines.
- Trivy no fue desplegado como servicio persistente.
- Trivy se ejecuta temporalmente dentro del pipeline CD-01.
- Los pipelines fueron configurados desde la interfaz de Jenkins y documentados en este repositorio para trazabilidad.

## Arquitectura general

El flujo DevSecOps se organiza en tres bloques funcionales:

```text
Full Pipeline
    │
    ├── CI-01 Source Compile - Unit Validation
    │       ├── Git checkout
    │       ├── Code compile
    │       ├── Unit Test
    │       └── Build artifact
    │
    ├── CI-02 Security - Quality Gate
    │       ├── Get Artifacts from Pipeline 1
    │       ├── Git checkout del commit construido
    │       ├── Prepare binaries for analysis
    │       ├── SonarQube Analysis
    │       ├── Quality Gate
    │       └── OWASP Dependency-Check
    │
    └── CD-01 Containerization - Deployment
            ├── Get Artifacts from CI-01
            ├── Git checkout del commit construido
            ├── Docker build
            ├── Trivy scan
            ├── Docker push
            ├── Deploy dependencies
            ├── Prepare Telegraf config
            ├── Deploy Telegraf
            ├── Deploy admin and data
            ├── Deploy remaining services
            └── Smoke Test
```

Relación general entre componentes:

```text
Repositorio de código analizado
        │
        ▼
Jenkins + Nginx
        │
        ├── Maven / JDK
        ├── JUnit
        ├── SonarQube
        ├── OWASP Dependency-Check
        ├── Docker
        ├── Trivy temporal
        └── Docker Compose

SonarQube
        │
        ▼
PostgreSQL dedicado
```

## Estructura del repositorio

```text
.
├── jenkins/
│   ├── README.md
│   ├── compose.yml
│   ├── Dockerfile
│   ├── plugins.txt
│   ├── .env.example
│   ├── casc/
│   │   └── jenkins.yaml
│   ├── nginx/
│   │   └── default.conf
│   └── certs/
│       ├── README.md
│       ├── gen_cert.sh
│       └── openssl-ip.cnf.example
│
├── sonarqube/
│   ├── README.md
│   ├── compose.yml
│   └── .env.example
│
├── pipelines/
│   ├── README.md
│   ├── CI-01 Source Compile - Unit Validation.groovy
│   ├── CI-02 Security - Quality Gate.groovy
│   ├── CD-01 Containerization - Deployment.groovy
│   └── Full Pipeline.groovy
│
└── README.md
```

## Componentes principales

| Componente | Rol dentro del proyecto |
|---|---|
| Jenkins | Orquestador principal del flujo CI/CD. |
| Nginx | Proxy reverso HTTPS para Jenkins. |
| SonarQube | Análisis estático de código y validación de Quality Gate. |
| PostgreSQL | Base de datos dedicada para SonarQube. |
| OWASP Dependency-Check | Análisis de dependencias y vulnerabilidades conocidas. |
| Trivy | Escaneo temporal de imágenes Docker durante CD-01. |
| Docker Compose | Despliegue de servicios del ambiente de pruebas. |

## Redes Docker

La implementación utiliza separación de redes para limitar la exposición de los componentes.

```text
jenkins_jenkins_net  -> Jenkins + Nginx + SonarQube
sonarnet             -> SonarQube + PostgreSQL
```

Relación esperada:

```text
Jenkins  -------->  SonarQube  -------->  PostgreSQL
        red CI/CD              red interna de SonarQube
```

Jenkins necesita comunicarse con SonarQube para enviar análisis y recibir resultados del Quality Gate. PostgreSQL no necesita ser accesible desde Jenkins, por lo que queda únicamente en `sonarnet`.

El `compose.yml` de SonarQube espera que la red externa de Jenkins exista con el nombre:

```text
jenkins_jenkins_net
```

Por eso se recomienda levantar Jenkins con el nombre de proyecto `jenkins`, usando:

```bash
docker compose -p jenkins up -d --build
```

Si se levanta Jenkins con otro nombre de proyecto, la red generada puede cambiar y será necesario ajustar el `compose.yml` de SonarQube.

## Requisitos previos del servidor

### Dependencias requeridas

El servidor de pruebas debe contar con:

- Rocky Linux 9 o una distribución Linux compatible.
- Usuario con permisos `sudo`.
- Acceso a Internet.
- Docker Engine.
- Docker Compose v2 como plugin de Docker.
- Git.
- OpenSSL.
- Curl.
- Vim o editor de texto equivalente.
- Firewall configurado según el ambiente.
- Acceso al repositorio de infraestructura.
- Acceso al repositorio de código analizado.
- Credenciales de Docker Hub.
- Token o API Key de NVD.
- IP o nombre DNS definido para Jenkins.
- IP o nombre DNS definido para SonarQube.

### Instalación de dependencias en Rocky Linux 9

Actualizar el sistema:

```bash
sudo dnf update -y
```

Instalar utilidades base:

```bash
sudo dnf install -y git curl vim openssl dnf-plugins-core firewalld
```

Eliminar paquetes que puedan entrar en conflicto con Docker:

```bash
sudo dnf remove -y docker \
  docker-client \
  docker-client-latest \
  docker-common \
  docker-latest \
  docker-latest-logrotate \
  docker-logrotate \
  docker-engine \
  podman \
  runc
```

Agregar el repositorio oficial de Docker para distribuciones compatibles con RHEL:

```bash
sudo dnf config-manager --add-repo https://download.docker.com/linux/rhel/docker-ce.repo
```

Instalar Docker Engine, Docker CLI, containerd, Buildx y Docker Compose plugin:

```bash
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

Habilitar e iniciar Docker:

```bash
sudo systemctl enable --now docker
```

Verificar estado del servicio:

```bash
sudo systemctl status docker
```

Validar Docker:

```bash
sudo docker run hello-world
```

Validar Docker Compose v2:

```bash
docker compose version
```

Agregar el usuario actual al grupo `docker`:

```bash
sudo usermod -aG docker $USER
```

Aplicar el cambio de grupo cerrando sesión y entrando nuevamente, o temporalmente con:

```bash
newgrp docker
```

Validar ejecución sin `sudo`:

```bash
docker ps
```

Verificar versiones base:

```bash
docker --version
docker compose version
git --version
openssl version
```

### Ajustes recomendados para SonarQube

SonarQube utiliza Elasticsearch internamente, por lo que en Linux se recomienda ajustar límites del host.

Aplicar ajustes en ejecución:

```bash
sudo sysctl -w vm.max_map_count=524288
sudo sysctl -w fs.file-max=131072
```

Persistir configuración:

```bash
cat <<'EOF' | sudo tee /etc/sysctl.d/99-sonarqube.conf
vm.max_map_count=524288
fs.file-max=131072
EOF
```

Aplicar configuración persistente:

```bash
sudo sysctl --system
```

Validar valores:

```bash
sysctl vm.max_map_count
sysctl fs.file-max
```

### Validación de permisos de Docker para Jenkins

El `compose.yml` de Jenkins monta el socket Docker del host:

```text
/var/run/docker.sock:/var/run/docker.sock
```

Por eso el contenedor de Jenkins debe tener permisos para usar el socket.

Validar grupo del socket Docker en el host:

```bash
ls -l /var/run/docker.sock
getent group docker
```

Si el grupo Docker del host no corresponde al valor configurado en `group_add` dentro de `jenkins/compose.yml`, ajustar ese valor.

Ejemplo:

```yaml
group_add:
  - "993"
```

El número debe coincidir con el GID del grupo que tiene permisos sobre `/var/run/docker.sock`.

## Receta de montaje

### 1. Clonar el repositorio

```bash
git clone https://github.com/Izidr0x/infra-DevSecOps_SmartCampusUIS.git
cd infra-DevSecOps_SmartCampusUIS
```

Validar estructura:

```bash
ls -la
```

### 2. Preparar archivos de variables

Los archivos `.env` reales no deben subirse al repositorio.

Crear archivos locales a partir de los ejemplos:

```bash
cp jenkins/.env.example jenkins/.env
cp sonarqube/.env.example sonarqube/.env
```

Editar valores:

```bash
vim jenkins/.env
vim sonarqube/.env
```

Ejemplo conceptual para Jenkins:

```env
JENKINS_URL=https://192.168.x.x/
JENKINS_ADMIN_ID=admin
JENKINS_ADMIN_PASSWORD=change_me
TZ=America/Bogota
```

Ejemplo conceptual para SonarQube:

```env
POSTGRES_DB=sonar_db
POSTGRES_USER=sonar
POSTGRES_PASSWORD=change_me

SONAR_JDBC_URL=jdbc:postgresql://sonarqube_db:5432/sonar_db
SONAR_JDBC_USERNAME=sonar
SONAR_JDBC_PASSWORD=change_me
```

Reglas importantes:

- No subir `.env` reales.
- No subir contraseñas.
- No subir tokens.
- No subir llaves privadas.
- No subir certificados generados.
- Usar `.env.example` para documentar variables.

`.gitignore` recomendado:

```gitignore
.env
*.key
*.crt
*.pem
jenkins/certs/*.key
jenkins/certs/*.crt
jenkins/certs/*.pem
```

### 3. Generar certificados para Jenkins

Jenkins es el único servicio publicado detrás de Nginx. El directorio de certificados aplica para el proxy Nginx que publica Jenkins con HTTPS interno.

Entrar al directorio:

```bash
cd jenkins/certs
```

Copiar la plantilla de OpenSSL:

```bash
cp openssl-ip.cnf.example openssl-ip.cnf
```

Editar la IP o DNS del servidor:

```bash
vim openssl-ip.cnf
```

Ejemplo conceptual para acceso por IP:

```ini
[req]
default_bits = 4096
prompt = no
default_md = sha256
distinguished_name = dn
x509_extensions = req_ext

[dn]
CN = 192.168.x.x

[req_ext]
subjectAltName = @alt_names
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth

[alt_names]
IP.1 = 192.168.x.x
```

Si se accede por dominio, puede usarse una entrada DNS:

```ini
[alt_names]
DNS.1 = jenkins.midominio.local
```

La IP o dominio configurado en `[alt_names]` debe coincidir con la dirección usada para acceder a Jenkins. Por ejemplo, si Jenkins se abre como `https://192.168.1.50`, entonces debe existir una entrada `IP.1 = 192.168.1.50`. Si se accede por dominio, debe existir una entrada `DNS.1 = nombre-del-dominio`.

Generar certificado:

```bash
chmod +x gen_cert.sh
./gen_cert.sh
```

Archivos esperados localmente:

```text
jenkins.crt
jenkins.key
```

Estos archivos no deben versionarse en Git.

Validar certificado:

```bash
openssl x509 -in jenkins.crt -text -noout
```

Regresar al directorio principal:

```bash
cd ../..
```

### 4. Levantar Jenkins con Nginx

Entrar al directorio de Jenkins:

```bash
cd jenkins
```

Levantar Jenkins y Nginx usando el nombre de proyecto esperado:

```bash
docker compose -p jenkins up -d --build
```

Validar estado:

```bash
docker compose -p jenkins ps
```

Revisar logs:

```bash
docker compose -p jenkins logs -f jenkins
docker compose -p jenkins logs -f nginx
```

Validar acceso HTTPS:

```bash
curl -k https://IP_O_DNS_JENKINS/login
```

En navegador:

```text
https://IP_O_DNS_JENKINS/
```

Si se usa certificado autofirmado, el navegador puede mostrar una advertencia de confianza.

Validar red creada por Jenkins:

```bash
docker network ls
```

La red esperada es:

```text
jenkins_jenkins_net
```

### 5. Levantar SonarQube con PostgreSQL

SonarQube se despliega como servicio independiente. No queda detrás de Nginx.

Antes de levantarlo, confirmar que existe la red de Jenkins:

```bash
docker network ls | grep jenkins_jenkins_net
```

Si no existe, Jenkins no fue levantado con el nombre de proyecto esperado o la red debe crearse manualmente:

```bash
docker network create jenkins_jenkins_net
```

Entrar al directorio de SonarQube:

```bash
cd ../sonarqube
```

Crear `.env` si aún no existe:

```bash
cp .env.example .env
```

Editar variables:

```bash
vim .env
```

Levantar SonarQube y PostgreSQL:

```bash
docker compose up -d
```

Verificar estado:

```bash
docker compose ps
```

Revisar logs:

```bash
docker compose logs -f sonarqube
docker compose logs -f sonarqube_db
```

Acceso:

```text
http://IP_O_DNS_SONARQUBE:9000
```

En un montaje desde cero, PostgreSQL crea automáticamente la base de datos y el usuario indicados en el `.env`, siempre que el volumen de datos esté vacío o no exista previamente.

Validar base creada:

```bash
docker exec -it sonarqube_postgres psql -U sonar -d postgres -c "\l"
```

### 6. Configurar Jenkins

Ingresar a Jenkins y validar:

```text
Manage Jenkins
```

Configurar o verificar:

- plugins;
- herramientas globales;
- credenciales;
- SonarQube Server;
- SonarScanner;
- OWASP Dependency-Check;
- Docker;
- jobs tipo Pipeline.

Herramientas esperadas por los pipelines:

| Herramienta | Nombre esperado |
|---|---|
| JDK | `JDK-21` |
| Maven | `maven3` |
| SonarScanner | `sonar-scanner` |
| OWASP Dependency-Check | `Check-DP` |
| Docker | `docker` |

### 7. Configurar SonarQube

Crear token para Jenkins:

```text
SonarQube > Administration > Security > Users > Tokens
```

Registrar token en Jenkins:

```text
Manage Jenkins > Credentials > Global > Add credentials
```

Tipo recomendado:

```text
Secret text
```

Registrar servidor SonarQube en Jenkins:

```text
Manage Jenkins > System > SonarQube servers
```

Valores esperados:

```text
Name: Sonarqube-server
Server URL: http://sonarqube:9000
Authentication token: credencial creada en Jenkins
```

El nombre debe coincidir con el pipeline:

```groovy
withSonarQubeEnv('Sonarqube-server')
```

Para que `waitForQualityGate` funcione, configurar el webhook en SonarQube:

```text
SonarQube > Administration > Configuration > Webhooks
```

URL recomendada si SonarQube y Jenkins se comunican por la red Docker compartida:

```text
http://jenkins:8080/sonarqube-webhook/
```

URL alternativa si se usa el acceso HTTPS externo por Nginx:

```text
https://IP_O_DNS_JENKINS/sonarqube-webhook/
```

### 8. Crear los jobs de pipeline

Los pipelines se crean como `Pipeline script` desde la interfaz de Jenkins.

Ruta:

```text
Jenkins > New Item > Pipeline
```

Crear estos jobs con nombres exactos:

```text
CI-01 Source Compile - Unit Validation
CI-02 Security - Quality Gate
CD-01 Containerization - Deployment
Full Pipeline
```

Los nombres deben coincidir porque `copyArtifacts` y `build job` dependen de ellos.

Pegar el contenido de los archivos del directorio `pipelines/` en cada job correspondiente.

### 9. Ejecutar validación por bloques

Orden recomendado:

1. Ejecutar CI-01.
2. Validar artefactos y resultados JUnit.
3. Ejecutar CI-02.
4. Validar SonarQube, Quality Gate y Dependency-Check.
5. Ejecutar CD-01.
6. Validar imágenes, Trivy, Docker push, despliegue y Smoke Test.
7. Ejecutar Full Pipeline.

## Pipelines implementados

### CI-01 Source Compile - Unit Validation

Este pipeline realiza:

- checkout del repositorio;
- selección de rama;
- selección opcional de commit;
- registro del commit real en `.git_commit`;
- compilación con Maven;
- pruebas unitarias;
- empaquetado de artefactos;
- publicación de resultados JUnit;
- archivado de artefactos.

Artefactos esperados:

```text
admin_microservice/target/*.jar
data_microservice/application/target/*.jar
.git_commit
**/Dockerfile
```

### CI-02 Security - Quality Gate

Este pipeline realiza:

- recuperación de artefactos de CI-01;
- lectura del archivo `.git_commit`;
- checkout del mismo commit construido por CI-01;
- preparación de binarios Java para análisis;
- análisis SonarQube;
- espera y validación explícita del Quality Gate;
- análisis de dependencias con OWASP Dependency-Check;
- publicación del reporte XML;
- archivado de evidencias.

Elementos requeridos:

```text
JDK-21
maven3
sonar-scanner
Sonarqube-server
Check-DP
nvd-api-key
```

### CD-01 Containerization - Deployment

Este pipeline realiza:

- recuperación de artefactos de CI-01;
- lectura de `.git_commit`;
- checkout del mismo commit construido;
- build de imágenes Docker;
- tag con número de build y `latest`;
- escaneo temporal con Trivy;
- push a Docker Hub;
- despliegue progresivo con Docker Compose;
- preparación y recreación de Telegraf;
- Smoke Test.

Servicios validados en el Smoke Test:

```text
db
mongo
emqx
influxdb
rabbitmq
minio
telegraf
admin
data
gateway
frontend
grafana
```

### Full Pipeline

Este pipeline ejecuta en orden:

```text
CI-01 Source Compile - Unit Validation
CI-02 Security - Quality Gate
CD-01 Containerization - Deployment
```

Sirve para lanzar el flujo completo desde un solo job.

## Credenciales requeridas

| Credencial | Tipo recomendado | Uso |
|---|---|---|
| Token de SonarQube | `Secret text` | Permitir que Jenkins envíe análisis a SonarQube. |
| `nvd-api-key` | `Secret text` | API Key usada por OWASP Dependency-Check. |
| Docker Hub | `Username with password` | Publicar imágenes Docker. |

Nota importante: en el pipeline CD-01 la credencial de Docker Hub aparece con `credentialsId: '2'`. Se debe crear una credencial con ese ID o modificar el pipeline para usar un ID más descriptivo, por ejemplo:

```text
docker-hub-credentials
```


### Obtención del token NVD

OWASP Dependency-Check consulta información de vulnerabilidades desde la National Vulnerability Database. Para evitar demoras o límites más restrictivos durante la actualización de datos, se recomienda usar una API Key de NVD.

Pasos para solicitarla:

1. Ingresar al portal oficial de solicitud de API Key de NVD:

   ```text
   https://nvd.nist.gov/developers/request-an-api-key
   ```

2. Diligenciar los campos solicitados:

   ```text
   Organization Name
   Email Address
   Organization Type
   ```

   Para un montaje académico o de laboratorio, puede usarse la opción de organización que mejor represente el caso. Si aplica, puede seleccionarse una opción relacionada con uso académico o personal.

3. Leer los términos de uso, desplazarse hasta el final del acuerdo y marcar la aceptación de términos.

4. Enviar la solicitud.

5. Revisar el correo registrado. NVD envía un enlace de un solo uso para activar y visualizar la API Key.

6. Abrir el enlace recibido y copiar la API Key en un lugar seguro.

7. Guardar la API Key inmediatamente. No debe almacenarse en archivos versionados, capturas públicas ni documentación del repositorio.

Notas importantes:

- El enlace de activación enviado por NVD es de un solo uso.
- Si la clave no se activa dentro del plazo indicado por NVD, se debe realizar una nueva solicitud.
- Si se genera una nueva clave para el mismo correo, la clave anterior puede quedar desactivada.
- La API Key debe tratarse como una credencial sensible, aunque su uso sea para consultar información pública de vulnerabilidades.

### Registro del token NVD en Jenkins

Después de obtener la API Key de NVD, se debe registrar en Jenkins como credencial segura.

Ruta recomendada:

```text
Manage Jenkins > Credentials > System > Global credentials > Add Credentials
```

Configurar la credencial así:

```text
Kind: Secret text
Secret: <API_KEY_DE_NVD>
ID: nvd-api-key
Description: NVD API Key para OWASP Dependency-Check
```

El valor importante es el ID:

```text
nvd-api-key
```

Ese ID debe coincidir con el usado en el pipeline CI-02. De esa forma, Jenkins puede inyectar la API Key durante la ejecución sin dejarla escrita directamente en el script.

Ejemplo conceptual de uso en pipeline:

```groovy
withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
    sh '''
        dependency-check.sh \
          --project smart-campus-uis \
          --scan . \
          --format XML \
          --out dependency-check-report \
          --nvdApiKey "$NVD_API_KEY"
    '''
}
```

En el pipeline real, el uso puede variar según la configuración del plugin OWASP Dependency-Check en Jenkins, pero la idea se mantiene: la API Key debe estar guardada como credencial y no escrita directamente en el repositorio.

## Variables y valores que deben ajustarse

Antes de ejecutar los pipelines, revisar especialmente:

| Ubicación | Valor | Ajuste requerido |
|---|---|---|
| `jenkins/.env` | `JENKINS_URL` | Debe apuntar a la URL real de Jenkins por HTTPS. |
| `jenkins/.env` | `JENKINS_ADMIN_PASSWORD` | Debe reemplazarse por una contraseña segura. |
| `jenkins/nginx/default.conf` | `server_name` | Debe coincidir con la IP o DNS de Jenkins. |
| `jenkins/nginx/default.conf` | reglas `allow` | Deben ajustarse a las redes autorizadas del ambiente. |
| `jenkins/certs/openssl-ip.cnf` | `CN` y `[alt_names]` | Deben coincidir con la IP o DNS usada para Jenkins. |
| `jenkins/compose.yml` | `group_add` | Debe coincidir con el GID del grupo Docker del host. |
| `sonarqube/.env` | `POSTGRES_PASSWORD` | Debe reemplazarse por una contraseña segura. |
| `sonarqube/.env` | `SONAR_JDBC_URL` | Debe apuntar a `sonarqube_db` y a la base definida. |
| `pipelines/CD-01...groovy` | `DOCKER_NAMESPACE` | Debe coincidir con el namespace real en Docker Hub. |
| `pipelines/CD-01...groovy` | `credentialsId: '2'` | Debe coincidir con la credencial real de Docker Hub. |
| `pipelines/CD-01...groovy` | `HOST_IP` | Debe coincidir con la IP real del host donde responde la aplicación. |
| Jenkins Tools | `JDK-21`, `maven3`, `sonar-scanner`, `Check-DP`, `docker` | Los nombres deben coincidir exactamente con los usados por los pipelines. |

## Operación básica

### Jenkins

Desde `jenkins/`:

```bash
docker compose -p jenkins ps
docker compose -p jenkins logs -f jenkins
docker compose -p jenkins logs -f nginx
docker compose -p jenkins down
docker compose -p jenkins up -d --build
```

Validar acceso HTTPS:

```bash
curl -k https://IP_O_DNS_JENKINS/login
```

Validar Docker dentro del contenedor:

```bash
docker exec -it jenkins-controller bash
docker ps
docker compose version
```

### SonarQube

Desde `sonarqube/`:

```bash
docker compose ps
docker compose logs -f sonarqube
docker compose logs -f sonarqube_db
docker compose restart sonarqube
docker compose restart sonarqube_db
docker compose down
docker compose up -d
```

Validar bases en PostgreSQL:

```bash
docker exec -it sonarqube_postgres psql -U sonar -d postgres -c "\l"
```

Validar conexión directa a la base de SonarQube:

```bash
docker exec -it sonarqube_postgres psql -U sonar -d sonar_db
```

Salir de PostgreSQL:

```sql
\q
```

### Redes

Validar red de Jenkins:

```bash
docker network inspect jenkins_jenkins_net
```

Validar red interna de SonarQube:

```bash
docker network ls
docker network inspect sonarqube_sonarnet
```

El nombre real de la red interna puede cambiar según el nombre del proyecto Compose usado para SonarQube.

## Evidencias a conservar

Guardar evidencias de:

- consola de Jenkins;
- Stage View;
- resultados JUnit;
- archivo `.git_commit`;
- artefactos `.jar`;
- análisis SonarQube;
- resultado del Quality Gate;
- reporte `dependency-check-report.xml`;
- reportes de Trivy;
- imágenes publicadas;
- estado de contenedores;
- resultado del Smoke Test.

Estas evidencias soportan la trazabilidad entre:

- versión del código;
- ejecución de pipeline;
- artefacto generado;
- imagen construida;
- hallazgos de seguridad;
- despliegue validado.

## Problemas comunes

### Jenkins no puede ejecutar Docker

Validar:

```bash
docker exec -it jenkins-controller bash
docker ps
```

Revisar:

- montaje de `/var/run/docker.sock`;
- permisos del usuario Jenkins;
- cliente Docker dentro del contenedor;
- grupo Docker del host;
- valor `group_add` en `jenkins/compose.yml`.

### Nginx no permite acceso a Jenkins

Revisar:

```bash
docker compose -p jenkins logs -f nginx
```

Validar en `jenkins/nginx/default.conf`:

- `server_name`;
- certificados;
- redes permitidas con `allow`;
- puerto `443`;
- reglas de firewall del host.

### SonarQube no arranca por Elasticsearch

Validar límites del host:

```bash
sysctl vm.max_map_count
sysctl fs.file-max
```

Aplicar ajustes si es necesario:

```bash
sudo sysctl -w vm.max_map_count=524288
sudo sysctl -w fs.file-max=131072
```

### SonarQube no conecta a PostgreSQL

Validar que el JDBC apunte al servicio correcto:

```env
SONAR_JDBC_URL=jdbc:postgresql://sonarqube_db:5432/sonar_db
```

Validar bases existentes:

```bash
docker exec -it sonarqube_postgres psql -U sonar -d postgres -c "\l"
```

Si el volumen de PostgreSQL ya existía, cambiar `POSTGRES_DB` en `.env` no recrea automáticamente la base. Para un montaje limpio, si no hay datos que conservar:

```bash
docker compose down -v
docker compose up -d
```

### Jenkins no alcanza SonarQube

Validar que SonarQube esté conectado a la red de Jenkins:

```bash
docker network inspect jenkins_jenkins_net
```

Desde Jenkins:

```bash
docker exec -it jenkins-controller bash
curl -I http://sonarqube:9000
```

### CI-02 no recupera artefactos

Revisar:

- que CI-01 haya finalizado correctamente;
- que CI-01 haya archivado los artefactos;
- que el nombre del job coincida exactamente;
- que los patrones de `copyArtifacts` coincidan con las rutas reales.

### Quality Gate queda esperando

Revisar:

- webhook de SonarQube hacia Jenkins;
- conectividad SonarQube -> Jenkins;
- URL `/sonarqube-webhook/`;
- nombre `Sonarqube-server`;
- token configurado.

### OWASP Dependency-Check falla

Revisar:

- credencial `nvd-api-key`;
- acceso a Internet;
- límites de consulta de NVD;
- configuración `Check-DP`;
- parámetro `--nvdApiDelay`.

### Trivy no encuentra imágenes

Revisar:

- que Docker build haya terminado correctamente;
- nombres de imagen;
- tags;
- acceso al socket Docker;
- existencia local de las imágenes.

### Smoke Test falla

Revisar:

```bash
docker compose ps
docker compose logs <servicio>
```

Validar:

- puertos;
- variables de entorno;
- dependencias levantadas;
- tiempos de inicialización;
- IP usada en el pipeline;
- URL `/actuator/health` del servicio `data`.

## Consideraciones de seguridad

- No versionar archivos `.env` reales.
- No versionar contraseñas, tokens ni llaves privadas.
- No subir certificados generados localmente.
- Restringir el acceso HTTPS a Jenkins desde Nginx.
- Revisar las reglas `allow` y `deny` del proxy Nginx.
- Usar tokens de acceso para integraciones.
- Mantener PostgreSQL de SonarQube sin publicar el puerto `5432`.
- Mantener PostgreSQL solo en la red interna `sonarnet`.
- Tratar el montaje de `/var/run/docker.sock` como un permiso sensible.
- Cambiar contraseñas de ejemplo antes de desplegar.
- Realizar backups periódicos de la base de datos de SonarQube y de los volúmenes relevantes.

## Alcance de esta receta

Esta receta cubre el montaje del ambiente de pruebas usado para validar la arquitectura DevSecOps del proyecto.

Incluye:

- Jenkins;
- Nginx para Jenkins;
- certificados internos;
- SonarQube;
- PostgreSQL dedicado para SonarQube;
- Quality Gate;
- OWASP Dependency-Check;
- Docker;
- Trivy temporal;
- Docker Compose;
- CI-01;
- CI-02;
- CD-01;
- Full Pipeline;
- Smoke Test.

No cubre completamente:

- hardening productivo;
- alta disponibilidad;
- backup y restore formal;
- DAST;
- SBOM;
- firma de artefactos;
- rollback automatizado;
- despliegue productivo institucional;
- pruebas funcionales exhaustivas;
- pruebas de regresión completas.

Estos puntos pueden tratarse como trabajo futuro o evolución del entorno.
