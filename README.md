# Ejecutar en el cliente
```
mvn -X mp-lemon:mp-lemon-plugin:jwtprovider 
```

## Agregando Payara Micro
```
mvn -X mp-lemon:mp-lemon-plugin:add-payara-micro
```

### Con JDBC
```
mvn -X mp-lemon:mp-lemon-plugin:add-payara-micro -DjdbcDriver=mysql -DjdbcProps="Password=sakila:User=sakila:Url=jdbc\:mysql\://localhost/sakila?serverTimezone\=America/Lima:useSSL=false"
```