# Pipelines Jenkins

Este directorio documenta las definiciones de los pipelines utilizados en Jenkins para automatizar el flujo CI/CD con enfoque DevSecOps del proyecto **Smart Campus UIS**.

## Índice

- [Nota sobre la implementación](#nota-sobre-la-implementación)
- [Pipelines documentados](#pipelines-documentados)
- [Flujo general](#flujo-general)
- [CI-01 Source Compile - Unit Validation](#ci-01-source-compile---unit-validation)
  - [Responsabilidades de CI-01](#responsabilidades-de-ci-01)
  - [Herramientas usadas en CI-01](#herramientas-usadas-en-ci-01)
  - [Etapas principales de CI-01](#etapas-principales-de-ci-01)
  - [Artefactos generados por CI-01](#artefactos-generados-por-ci-01)
- [CI-02 Security - Quality Gate](#ci-02-security---quality-gate)
  - [Responsabilidades de CI-02](#responsabilidades-de-ci-02)
  - [Herramientas usadas en CI-02](#herramientas-usadas-en-ci-02)
  - [Etapas principales de CI-02](#etapas-principales-de-ci-02)
  - [Aspectos relevantes de CI-02](#aspectos-relevantes-de-ci-02)
- [CD-01 Containerization - Deployment](#cd-01-containerization---deployment)
  - [Responsabilidades de CD-01](#responsabilidades-de-cd-01)
  - [Herramientas usadas en CD-01](#herramientas-usadas-en-cd-01)
  - [Etapas principales de CD-01](#etapas-principales-de-cd-01)
  - [Servicios validados en el Smoke Test](#servicios-validados-en-el-smoke-test)
- [Uso de Trivy](#uso-de-trivy)
- [Full Pipeline](#full-pipeline)
- [Credenciales requeridas](#credenciales-requeridas)
- [Artefactos y trazabilidad](#artefactos-y-trazabilidad)
- [Nombres esperados en Jenkins](#nombres-esperados-en-jenkins)
- [Consideraciones](#consideraciones)

## Nota sobre la implementación

En esta implementación, los pipelines no fueron almacenados como `Jenkinsfile` dentro del repositorio de la aplicación. Fueron configurados directamente desde la interfaz de Jenkins como `Pipeline script`.

Las definiciones se conservan en este directorio como copia documental para facilitar la trazabilidad, la revisión técnica y la reproducción del flujo en otro ambiente.

## Pipelines documentados

```text
pipelines/
├── CI-01 Source Compile - Unit Validation.groovy
├── CI-02 Security - Quality Gate.groovy
├── CD-01 Containerization - Deployment.groovy
└── Full Pipeline.groovy
```

## Flujo general

```text
Full Pipeline
      │
      ▼
CI-01 Source Compile - Unit Validation
      │
      ▼
CI-02 Security - Quality Gate
      │
      ▼
CD-01 Containerization - Deployment
```

El pipeline `Full Pipeline` ejecuta de forma secuencial los tres jobs principales. Esta separación permite organizar el flujo en bloques funcionales, revisar los resultados por etapa y mantener trazabilidad entre la compilación, el análisis de seguridad y el despliegue.

## CI-01 Source Compile - Unit Validation

Este pipeline corresponde a la primera etapa del flujo de integración continua. Su propósito es obtener una versión identificable del código fuente, compilar los microservicios priorizados, ejecutar pruebas unitarias y generar los artefactos `.jar` que serán utilizados por las etapas posteriores.

### Responsabilidades de CI-01

- Obtener el código fuente desde el repositorio Git.
- Permitir seleccionar rama y commit específico mediante parámetros.
- Registrar el commit construido en el archivo `.git_commit`.
- Compilar los microservicios con Maven.
- Ejecutar pruebas unitarias.
- Empaquetar los artefactos `.jar`.
- Archivar artefactos, Dockerfiles y evidencias de la ejecución.

### Herramientas usadas en CI-01

- JDK 21 configurado en Jenkins como `JDK-21`.
- Maven configurado en Jenkins como `maven3`.
- Plugin JUnit para publicar resultados de pruebas.
- `archiveArtifacts` para conservar artefactos y evidencias.

### Etapas principales de CI-01

| Etapa | Descripción |
|---|---|
| `Git checkout` | Clona el repositorio, permite seleccionar rama y, si se define, hace checkout de un commit específico. También genera el archivo `.git_commit` con el commit real construido. |
| `Code compile` | Ejecuta `mvn -B clean compile` en `admin_microservice` y `data_microservice`. |
| `Unit Test` | Ejecuta `mvn -B test` en los microservicios priorizados. |
| `Build artifact` | Genera los `.jar` requeridos por las etapas posteriores. |
| `post` | Publica resultados JUnit con `allowEmptyResults: true` y archiva artefactos, `.git_commit` y Dockerfiles. |

### Artefactos generados por CI-01

```text
admin_microservice/target/*.jar
data_microservice/application/target/*.jar
.git_commit
**/Dockerfile
```

## CI-02 Security - Quality Gate

Este pipeline corresponde a la etapa de validación de calidad y seguridad del código. Su objetivo es recuperar los artefactos generados por CI-01, analizar exactamente la misma versión del código fuente, ejecutar SonarQube, validar el Quality Gate y realizar análisis de dependencias con OWASP Dependency-Check.

### Responsabilidades de CI-02

- Recuperar artefactos generados en CI-01.
- Leer el commit registrado en `.git_commit`.
- Hacer checkout del mismo commit que fue construido en CI-01.
- Preparar los binarios requeridos por SonarQube para el análisis Java.
- Ejecutar análisis de código con SonarQube.
- Evaluar el Quality Gate configurado en SonarQube.
- Fallar explícitamente el pipeline si el Quality Gate no retorna estado `OK`.
- Ejecutar OWASP Dependency-Check para identificar vulnerabilidades en dependencias.
- Publicar y archivar el reporte de Dependency-Check.
- Conservar artefactos necesarios para trazabilidad.

### Herramientas usadas en CI-02

- JDK 21 configurado en Jenkins como `JDK-21`.
- Maven configurado en Jenkins como `maven3`.
- SonarScanner configurado en Jenkins como `sonar-scanner`.
- Servidor SonarQube configurado en Jenkins como `Sonarqube-server`.
- OWASP Dependency-Check configurado en Jenkins como `Check-DP`.
- Credencial `nvd-api-key` para consultar la base NVD.
- Plugin Copy Artifact para recuperar artefactos desde CI-01.

### Etapas principales de CI-02

| Etapa | Descripción |
|---|---|
| `Get Artifacts from Pipeline 1` | Copia desde CI-01 el archivo `.git_commit` y los artefactos `.jar` generados. |
| `Git checkout` | Lee el commit desde `.git_commit`, clona el repositorio y hace checkout del mismo commit analizado en CI-01. |
| `Prepare binaries for analysis` | Ejecuta compilación sin pruebas para generar las clases requeridas por SonarQube. |
| `Sonarqube Analysis` | Ejecuta `sonar-scanner` sobre los microservicios priorizados y define explícitamente las rutas de binarios Java. |
| `Quality Gate` | Espera el resultado del Quality Gate, imprime el estado recibido y detiene el pipeline con `error` si el estado es diferente de `OK`. |
| `OWASP CHECK` | Ejecuta OWASP Dependency-Check sobre los microservicios, usando la API Key de la NVD. |
| `post` | Archiva artefactos, `.git_commit`, Dockerfiles y `dependency-check-report.xml`. |

### Aspectos relevantes de CI-02

En esta versión del pipeline, CI-02 no analiza un estado genérico de la rama principal, sino el commit exacto generado por CI-01. Esto mejora la trazabilidad porque los resultados de SonarQube y OWASP Dependency-Check quedan asociados a la misma versión del código que fue compilada y empaquetada inicialmente.

También se agregó una etapa de preparación de binarios para que SonarQube pueda analizar correctamente proyectos Java, especialmente en el caso del microservicio de datos, que maneja una estructura modular.

## CD-01 Containerization - Deployment

Este pipeline corresponde a la etapa de construcción de imágenes, escaneo de contenedores, publicación en Docker Hub, despliegue con Docker Compose y verificación básica posterior al despliegue.

### Responsabilidades de CD-01

- Recuperar artefactos generados en CI-01.
- Leer el commit registrado en `.git_commit`.
- Hacer checkout del mismo commit construido en CI-01.
- Construir imágenes Docker para los microservicios priorizados.
- Etiquetar imágenes con el número de build y con `latest`.
- Ejecutar Trivy temporalmente para analizar imágenes Docker.
- Generar reportes de Trivy para cada imagen.
- Autenticarse en Docker Hub mediante credenciales de Jenkins.
- Publicar imágenes en el registro de contenedores.
- Desplegar dependencias, microservicios y servicios complementarios con Docker Compose.
- Preparar y recrear el servicio Telegraf.
- Ejecutar pruebas básicas de humo sobre los servicios desplegados.

### Herramientas usadas en CD-01

- Docker configurado en Jenkins como `docker`.
- Docker Compose disponible en el entorno de ejecución.
- Trivy ejecutado temporalmente mediante la imagen `aquasec/trivy:latest`.
- Credencial de Docker Hub configurada en Jenkins.
- Plugin Copy Artifact para recuperar artefactos desde CI-01.

### Etapas principales de CD-01

| Etapa | Descripción |
|---|---|
| `Get Artifacts from CI-01` | Copia desde CI-01 el archivo `.git_commit` y los artefactos `.jar` generados. |
| `Git checkout` | Lee el commit desde `.git_commit`, clona el repositorio y hace checkout del mismo commit construido previamente. |
| `Docker build` | Construye las imágenes de los microservicios, usando el número de build como tag y generando también el tag `latest`. |
| `Trivy scan` | Ejecuta Trivy como contenedor temporal para analizar las imágenes construidas. |
| `Docker push` | Autentica contra Docker Hub y publica las imágenes generadas. |
| `Deploy dependencies` | Levanta servicios base como `db`, `mongo`, `emqx`, `influxdb`, `rabbitmq` y `minio`. |
| `Prepare telegraf config` | Valida y sincroniza la configuración de Telegraf para que sea visible desde el Docker daemon. |
| `Deploy telegraf` | Elimina y recrea el servicio `telegraf` para aplicar la configuración preparada. |
| `Wait dependencies` | Espera la inicialización inicial de las dependencias. |
| `Deploy admin and data` | Despliega los microservicios principales `admin` y `data`. |
| `Wait core services` | Espera la inicialización de los microservicios principales. |
| `Deploy remaining services` | Despliega `gateway`, `frontend` y `grafana`. |
| `Smoke Test` | Verifica que los contenedores esperados estén en ejecución y que servicios clave respondan por HTTP. |

### Servicios validados en el Smoke Test

El smoke test valida que los siguientes servicios estén en estado `running`:

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

Además, realiza verificaciones HTTP básicas sobre:

```text
data     -> /actuator/health
gateway  -> /
frontend -> /
```

El criterio principal de esta validación es confirmar disponibilidad inicial. Un código HTTP distinto de `000` se interpreta como evidencia de que el servicio responde, aunque no necesariamente valida la lógica funcional completa de la aplicación.

## Uso de Trivy

Trivy no se despliega como servicio persistente dentro de la infraestructura. Su ejecución se realiza temporalmente durante el pipeline CD-01 mediante la imagen oficial:

```text
aquasec/trivy:latest
```

El análisis se realiza sobre las imágenes Docker construidas en la misma ejecución del pipeline.

Los reportes se almacenan en el directorio:

```text
trivy-reports/
```

y luego se archivan como artefactos de Jenkins.

En esta versión del pipeline, Trivy se ejecuta considerando todos los niveles de severidad:

```text
UNKNOWN
LOW
MEDIUM
HIGH
CRITICAL
```

Esto permite conservar una evidencia más completa del estado de seguridad de las imágenes, no limitada únicamente a vulnerabilidades altas o críticas.

## Full Pipeline

Este pipeline orquesta los tres bloques principales del flujo:

1. `CI-01 Source Compile - Unit Validation`
2. `CI-02 Security - Quality Gate`
3. `CD-01 Containerization - Deployment`

Su objetivo es permitir una ejecución completa del flujo desde la integración inicial hasta el despliegue en el ambiente de pruebas.

## Credenciales requeridas

Los pipelines requieren credenciales configuradas en Jenkins.

| Credencial | Uso |
|---|---|
| `nvd-api-key` | API Key usada por OWASP Dependency-Check para consultar la base NVD. |
| Credencial de Docker Hub | Usuario y contraseña/token para publicar imágenes Docker en el registro de contenedores. |
| Token de SonarQube | Token utilizado por Jenkins para enviar análisis a SonarQube. |

Las credenciales no deben almacenarse directamente en los scripts de pipeline.

## Artefactos y trazabilidad

Los pipelines archivan artefactos como:

- archivos `.jar` generados por Maven;
- resultados de pruebas unitarias JUnit;
- archivo `.git_commit` con el commit construido;
- Dockerfiles usados en el proceso;
- reportes de Trivy;
- reporte de OWASP Dependency-Check.

Esto permite relacionar cada ejecución del pipeline con:

- el código fuente analizado;
- el commit específico;
- los artefactos generados;
- las imágenes construidas;
- los resultados de análisis;
- el despliegue realizado.

## Nombres esperados en Jenkins

Los scripts dependen de ciertos nombres configurados en Jenkins.

| Elemento | Nombre esperado |
|---|---|
| JDK | `JDK-21` |
| Maven | `maven3` |
| SonarScanner | `sonar-scanner` |
| Servidor SonarQube | `Sonarqube-server` |
| OWASP Dependency-Check | `Check-DP` |
| API Key NVD | `nvd-api-key` |

Los jobs deben llamarse:

```text
CI-01 Source Compile - Unit Validation
CI-02 Security - Quality Gate
CD-01 Containerization - Deployment
Full Pipeline
```

## Consideraciones

- Aunque los pipelines fueron configurados desde la interfaz de Jenkins, se recomienda conservar estas copias en el repositorio para documentación, trazabilidad y recuperación.
- En una evolución futura del proyecto, los scripts podrían migrarse a `Jenkinsfile` versionados dentro del repositorio de la aplicación.
- Las IP internas, nombres de credenciales y nombres de jobs deben ajustarse según el ambiente donde se reproduzca la implementación.
- No deben incluirse tokens, contraseñas ni llaves dentro de los scripts versionados.
- El uso del socket de Docker desde Jenkins debe tratarse como una consideración técnica de seguridad del montaje, debido a que otorga al controlador capacidad de interactuar con el motor Docker del host.
- El `Smoke Test` implementado corresponde a una validación básica de disponibilidad y no reemplaza pruebas funcionales, pruebas de integración ni pruebas de regresión.

- No deben incluirse tokens, contraseñas ni llaves dentro de los scripts versionados.
- El uso del socket de Docker desde Jenkins debe tratarse como una consideración técnica de seguridad del montaje, debido a que otorga al controlador capacidad de interactuar con el motor Docker del host.
- El `Smoke Test` implementado corresponde a una validación básica de disponibilidad y no reemplaza pruebas funcionales, pruebas de integración ni pruebas de regresión.
