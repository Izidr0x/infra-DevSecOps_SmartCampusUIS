# Jenkins - Infraestructura CI/CD

Este directorio contiene la configuración utilizada para desplegar Jenkins como orquestador principal del flujo CI/CD del proyecto **Smart Campus UIS**. Jenkins se ejecuta como servicio contenerizado y se publica detrás de un proxy reverso Nginx con HTTPS interno.

## Rol dentro del proyecto

Jenkins cumple la función de coordinar las etapas de integración continua, análisis de seguridad, construcción de imágenes y despliegue de servicios. Desde este componente se ejecutan los pipelines definidos para compilar los microservicios, ejecutar pruebas, enviar análisis a SonarQube, realizar verificaciones con OWASP Dependency Check, construir imágenes Docker, analizarlas con Trivy y desplegar la aplicación en el ambiente de pruebas.

En esta implementación, Jenkins quedó como el único servicio publicado detrás de Nginx. SonarQube se desplegó como servicio independiente y Trivy no se ejecutó como servicio permanente, sino de forma temporal durante los pipelines.

## Estructura del directorio

```text
jenkins/
├── compose.yml
├── Dockerfile
├── plugins.txt
├── .env.example
├── casc/
│   └── jenkins.yaml
├── nginx/
│   └── default.conf
└── certs/
    ├── gen_cert.sh
    ├── openssl-ip.cnf.example
    └── README.md
```

## Archivos principales

| Archivo | Descripción |
|---|---|
| `compose.yml` | Define los servicios `jenkins` y `nginx`, sus volúmenes, red interna, variables de entorno y restricciones básicas. |
| `Dockerfile` | Construye la imagen personalizada de Jenkins con paquetes base, plugins y configuración inicial. |
| `plugins.txt` | Lista los plugins requeridos por Jenkins para ejecutar pipelines, usar Git, Maven, SonarQube, OWASP Dependency Check, credenciales y configuración como código. |
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

## Acceso

Jenkins queda disponible a través del proxy Nginx usando HTTPS interno:

```text
https://<IP_DEL_SERVIDOR>/
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

Estos plugins soportan la ejecución de los pipelines CI-01, CI-02, CD-01 y el pipeline de integración.

## Consideraciones de seguridad

- El archivo `.env` no debe versionarse.
- Las credenciales deben gestionarse desde Jenkins Credentials o variables de entorno protegidas.
- Los certificados y llaves privadas reales no deben almacenarse en el repositorio.
- El proxy Nginx debe restringir el acceso únicamente a redes autorizadas.
- El montaje de `/var/run/docker.sock` permite que Jenkins controle Docker en el host, por lo que debe considerarse un permiso sensible.
- El usuario administrador inicial debe cambiarse por una contraseña segura en ambientes reales.
- Se recomienda limitar el acceso administrativo solo a usuarios autorizados.

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

Verificar conectividad HTTPS:

```bash
curl -k https://<IP_DEL_SERVIDOR>/login
```

## Relación con los pipelines

Los pipelines no fueron almacenados como `Jenkinsfile` dentro del repositorio de aplicación. En esta implementación fueron creados directamente desde la interfaz de Jenkins como `Pipeline script`.

Para mantener trazabilidad, las definiciones utilizadas se documentan en el directorio `pipelines/` del repositorio de infraestructura.
