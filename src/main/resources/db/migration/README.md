# Migraciones Flyway

Aquí van los scripts de la base de lectura, con el formato `V<n>__<descripcion>.sql`. Por ejemplo:

- `V1__eventos_procesados.sql`
- `V2__proyeccion_documentos.sql`

Nunca se edita una migración que ya se aplicó. Si hay que cambiar algo, se crea una nueva.
