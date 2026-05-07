# Pipelines Jenkins

Este directorio documenta las definiciones de los pipelines utilizados en Jenkins para automatizar el flujo CI/CD con enfoque DevSecOps del proyecto **Smart Campus UIS**.

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
Integration Pipeline
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

El pipeline de integración ejecuta de forma secuencial los tres jobs principales. Esta separación permite organizar el flujo en bloques funcionales y facilita la revisión de resultados por etapa.

## CI-01 Source Compile - Unit Validation

Este pipeline corresponde a la primera etapa del flujo de integración continua.

### Responsabilidades

- Obtener el código fuente desde el repositorio Git.
- Permitir seleccionar rama y commit específico.
- Compilar los microservicios con Maven.
- Ejecutar pruebas unitarias.
- Empaquetar los artefactos `.jar`.
- Archivar artefactos y evidencias de la ejecución.

### Etapas principales

| Etapa | Descripción |
|---|---|
| `Git checkout` | Clona el repositorio y permite construir una rama o commit específico. |
| `Code compile` | Ejecuta `mvn clean compile` en los microservicios. |
| `Unit Test` | Ejecuta pruebas unitarias con Maven. |
| `Build artifact` | Genera los artefactos `.jar` con `mvn package`. |
| `post` | Publica resultados JUnit y archiva artefactos generados. |

## CI-02 Security - Quality Gate

Este pipeline corresponde a la etapa de validación de calidad y seguridad del código.

### Responsabilidades

- Recuperar artefactos generados en CI-01.
- Ejecutar análisis de código con SonarQube.
- Evaluar el Quality Gate configurado en SonarQube.
- Ejecutar OWASP Dependency Check para identificar vulnerabilidades en dependencias.
- Publicar el reporte de Dependency Check.
- Archivar artefactos necesarios para trazabilidad.

### Etapas principales

| Etapa | Descripción |
|---|---|
| `Get Artifacts from Pipeline 1` | Copia los artefactos generados por CI-01. |
| `Sonarqube Analysis` | Ejecuta `sonar-scanner` para los microservicios. |
| `Quality Gate` | Espera y valida el resultado del Quality Gate de SonarQube. |
| `OWASP CHECK` | Ejecuta OWASP Dependency Check con API Key de NVD. |
| `post` | Archiva artefactos para mantener trazabilidad. |

## CD-01 Containerization - Deployment

Este pipeline corresponde a la etapa de construcción de imágenes, análisis de contenedores, publicación y despliegue.

### Responsabilidades

- Obtener el código fuente del repositorio.
- Recuperar artefactos generados en CI-01.
- Construir imágenes Docker para los microservicios.
- Etiquetar imágenes con el número de build y con `latest`.
- Ejecutar Trivy temporalmente para analizar imágenes Docker.
- Publicar reportes de Trivy.
- Autenticarse en Docker Hub mediante credenciales de Jenkins.
- Publicar imágenes en el registro.
- Desplegar servicios con Docker Compose.
- Ejecutar pruebas básicas de humo sobre servicios desplegados.

### Etapas principales

| Etapa | Descripción |
|---|---|
| `Git checkout` | Clona el repositorio de la aplicación. |
| `Get Artifacts from CI-01` | Copia los `.jar` generados en CI-01. |
| `Docker build` | Construye y etiqueta imágenes Docker. |
| `Trivy scan` | Ejecuta Trivy como contenedor temporal para analizar imágenes. |
| `Docker push` | Publica imágenes en Docker Hub. |
| `Deploy dependencies` | Levanta servicios base como bases de datos y mensajería. |
| `Deploy admin and data` | Despliega los microservicios principales. |
| `Deploy remaining services` | Despliega gateway, frontend y Grafana. |
| `Smoke Test` | Verifica que servicios clave estén corriendo y respondan HTTP. |

## Uso de Trivy

Trivy no se despliega como servicio persistente dentro de la infraestructura. Su ejecución se realiza temporalmente durante el pipeline CD-01 mediante la imagen oficial `aquasec/trivy`.

El análisis se realiza sobre las imágenes Docker construidas en la misma ejecución del pipeline. Los reportes se almacenan en el directorio `trivy-reports/` y luego se archivan como artefactos de Jenkins.

## Integration Pipeline

Este pipeline orquesta los tres bloques principales del flujo:

1. `CI-01 Source Compile - Unit Validation`
2. `CI-02 Security - Quality Gate`
3. `CD-01 Containerization - Deployment`

Su objetivo es permitir una ejecución completa del flujo desde la integración inicial hasta el despliegue en el ambiente de pruebas.

## Credenciales requeridas

Los pipelines requieren credenciales configuradas en Jenkins:

| Credencial | Uso |
|---|---|
| `nvd-api-key` | API Key usada por OWASP Dependency Check para consultar la base NVD. |
| Credencial de Docker Hub | Usuario y contraseña/token para publicar imágenes Docker. |
| Token de SonarQube | Token utilizado por Jenkins para enviar análisis a SonarQube. |

Las credenciales no deben almacenarse directamente en los scripts de pipeline.

## Artefactos y trazabilidad

Los pipelines archivan artefactos como:

- archivos `.jar` generados por Maven;
- resultados de pruebas unitarias JUnit;
- archivo `.git_commit` con el commit construido;
- Dockerfiles usados en el proceso;
- reportes de Trivy;
- reporte de OWASP Dependency Check.

Esto permite relacionar cada ejecución del pipeline con el código fuente, los artefactos generados y los resultados de análisis.

## Consideraciones

- Aunque los pipelines fueron configurados desde la interfaz de Jenkins, se recomienda conservar estas copias en el repositorio para documentación y recuperación.
- En una evolución futura del proyecto, los scripts podrían migrarse a `Jenkinsfile` versionados dentro del repositorio de la aplicación.
- Las IP internas, nombres de credenciales y nombres de jobs deben ajustarse según el ambiente donde se reproduzca la implementación.
- No deben incluirse tokens, contraseñas ni llaves dentro de los scripts versionados.
