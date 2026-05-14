# SonarQube - Análisis de calidad y seguridad de código

Este directorio contiene la configuración utilizada para desplegar SonarQube Community Edition como plataforma de análisis de calidad y seguridad del código fuente dentro del flujo DevSecOps del proyecto **Smart Campus UIS**.

La implementación incluye SonarQube y una base de datos PostgreSQL dedicada dentro del mismo archivo `compose.yml`. PostgreSQL se utiliza como motor de persistencia para almacenar la configuración, proyectos, análisis, usuarios, métricas y resultados generados por SonarQube.

## Índice

- [Rol dentro del proyecto](#rol-dentro-del-proyecto)
- [Arquitectura de red](#arquitectura-de-red)
- [Estructura del directorio](#estructura-del-directorio)
- [Archivo principal](#archivo-principal)
- [Variables recomendadas](#variables-recomendadas)
- [Despliegue](#despliegue)
- [Creación automática de la base de datos](#creación-automática-de-la-base-de-datos)
- [Acceso](#acceso)
- [Base de datos](#base-de-datos)
- [Volúmenes persistentes](#volúmenes-persistentes)
- [Integración con Jenkins](#integración-con-jenkins)
- [Quality Gate](#quality-gate)
- [Operación básica](#operación-básica)
- [Problemas comunes](#problemas-comunes)
- [Consideraciones de seguridad](#consideraciones-de-seguridad)
- [Notas del Compose](#notas-del-compose)

## Rol dentro del proyecto

SonarQube se utiliza en el pipeline CI-02 para realizar análisis estático del código fuente y evaluar condiciones de calidad mediante Quality Gate. Su integración con Jenkins permite que el flujo CI/CD incorpore controles automáticos antes de continuar con las etapas posteriores.

En esta implementación, SonarQube no quedó detrás de Nginx. El servicio se desplegó de forma independiente y se expuso directamente dentro del ambiente de pruebas por el puerto `9000`.

Adicionalmente, SonarQube se conecta a la red utilizada por Jenkins para permitir que los pipelines puedan enviar análisis al servidor SonarQube. PostgreSQL, por su parte, permanece en una red interna independiente y no se expone directamente al host ni a Jenkins.

## Arquitectura de red

La implementación utiliza dos redes Docker:

```text
jenkins_jenkins_net  -> Jenkins + SonarQube
sonarnet             -> SonarQube + PostgreSQL
```

La comunicación queda organizada de la siguiente manera:

```text
Jenkins  -------->  SonarQube  -------->  PostgreSQL
        red CI/CD              red interna de SonarQube
```

De esta forma:

- Jenkins puede comunicarse con SonarQube para ejecutar los análisis del pipeline.
- SonarQube puede comunicarse con PostgreSQL para almacenar su información.
- PostgreSQL no queda expuesto directamente a Jenkins.
- PostgreSQL no publica el puerto `5432` hacia el host.
- La base de datos queda aislada en la red interna `sonarnet`.

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
| `compose.yml` | Define los servicios de SonarQube y PostgreSQL, sus variables de entorno, volúmenes persistentes y redes Docker. |
| `.env.example` | Plantilla recomendada para parametrizar credenciales y valores dependientes del ambiente. |
| `README.md` | Documentación del despliegue y operación básica de SonarQube. |

## Variables recomendadas

Se recomienda utilizar un archivo `.env` para evitar exponer credenciales reales en el repositorio.

Ejemplo de `.env.example`:

```env
POSTGRES_DB=sonar_db
POSTGRES_USER=sonar
POSTGRES_PASSWORD=change_me

SONAR_JDBC_URL=jdbc:postgresql://sonarqube_db:5432/sonar_db
SONAR_JDBC_USERNAME=sonar
SONAR_JDBC_PASSWORD=change_me
```

El archivo `.env` real no debe versionarse.

La variable más importante para la conexión entre SonarQube y PostgreSQL es:

```env
SONAR_JDBC_URL=jdbc:postgresql://sonarqube_db:5432/sonar_db
```

Donde:

| Valor | Descripción |
|---|---|
| `sonarqube_db` | Nombre del servicio PostgreSQL dentro de Docker Compose. |
| `5432` | Puerto interno de PostgreSQL dentro de la red Docker. |
| `sonar_db` | Nombre de la base de datos utilizada por SonarQube. |

El usuario y contraseña configurados en `SONAR_JDBC_USERNAME` y `SONAR_JDBC_PASSWORD` deben coincidir con los valores definidos en `POSTGRES_USER` y `POSTGRES_PASSWORD`.

## Despliegue

Antes de levantar SonarQube, debe existir la red externa utilizada por Jenkins.

Para verificar las redes existentes:

```bash
docker network ls
```

En esta implementación, la red utilizada por Jenkins es:

```text
jenkins_jenkins_net
```

Si Jenkins ya está desplegado, esta red ya debería existir.

Luego, desde el directorio `sonarqube/`, ejecutar:

```bash
docker compose up -d
```

Para validar el estado de los servicios:

```bash
docker compose ps
```

Para revisar los logs de SonarQube:

```bash
docker compose logs -f sonarqube
```

Para revisar los logs de PostgreSQL:

```bash
docker compose logs -f sonarqube_db
```

## Creación automática de la base de datos

En un montaje nuevo desde cero, PostgreSQL crea automáticamente la base de datos y el usuario definidos en el archivo `.env`.

Esto ocurre mediante las variables:

```env
POSTGRES_DB=sonar_db
POSTGRES_USER=sonar
POSTGRES_PASSWORD=change_me
```

Cuando se ejecuta:

```bash
docker compose up -d
```

PostgreSQL inicializa el volumen de datos y crea la base `sonar_db` con el usuario `sonar`.

Es importante tener en cuenta que esta inicialización solo ocurre cuando el volumen de PostgreSQL está vacío o no existe previamente. Si el volumen ya fue creado antes, cambiar las variables del `.env` no recrea automáticamente la base de datos ni el usuario.

Para un montaje completamente limpio se puede usar:

```bash
docker compose down -v
docker compose up -d
```

El comando `docker compose down -v` elimina los volúmenes asociados al Compose, por lo que solo debe usarse cuando no se requiere conservar información previa.

## Acceso

SonarQube queda disponible en:

```text
http://IP_O_DNS_SONARQUBE:9000
```

En esta implementación no se configuró un proxy reverso Nginx para SonarQube.

## Base de datos

SonarQube utiliza PostgreSQL como base de datos para almacenar información de configuración, proyectos, análisis, usuarios y resultados.

El servicio PostgreSQL se define dentro del mismo `compose.yml` con el nombre:

```text
sonarqube_db
```

El contenedor asociado se identifica como:

```text
sonarqube_postgres
```

PostgreSQL no expone el puerto `5432` hacia el host. Su acceso queda limitado a la red interna `sonarnet`, donde también se encuentra SonarQube.

La conexión desde SonarQube se define mediante las variables:

```env
SONAR_JDBC_URL
SONAR_JDBC_USERNAME
SONAR_JDBC_PASSWORD
```

Ejemplo:

```env
SONAR_JDBC_URL=jdbc:postgresql://sonarqube_db:5432/sonar_db
SONAR_JDBC_USERNAME=sonar
SONAR_JDBC_PASSWORD=change_me
```

## Volúmenes persistentes

El servicio utiliza volúmenes Docker para conservar datos entre reinicios.

| Volumen | Uso |
|---|---|
| `sonarqube_conf` | Configuración de SonarQube. |
| `sonarqube_data` | Datos internos del servicio. |
| `sonarqube_logs` | Logs generados por SonarQube. |
| `sonarqube_extensions` | Plugins y extensiones instaladas. |
| `sonarqube_bundled-plugins` | Plugins incluidos en la distribución. |
| `postgres_data` | Datos persistentes de PostgreSQL. |

El volumen más crítico para la base de datos es:

```text
postgres_data
```

Este volumen conserva la información de PostgreSQL incluso si el contenedor se elimina o se recrea.

## Integración con Jenkins

Para que Jenkins pueda enviar análisis a SonarQube, ambos servicios deben compartir una red Docker.

En esta implementación, SonarQube se conecta a la red externa de Jenkins:

```text
jenkins_jenkins_net
```

Desde Jenkins, la URL interna recomendada para acceder a SonarQube es:

```text
http://sonarqube:9000
```

No se debe usar:

```text
http://localhost:9000
```

porque desde el contenedor de Jenkins, `localhost` apunta al propio contenedor de Jenkins y no al contenedor de SonarQube.

Para integrar Jenkins con SonarQube se requiere:

1. Crear un token de usuario o proyecto en SonarQube.
2. Registrar el token en Jenkins Credentials.
3. Configurar el servidor SonarQube en Jenkins.
4. Usar el nombre configurado del servidor dentro del pipeline.
5. Ejecutar el scanner de SonarQube desde el pipeline CI-02.

En el pipeline CI-02 se utiliza el entorno de SonarQube configurado en Jenkins y el scanner para analizar los microservicios.

## Quality Gate

El Quality Gate permite definir condiciones mínimas de calidad antes de continuar con el flujo.

En el pipeline CI-02 se utiliza una etapa de espera del resultado del Quality Gate. Si el resultado no cumple las condiciones definidas, el pipeline puede detenerse.

Esto permite que el flujo DevSecOps incorpore una validación automática sobre el estado del código antes de continuar con etapas posteriores.

## Operación básica

Detener los servicios:

```bash
docker compose down
```

Reiniciar SonarQube:

```bash
docker compose restart sonarqube
```

Reiniciar PostgreSQL:

```bash
docker compose restart sonarqube_db
```

Ver logs recientes de SonarQube:

```bash
docker compose logs --tail=100 sonarqube
```

Ver logs recientes de PostgreSQL:

```bash
docker compose logs --tail=100 sonarqube_db
```

Validar las bases de datos creadas en PostgreSQL:

```bash
docker exec -it sonarqube_postgres psql -U sonar -d postgres -c "\l"
```

Validar conexión a la base de SonarQube:

```bash
docker exec -it sonarqube_postgres psql -U sonar -d sonar_db
```

Salir de la consola de PostgreSQL:

```sql
\q
```

## Problemas comunes

### SonarQube intenta conectarse a una base que no existe

El log puede mostrar algo parecido a:

```text
FATAL: database "sonarqube" does not exist
```

Esto significa que el valor final del JDBC apunta a una base diferente a la que existe en PostgreSQL.

Validar:

```env
SONAR_JDBC_URL=jdbc:postgresql://sonarqube_db:5432/sonar_db
```

Revisar bases creadas:

```bash
docker exec -it sonarqube_postgres psql -U sonar -d postgres -c "\l"
```

### SonarQube no alcanza a PostgreSQL

Validar que ambos servicios estén en la red `sonarnet`:

```bash
docker network inspect sonarqube_sonarnet
```

o el nombre real generado por Docker Compose.

### Jenkins no alcanza SonarQube

Validar que SonarQube esté conectado a `jenkins_jenkins_net`:

```bash
docker network inspect jenkins_jenkins_net
```

Desde Jenkins:

```bash
docker exec -it jenkins-controller bash
curl -I http://sonarqube:9000
```

## Consideraciones de seguridad

- No versionar el archivo `.env` con credenciales reales.
- No exponer PostgreSQL mediante `ports` si no es necesario.
- Mantener PostgreSQL únicamente en la red interna `sonarnet`.
- Usar un usuario dedicado para la base de datos de SonarQube.
- Usar tokens de acceso en lugar de contraseñas personales para integraciones con Jenkins.
- No exponer SonarQube fuera del ambiente de pruebas si no es necesario.
- Restringir el acceso de red al puerto `9000` cuando sea posible.
- Realizar backups periódicos de la base de datos PostgreSQL y de los volúmenes relevantes.

## Notas del Compose

Para que esta documentación sea coherente con el despliegue, la sección de redes del `compose.yml` debe declarar la red de Jenkins como externa:

```yaml
networks:
  jenkins_net:
    external: true
    name: jenkins_jenkins_net

  sonarnet:
    driver: bridge
```

El servicio `sonarqube` debe estar conectado a ambas redes:

```yaml
networks:
  - jenkins_net
  - sonarnet
```

El servicio PostgreSQL debe permanecer únicamente en `sonarnet` y no debe declarar la sección `ports`.

El JDBC recomendado es:

```env
SONAR_JDBC_URL=jdbc:postgresql://sonarqube_db:5432/sonar_db
```
