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
