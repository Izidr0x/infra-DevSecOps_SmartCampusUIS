# Certificados para Nginx/Jenkins

Este directorio contiene los archivos auxiliares usados para generar certificados autofirmados para el proxy Nginx que publica Jenkins mediante HTTPS interno.

## Rol dentro del proyecto

El certificado se utiliza para permitir acceso HTTPS a Jenkins dentro del ambiente de pruebas. En esta implementación, Jenkins quedó detrás de Nginx y el proxy utiliza el certificado generado en este directorio para terminar TLS.

## Estructura sugerida

```text
certs/
├── gen_cert.sh
├── openssl-ip.cnf.example
└── README.md
```

Los archivos `jenkins.key`, `jenkins.crt`, `.pem` u otros certificados reales no deben subirse al repositorio.

## Generación del certificado

El script `gen_cert.sh` puede usarse para generar un certificado autofirmado:

```bash
chmod +x gen_cert.sh
./gen_cert.sh
```

El script genera dos archivos:

```text
jenkins.key
jenkins.crt
```

Estos archivos son usados por Nginx en la configuración del proxy reverso.

## Configuración de OpenSSL

El archivo `openssl-ip.cnf.example` debe copiarse y ajustarse según la IP o nombre DNS del servidor:

```bash
cp openssl-ip.cnf.example openssl-ip.cnf
```

Ejemplo de configuración para una IP interna:

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

Es importante que el valor de `subjectAltName` coincida con la IP o dominio usado para acceder a Jenkins. Si no coincide, el navegador puede mostrar advertencias adicionales o rechazar la conexión.

## Uso en Nginx

La configuración de Nginx referencia los archivos generados de la siguiente forma:

```nginx
ssl_certificate     /etc/nginx/certs/jenkins.crt;
ssl_certificate_key /etc/nginx/certs/jenkins.key;
```

En el `compose.yml`, el directorio local de certificados se monta dentro del contenedor de Nginx:

```yaml
volumes:
  - ./certs:/etc/nginx/certs:ro,Z
```

## Archivos que no deben versionarse

Agregar al `.gitignore` del repositorio:

```gitignore
*.key
*.crt
*.pem
jenkins/certs/*.key
jenkins/certs/*.crt
jenkins/certs/*.pem
```

## Consideraciones de seguridad

- La llave privada `jenkins.key` no debe subirse al repositorio.
- Si una llave privada fue publicada accidentalmente, debe considerarse comprometida y regenerarse.
- Los certificados autofirmados son aceptables para un ambiente de pruebas, pero en producción se recomienda usar certificados emitidos por una autoridad confiable o por una CA interna controlada.
- El acceso a los archivos de llave privada debe limitarse al usuario o servicio que los necesita.

## Validación

Para revisar el certificado desde un cliente:

```bash
openssl s_client -connect <IP_DEL_SERVIDOR>:443 -showcerts
```

Para probar el acceso HTTPS ignorando la validación de confianza del certificado:

```bash
curl -k https://<IP_DEL_SERVIDOR>/login
```
