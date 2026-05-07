openssl req -x509 -nodes -days 825 \
  -newkey rsa:4096 \
  -keyout jenkins.key \
  -out jenkins.crt \
  -config openssl-ip.cnf
