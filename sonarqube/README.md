# SonarQube - Análisis de calidad y seguridad de código

Este directorio contiene la configuración utilizada para desplegar SonarQube Community Edition como plataforma de análisis de calidad y seguridad del código fuente dentro del flujo DevSecOps del proyecto **Smart Campus UIS**.

## Rol dentro del proyecto

SonarQube se utiliza en el pipeline CI-02 para realizar análisis estático del código fuente y evaluar condiciones de calidad mediante Quality Gate. Su integración con Jenkins permite que el flujo CI/CD incorpore controles automáticos antes de continuar con las etapas posteriores.

En esta implementación, SonarQube no quedó detrás de Nginx. El servicio se desplegó de forma independiente y se expuso directamente dentro del ambiente de pruebas por el puerto `9000`.

## Estructura del directorio

```text
sonarqube/
├── compose.yml
├── .env.example
└── README.md
```

## Archivo principal

| Archivo | Descripción |
|---|---|
| `compose.yml` | Define el servicio de SonarQube, sus variables de conexión a PostgreSQL, volúmenes persistentes y red Docker. |
| `.env.example` | Plantilla recomendada para parametrizar valores sensibles o dependientes del ambiente. |
| `README.md` | Documentación del despliegue y operación básica de SonarQube. |

## Variables recomendadas

Aunque el archivo `compose.yml` puede contener valores directos, se recomienda usar un archivo `.env` para evitar exponer credenciales reales en el repositorio.

Ejemplo de `.env.example`:

```env
SONAR_JDBC_URL=jdbc:postgresql://192.168.x.x:5432/sonar_db
SONAR_JDBC_USERNAME=sonar
SONAR_JDBC_PASSWORD=change_me
```

El archivo `.env` real no debe versionarse.

## Despliegue

Desde el directorio `sonarqube/`:

```bash
docker compose up -d
```

Para validar el estado del servicio:

```bash
docker compose ps
```

Para revisar logs:

```bash
docker compose logs -f sonarqube
```

## Acceso

SonarQube queda disponible en:

```text
http://<IP_DEL_SERVIDOR>:9000
```

En esta implementación no se configuró un proxy reverso Nginx para SonarQube.

## Base de datos

SonarQube utiliza PostgreSQL como base de datos para almacenar información de configuración, proyectos, análisis y resultados.

La conexión se define mediante las variables:

```env
SONAR_JDBC_URL
SONAR_JDBC_USERNAME
SONAR_JDBC_PASSWORD
```

Se recomienda que la base de datos tenga un usuario dedicado para SonarQube y que las credenciales no se almacenen directamente en archivos versionados.

## Volúmenes persistentes

El servicio utiliza volúmenes Docker para conservar datos entre reinicios:

| Volumen | Uso |
|---|---|
| `sonarqube_conf` | Configuración de SonarQube. |
| `sonarqube_data` | Datos internos del servicio. |
| `sonarqube_logs` | Logs generados por SonarQube. |
| `sonarqube_extensions` | Plugins y extensiones instaladas. |
| `sonarqube_bundled-plugins` | Plugins incluidos en la distribución. |

## Integración con Jenkins

Para que Jenkins pueda enviar análisis a SonarQube, se requiere:

1. Crear un token de usuario o proyecto en SonarQube.
2. Registrar el token en Jenkins Credentials.
3. Configurar el servidor SonarQube en Jenkins.
4. Usar el nombre configurado del servidor dentro del pipeline.

En el pipeline CI-02 se utiliza el entorno de SonarQube configurado en Jenkins y el scanner para analizar los microservicios.

## Quality Gate

El Quality Gate permite definir condiciones mínimas de calidad antes de continuar con el flujo. En el pipeline CI-02 se usa una etapa de espera del resultado del Quality Gate. Si el resultado no cumple las condiciones definidas, el pipeline puede detenerse.

## Operación básica

Detener SonarQube:

```bash
docker compose down
```

Reiniciar SonarQube:

```bash
docker compose restart sonarqube
```

Ver logs recientes:

```bash
docker compose logs --tail=100 sonarqube
```

## Consideraciones de seguridad

- No versionar credenciales reales de PostgreSQL.
- No exponer SonarQube fuera del ambiente de pruebas si no es necesario.
- Usar tokens de acceso en lugar de contraseñas personales para integraciones.
- Restringir el acceso de red al puerto `9000` cuando sea posible.
- Realizar backups periódicos de la base de datos PostgreSQL y de los volúmenes relevantes.
