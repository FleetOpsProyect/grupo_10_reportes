# Pruebas

Las pruebas siguen la misma estructura de paquetes que `src/main/java`.

- **Unitarias** (JUnit 5 + Mockito): sin red, sin base de datos y sin AWS. Los clientes HTTP y SQS se simulan.
- **De controlador** (`@WebMvcTest`): verifican el código HTTP y el campo `codigo` del error, no el texto del mensaje.
- **Fixtures de eventos:** los payloads de ejemplo del catálogo de eventos (Excel V2) van en `src/test/resources/eventos/`.

Para correrlas: `./mvnw verify`. El reporte de cobertura de JaCoCo queda en `target/site/jacoco/` y es el que lee SonarQube.
