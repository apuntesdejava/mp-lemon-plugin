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
mvn -X mp-lemon:mp-lemon-plugin:add-payara-micro -DjdbcDriver=mysql -DjdbcUrl="jdbc://localhost/sakila?serverTimezone=America/lima" -DjdbcUsername=sakila -DjdbcPassword=sakila
```