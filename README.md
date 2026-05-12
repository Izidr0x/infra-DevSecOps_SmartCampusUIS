# Infraestructura DevSecOps Smart Campus UIS

Este repositorio contiene la configuración y documentación de la infraestructura utilizada para soportar el flujo CI/CD con enfoque DevSecOps aplicado a la plataforma Smart Campus UIS.

La infraestructura implementada contempla Jenkins como orquestador principal de los pipelines, SonarQube como herramienta de análisis de calidad y seguridad del código, OWASP Dependency Check para análisis de dependencias, y Trivy como herramienta temporal de escaneo de imágenes Docker dentro del pipeline de despliegue.

## Alcance del repositorio

Este repositorio documenta y organiza los componentes de infraestructura utilizados durante el desarrollo del proyecto de grado. No contiene el código fuente principal de los microservicios de Smart Campus UIS, sino los archivos relacionados con el despliegue, configuración y operación de las herramientas DevSecOps.

En esta implementación:

- Jenkins fue desplegado como servicio contenerizado.
- Jenkins quedó publicado detrás de un proxy reverso Nginx.
- Nginx fue utilizado únicamente para el acceso a Jenkins.
- SonarQube fue desplegado como servicio independiente.
- SonarQube no quedó publicado detrás de Nginx.
- Trivy no fue desplegado como servicio persistente.
- Trivy se ejecuta temporalmente dentro del pipeline CD-01.
- Los pipelines fueron configurados desde la interfaz de Jenkins y documentados en este repositorio para trazabilidad.

## Estructura del repositorio

```text
.
├── jenkins/
│   ├── README.md
│   ├── compose.yml
│   ├── Dockerfile
│   ├── plugins.txt
│   ├── nginx/
│   └── certs/
│
├── sonarqube/
│   ├── README.md
│   └── compose.yml
│
├── pipelines/
│   ├── README.md
│   ├── CI-01 Source Compile - Unit Validation.groovy
│   ├── CI-02 Security - Quality Gate.groovy
│   ├── CD-01 Containerization - Deployment.groovy
│   └── Full Pipeline.groovy
│
└── README.md

---

# Receta de montaje de la infraestructura DevSecOps Smart Campus UIS

Esta guía describe el paso a paso para reproducir el ambiente de infraestructura DevSecOps usado en el proyecto **Smart Campus UIS**. Está pensada como una receta práctica para el repositorio de infraestructura y complementa los README específicos de cada directorio.

El montaje contempla:

- Jenkins como orquestador CI/CD.
- Nginx como proxy reverso únicamente para Jenkins.
- Certificados internos/autofirmados para el acceso HTTPS a Jenkins.
- SonarQube como servicio independiente para análisis SAST y Quality Gate.
- OWASP Dependency-Check para análisis de composición de software.
- Docker y Docker Compose para construcción y despliegue.
- Trivy ejecutado temporalmente dentro del pipeline CD-01.
- Pipelines configurados desde la interfaz de Jenkins y documentados en el repositorio.


---

## 1. Lectura recomendada del repositorio

Antes de ejecutar comandos, revisar los README de cada componente:

```text
infra-DevSecOps_SmartCampusUIS/
├── README.md
├── jenkins/
│   ├── README.md
│   └── certs/
│       └── README.md
├── sonarqube/
│   └── README.md
└── pipelines/
    └── README.md
```

Uso recomendado de cada README:

| Documento | Uso |
|---|---|
| `README.md` | Visión general del repositorio, alcance y estructura. |
| `jenkins/README.md` | Despliegue de Jenkins, Nginx, variables, operación y consideraciones de seguridad. |
| `jenkins/certs/README.md` | Generación y uso de certificados para Nginx/Jenkins. |
| `sonarqube/README.md` | Despliegue, variables, volúmenes, acceso e integración con Jenkins. |
| `pipelines/README.md` | Descripción de CI-01, CI-02, CD-01 y Full Pipeline. |

Esta receta no reemplaza esos archivos. Los usa como base para ordenar el montaje completo.

---

## 2. Arquitectura general

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
    │       ├── Sonarqube Analysis
    │       ├── Quality Gate
    │       └── OWASP DEPENDENCY CHECK
    │
    └── CD-01 Containerization - Deployment
            ├── Get Artifacts from CI-01
            ├── Git checkout del commit construido
            ├── Docker build
            ├── Trivy scan
            ├── Docker push
            ├── Deploy dependencies
            ├── Prepare telegraf config
            ├── Deploy telegraf
            ├── Deploy admin and data
            ├── Deploy remaining services
            └── Smoke Test
```

Relación entre componentes:

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
```

---

## 3. Requisitos previos del servidor

El servidor de pruebas debe contar con:

- Linux (En este proyecto de uso Rocky linux 9).
- Docker.
- Docker Compose.
- Git.
- Acceso a Internet.
- Permisos para ejecutar contenedores.
- Acceso al repositorio de infraestructura.
- Acceso al repositorio de código analizado.
- Credenciales de Docker Hub.
- Token/API Key de NVD.
- IP o nombre DNS definido para acceder a Jenkins.
- IP o nombre DNS definido para acceder a SonarQube.

Validar herramientas base:

```bash
docker --version
docker compose version
git --version
```

---

## 4. Clonar el repositorio de infraestructura

```bash
git clone https://github.com/Izidr0x/infra-DevSecOps_SmartCampusUIS.git
cd infra-DevSecOps_SmartCampusUIS
```

Validar estructura:

```bash
ls -la
```

Estructura esperada:

```text
jenkins/
pipelines/
sonarqube/
README.md
```

---

## 5. Preparar archivos `.env`

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
SONAR_JDBC_URL=jdbc:postgresql://192.168.x.x:5432/sonar_db
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

---

## 6. Preparar certificados para Jenkins/Nginx

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

Ejemplo conceptual:

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

> Estos archivos no deben versionarse en Git.

Validar certificado:

```bash
openssl x509 -in jenkins.crt -text -noout
```

Regresar al directorio principal del repositorio:

```bash
cd ../..
```

---

## 7. Montar Jenkins con Nginx

Entrar al directorio de Jenkins:

```bash
cd jenkins
```

Validar archivos principales:

```bash
ls -la
```

Archivos esperados:

```text
compose.yml
Dockerfile
plugins.txt
.env
.env.example
casc/
nginx/
certs/
README.md
```

Levantar Jenkins y Nginx:

```bash
docker compose up -d --build
```

Validar estado:

```bash
docker compose ps
```

Revisar logs:

```bash
docker compose logs -f jenkins
```

```bash
docker compose logs -f nginx
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

---

## 8. Validar persistencia de Jenkins

Jenkins debe tener persistencia en `/var/jenkins_home`.

Validar contenedores:

```bash
docker ps
```

Validar volúmenes:

```bash
docker volume ls
```

La persistencia permite conservar:

- configuración interna;
- usuarios;
- credenciales;
- jobs;
- plugins;
- historial de ejecuciones;
- artefactos y reportes asociados a builds.

---

## 9. Consideración sobre Docker en Jenkins

El montaje permite que Jenkins interactúe con Docker del host para:

- construir imágenes;
- ejecutar Trivy como contenedor temporal;
- publicar imágenes;
- ejecutar Docker Compose;
- desplegar servicios del ambiente de pruebas.

Esto normalmente se realiza montando el socket:

```text
/var/run/docker.sock:/var/run/docker.sock
```

Validación dentro del contenedor:

```bash
docker exec -it jenkins bash
docker ps
```

> Esta configuración es funcional para el ambiente de pruebas, pero debe considerarse sensible desde el punto de vista de seguridad porque otorga a Jenkins capacidad de operar sobre Docker del host.

---

## 10. Configuración inicial de Jenkins

Ingresar a Jenkins desde el navegador y completar la configuración inicial.

Luego revisar:

```text
Manage Jenkins
```

Configurar o validar:

- plugins;
- herramientas globales;
- credenciales;
- SonarQube Server;
- SonarScanner;
- OWASP Dependency-Check;
- Docker;
- jobs tipo Pipeline.

---

## 11. Plugins necesarios en Jenkins

Instalar desde:

```text
Manage Jenkins > Plugins > Available plugins
```

Plugins principales:

| Plugin | Uso |
|---|---|
| Pipeline | Crear y ejecutar pipelines. |
| Git | Obtener código fuente desde GitHub. |
| JUnit | Publicar resultados de pruebas unitarias. |
| Copy Artifact | Copiar artefactos entre jobs. |
| SonarQube Scanner for Jenkins | Integrar análisis de SonarQube. |
| OWASP Dependency-Check | Ejecutar análisis SCA. |
| Docker | Integración con Docker. |
| Docker Pipeline | Uso de Docker en pipelines. |
| Credentials Binding | Inyectar credenciales de forma segura. |
| Configuration as Code | Soportar configuración declarativa inicial. |

---

## 12. Configurar herramientas globales en Jenkins

Ruta:

```text
Manage Jenkins > Tools
```

Configurar los nombres exactamente como los usan los pipelines.

### 12.1 JDK

Nombre:

```text
JDK-21
```

### 12.2 Maven

Nombre:

```text
maven3
```

### 12.3 SonarScanner

Nombre:

```text
sonar-scanner
```

### 12.4 OWASP Dependency-Check

Nombre:

```text
Check-DP
```

### 12.5 Docker

Nombre:

```text
docker
```

---

## 13. Montar SonarQube

SonarQube se despliega como servicio independiente. No queda detrás de Nginx.

Entrar al directorio:

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

Levantar SonarQube:

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
```

Acceso:

```text
http://IP_O_DNS_SONARQUBE:9000
```

---

## 14. Validar persistencia de SonarQube

SonarQube usa volúmenes para conservar:

- configuración;
- datos;
- logs;
- extensiones;
- plugins.

Validar volúmenes:

```bash
docker volume ls
```

Verificar que el servicio pueda conectarse a PostgreSQL según la configuración del `compose.yml` y las variables definidas.

---

## 15. Configurar SonarQube para integrarse con Jenkins

### 15.1 Crear token en SonarQube

Ruta general:

```text
SonarQube > Administration > Security > Users > Tokens
```

Crear un token para Jenkins y copiarlo.

### 15.2 Registrar token en Jenkins

Ruta:

```text
Manage Jenkins > Credentials > Global > Add credentials
```

Tipo:

```text
Secret text
```

Guardar el token.

### 15.3 Registrar SonarQube Server en Jenkins

Ruta:

```text
Manage Jenkins > System > SonarQube servers
```

Valores:

```text
Name: Sonarqube-server
Server URL: http://IP_O_DNS_SONARQUBE:9000
Authentication token: credencial creada en Jenkins
```

El nombre debe coincidir con el pipeline:

```groovy
withSonarQubeEnv('Sonarqube-server')
```

---

## 16. Crear Quality Gate en SonarQube

Ruta:

```text
SonarQube > Quality Gates
```

Crear un Quality Gate para el proyecto, por ejemplo:

```text
SmartCampus
```

Configurar condiciones mínimas de calidad y seguridad según el alcance del ambiente de pruebas.

---

## 17. Configurar webhook de SonarQube hacia Jenkins

Para que Jenkins pueda usar `waitForQualityGate`, SonarQube debe notificar el resultado del análisis.

Ruta:

```text
SonarQube > Administration > Configuration > Webhooks
```

URL sugerida:

```text
https://IP_O_DNS_JENKINS/sonarqube-webhook/
```

Si se usa comunicación interna y es alcanzable desde SonarQube:

```text
http://jenkins:8080/sonarqube-webhook/
```

Validar conectividad desde SonarQube hacia Jenkins.

---

## 18. Configurar credencial NVD para OWASP Dependency-Check

Crear o disponer de una API Key de NVD.

Registrar en Jenkins:

```text
Manage Jenkins > Credentials > Global > Add credentials
```

Tipo:

```text
Secret text
```

ID recomendado:

```text
nvd-api-key
```

El pipeline CI-02 espera usar:

```groovy
NVD_API_KEY = credentials('nvd-api-key')
```

---

## 19. Configurar credenciales de Docker Hub

Registrar credenciales:

```text
Manage Jenkins > Credentials > Global > Add credentials
```

Tipo:

```text
Username with password
```

Usar usuario y token/contraseña de Docker Hub.

Revisar que el `credentialsId` usado en el pipeline CD-01 coincida con la credencial creada.

---

## 20. Preparar repositorio del código analizado

El código fuente analizado por el pipeline se encuentra en:

```text
https://github.com/PWN3D777/DevSecOps_SmartCampusUIS.git
```

Los pipelines clonan este repositorio durante la ejecución.

Componentes principales:

```text
admin_microservice/
data_microservice/
```

El pipeline actualizado considera que el artefacto del microservicio de datos queda en:

```text
data_microservice/application/target/*.jar
```

---

## 21. Crear jobs en Jenkins

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

> Los nombres deben coincidir porque `copyArtifacts` y `build job` dependen de ellos.

---

## 22. Crear CI-01 Source Compile - Unit Validation

Crear job:

```text
New Item > CI-01 Source Compile - Unit Validation > Pipeline
```

Pegar el contenido de:

```text
pipelines/CI-01 Source Compile - Unit Validation.groovy
```

Este pipeline hace:

- checkout del repositorio;
- selección de rama;
- selección opcional de commit;
- registro del commit real en `.git_commit`;
- compilación con Maven;
- pruebas unitarias;
- empaquetado de artefactos;
- publicación de resultados JUnit;
- archivado de artefactos.

Validar que archive:

```text
admin_microservice/target/*.jar
data_microservice/application/target/*.jar
.git_commit
**/Dockerfile
```

Ejecutar job y validar resultado.

---

## 23. Crear CI-02 Security - Quality Gate

Crear job:

```text
New Item > CI-02 Security - Quality Gate > Pipeline
```

Pegar el contenido de:

```text
pipelines/CI-02 Security - Quality Gate.groovy
```

Este pipeline hace:

- recuperación de artefactos de CI-01;
- lectura del archivo `.git_commit`;
- checkout del mismo commit construido por CI-01;
- preparación de binarios Java para análisis;
- análisis SonarQube;
- espera y validación explícita del Quality Gate;
- análisis de dependencias con OWASP Dependency-Check;
- publicación del reporte XML;
- archivado de evidencias.

Validar que existan:

```text
JDK-21
maven3
sonar-scanner
Sonarqube-server
Check-DP
nvd-api-key
```

Ejecutar job y validar resultado.

---

## 24. Crear CD-01 Containerization - Deployment

Crear job:

```text
New Item > CD-01 Containerization - Deployment > Pipeline
```

Pegar el contenido de:

```text
pipelines/CD-01 Containerization - Deployment.groovy
```

Este pipeline hace:

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

Validar que Jenkins tenga acceso a Docker:

```bash
docker exec -it jenkins bash
docker ps
docker compose version
```

Ejecutar job y validar resultado.

---

## 25. Crear Full Pipeline

Crear job:

```text
New Item > Full Pipeline > Pipeline
```

Pegar el contenido de:

```text
pipelines/Full Pipeline.groovy
```

Este pipeline ejecuta:

```text
CI-01 Source Compile - Unit Validation
CI-02 Security - Quality Gate
CD-01 Containerization - Deployment
```

Sirve para lanzar el flujo completo desde un solo job.

---

## 26. Ejecutar validación por bloques

Orden recomendado:

```text
1. Ejecutar CI-01.
2. Validar artefactos y JUnit.
3. Ejecutar CI-02.
4. Validar SonarQube, Quality Gate y Dependency-Check.
5. Ejecutar CD-01.
6. Validar imágenes, Trivy, Docker push, despliegue y Smoke Test.
7. Ejecutar Full Pipeline.
```

---

## 27. Validaciones esperadas por pipeline

### 27.1 CI-01

Debe evidenciar:

- checkout correcto;
- commit registrado en `.git_commit`;
- compilación de `admin_microservice`;
- compilación de `data_microservice`;
- pruebas unitarias ejecutadas;
- artefactos `.jar` generados;
- resultados JUnit publicados;
- artefactos archivados.

### 27.2 CI-02

Debe evidenciar:

- recuperación de artefactos desde CI-01;
- checkout del mismo commit;
- compilación previa para binarios de análisis;
- análisis SonarQube ejecutado;
- Quality Gate con estado recibido por Jenkins;
- fallo explícito si el Quality Gate no es `OK`;
- Dependency-Check ejecutado;
- reporte `dependency-check-report.xml` archivado.

### 27.3 CD-01

Debe evidenciar:

- recuperación de artefactos desde CI-01;
- checkout del mismo commit;
- imágenes Docker construidas;
- Trivy ejecutado como contenedor temporal;
- reportes `admin-trivy.txt` y `data-trivy.txt` archivados;
- imágenes publicadas en Docker Hub;
- dependencias desplegadas;
- Telegraf preparado y recreado;
- microservicios desplegados;
- gateway, frontend y grafana desplegados;
- Smoke Test exitoso.

---

## 28. Validar Trivy

Trivy no se despliega como servicio persistente.

Se ejecuta temporalmente desde CD-01 con:

```text
aquasec/trivy:latest
```

El análisis actualizado considera severidades:

```text
UNKNOWN, LOW, MEDIUM, HIGH, CRITICAL
```

Reportes esperados:

```text
trivy-reports/admin-trivy.txt
trivy-reports/data-trivy.txt
```

---

## 29. Validar despliegue con Docker Compose

El pipeline CD-01 despliega progresivamente.

Primero dependencias:

```text
db
mongo
emqx
influxdb
rabbitmq
minio
```

Luego Telegraf:

```text
telegraf
```

Luego microservicios principales:

```text
admin
data
```

Luego servicios complementarios:

```text
gateway
frontend
grafana
```

Validación manual:

```bash
docker compose ps
```

---

## 30. Validar Smoke Test

El Smoke Test debe comprobar que estén corriendo:

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

También valida conectividad HTTP hacia:

```text
data     -> http://IP_DEL_HOST:8082/actuator/health
gateway  -> http://IP_DEL_HOST:8080/
frontend -> http://IP_DEL_HOST:4000/
```

Un código HTTP `000` indica que el servicio no respondió y debe tratarse como fallo.

---

## 31. Evidencias a conservar

Guardar evidencias de:

- consola de Jenkins;
- Stage View;
- resultados JUnit;
- `.git_commit`;
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

---

## 32. Operación básica

### 32.1 Jenkins

Desde `jenkins/`:

```bash
docker compose ps
docker compose logs -f jenkins
docker compose logs -f nginx
docker compose down
docker compose up -d --build
```

### 32.2 SonarQube

Desde `sonarqube/`:

```bash
docker compose ps
docker compose logs -f sonarqube
docker compose restart sonarqube
docker compose down
docker compose up -d
```

---

## 33. Problemas comunes

### 33.1 Jenkins no puede ejecutar Docker

Validar:

```bash
docker exec -it jenkins bash
docker ps
```

Revisar:

- montaje de `/var/run/docker.sock`;
- permisos del usuario Jenkins;
- cliente Docker dentro del contenedor;
- grupo Docker;
- configuración del `compose.yml`.

### 33.2 CI-02 no recupera artefactos

Revisar:

- que CI-01 haya finalizado correctamente;
- que CI-01 haya archivado los artefactos;
- que el nombre del job coincida exactamente;
- que los patrones de `copyArtifacts` coincidan con las rutas reales.

### 33.3 SonarQube no responde

Revisar:

```bash
cd sonarqube
docker compose ps
docker compose logs -f sonarqube
```

Validar:

- puerto 9000;
- conexión a PostgreSQL;
- recursos de memoria;
- variables de entorno;
- estado de los volúmenes.

### 33.4 Quality Gate queda esperando

Revisar:

- webhook de SonarQube hacia Jenkins;
- conectividad SonarQube -> Jenkins;
- URL `/sonarqube-webhook/`;
- nombre `Sonarqube-server`;
- token configurado.

### 33.5 OWASP Dependency-Check falla

Revisar:

- credencial `nvd-api-key`;
- acceso a Internet;
- límites de consulta de NVD;
- configuración `Check-DP`;
- parámetro `--nvdApiDelay`.

### 33.6 Trivy no encuentra imágenes

Revisar:

- que Docker build haya terminado correctamente;
- nombres de imagen;
- tags;
- acceso al socket Docker;
- existencia local de las imágenes.

### 33.7 Smoke Test falla

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

---

## 34. Orden resumido de montaje

```text
1. Preparar servidor Linux.
2. Instalar Docker, Docker Compose y Git.
3. Clonar repositorio de infraestructura.
4. Crear archivos .env locales desde .env.example.
5. Generar certificados internos para Jenkins/Nginx.
6. Levantar Jenkins + Nginx.
7. Entrar a Jenkins y completar configuración inicial.
8. Instalar plugins requeridos.
9. Configurar JDK-21, Maven, Docker, SonarScanner y Dependency-Check.
10. Levantar SonarQube.
11. Crear token de SonarQube.
12. Registrar token de SonarQube en Jenkins Credentials.
13. Registrar servidor Sonarqube-server en Jenkins.
14. Crear Quality Gate.
15. Configurar webhook de SonarQube hacia Jenkins.
16. Registrar credencial nvd-api-key.
17. Registrar credenciales de Docker Hub.
18. Crear jobs CI-01, CI-02, CD-01 y Full Pipeline.
19. Pegar scripts desde pipelines/ en cada job.
20. Ejecutar CI-01.
21. Ejecutar CI-02.
22. Ejecutar CD-01.
23. Ejecutar Full Pipeline.
24. Validar artefactos, reportes, imágenes y Smoke Test.
```

---

## 35. Alcance de esta receta

Esta receta cubre el montaje del ambiente de pruebas usado para validar la arquitectura DevSecOps del proyecto.

Incluye:

- Jenkins;
- Nginx para Jenkins;
- certificados internos;
- SonarQube;
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
