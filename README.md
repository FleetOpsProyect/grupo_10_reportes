# FleetOps · MS Reportes Operativos (Grupo 10)

Microservicio de Reportes Operativos. Expone `GET /reportes/disponibilidad` con los KPIs gerenciales de la flota. Combina dos caminos:

- consultas síncronas en paralelo a 7 microservicios (Scatter-Gather con Circuit Breaker);
- eventos de AWS SNS recibidos en la cola SQS `reporting_queue`, que alimentan una base de lectura en PostgreSQL.

## Stack

| Tema | Herramienta |
|---|---|
| Lenguaje / framework | Java 21 · Spring Boot 4.1 (Spring MVC) |
| Resiliencia | Spring Cloud Circuit Breaker + Resilience4j |
| Mensajería | AWS SDK v2 (SQS) |
| Base de lectura | Neon (PostgreSQL serverless) · Spring Data JPA · Flyway |
| Operación | Actuator (`/health`, `/health/readiness`, `/metrics`) · Micrometer Prometheus · logs JSON |
| Calidad | JUnit 5 · JaCoCo · SonarQube |
| Entrega | Docker · GitHub Actions · GHCR · AWS EC2 |

## Estructura

```
src/main/java/com/fleetops/reporting
├── api/                      Capa de presentación: controladores, DTO, errores, correlation id
├── application/              Lógica de negocio: orquestador Scatter-Gather, período, ensamblado
│   └── kpi/                  Estrategias de cálculo (una por grupo de KPIs)
├── integration/
│   ├── http/                 Clientes de las 7 fuentes + timeout + Circuit Breaker
│   ├── messaging/            Consumidor SQS y handlers de eventos (idempotentes)
│   └── persistence/          Entidades y repositorios de la base de lectura
└── config/                   Beans y propiedades reporting.*
src/main/resources
├── application.yml           Configuración (variables de entorno o .env en local)
└── db/migration/             Migraciones Flyway
deploy/docker-compose.prod.yml  Compose que usa la EC2
docker/localstack/init-aws.sh   SNS/SQS simulados para desarrollo local
```

Cada paquete tiene un `package-info.java` que explica qué va ahí.

## Base de datos (Neon)

La base de lectura vive en [Neon](https://neon.tech), un PostgreSQL serverless que se suspende cuando no hay consultas. Ni en local ni en la EC2 corre un contenedor de PostgreSQL. El proyecto de Neon tiene dos ramas:

| Rama de Neon | Se usa en | Dónde se configura |
|---|---|---|
| `develop` | Desarrollo local (IDE o `docker compose`) | `.env` en la raíz del proyecto |
| `production` | La EC2 (lo que se despliega desde `main`) | `~/reporting/.env` en la EC2 |

Para obtener la cadena de conexión: en Neon, entra a **Connect**, elige la rama, activa *Connection pooling* y copia la cadena en formato **JDBC (Java)**. Luego sepárala en tres variables (ver `.env.example`):

```properties
DATABASE_URL=jdbc:postgresql://HOST-pooler/BASE?sslmode=require&channelBinding=require
DATABASE_USER=USUARIO
DATABASE_PASSWORD=CLAVE
```

- **No dejes `user=` ni `password=` dentro de la URL.** Flyway y Hibernate imprimen la URL al arrancar, así que la clave terminaría en los logs (y en CloudWatch).
- El formato `postgresql://USUARIO:CLAVE@HOST/...` que Neon muestra por defecto **no** funciona: la URL tiene que empezar con `jdbc:postgresql://`.

Las tablas las crea Flyway al arrancar, con las migraciones de `src/main/resources/db/migration`.

## Correr en local

```bash
cp .env.example .env
```

Luego llena `DATABASE_*` con los datos de la rama `develop` de Neon. Después tienes dos opciones.

**Desde el IDE o la terminal.** Spring Boot lee el `.env` de la raíz automáticamente:

```bash
./mvnw spring-boot:run
```

**Con Docker.** Levanta la API y LocalStack, que crea `reporting_queue`, su DLQ y los topics a los que nos suscribimos:

```bash
docker compose up --build
```

La API queda en `http://localhost:3010` (salud en `/health`).

### Si no arranca

| Error | Causa | Solución |
|---|---|---|
| `'url' must start with "jdbc"` | No existe `.env` en la raíz, le falta `DATABASE_URL` o la cadena no está en formato JDBC | Crear el `.env` a partir de `.env.example`. En IntelliJ, el *Working directory* de la configuración de ejecución debe ser la raíz del proyecto. |
| `Connection to localhost:5432 refused` | `DATABASE_URL` apunta a localhost | Usar la URL de la rama `develop` de Neon. |
| `UnknownHostException` / timeout | Host de Neon mal copiado, o sin internet | Revisar el host en Neon → Connect. |
| `password authentication failed` | `DATABASE_USER` o `DATABASE_PASSWORD` no coinciden con la rama de Neon | Copiarlos de nuevo desde Neon → Connect, de la misma rama que la URL. |

## Ramas y pipelines

```
feature/*  ──PR──►  develop  ──PR──►  main  ──►  EC2
hotfix/*   ─────────────────────PR──►  main
```


| Workflow | Cuándo | Qué hace |
|---|---|---|
| `ci.yml` | PR hacia `develop` | `mvn verify` (compilación, pruebas, JaCoCo) + análisis SonarQube esperando el Quality Gate |
| `pr-main.yml` | PR hacia `main` | Solo valida que el PR venga de `develop` o `hotfix/*` |
| `deploy.yml` | Push a `main` (merge) o manual | Construye la imagen, la publica en GHCR y la despliega en la EC2 por SSH; luego verifica `/health/readiness` |

### Protección de ramas (Settings → Branches)

- `develop`: requerir PR y el check **CI / Build, pruebas y SonarQube**.
- `main`: requerir PR y el check **PR a main / Validar rama de origen**.

### Secretos y variables (Settings → Secrets and variables → Actions)

| Nombre | Tipo | Para qué |
|---|---|---|
| `SONAR_TOKEN` | secret | Token de análisis de SonarQube Cloud |
| `EC2_HOST` | secret | IP o DNS público de la EC2 |
| `EC2_USER` | secret | Usuario SSH (ej. `ubuntu` o `ec2-user`) |
| `EC2_SSH_KEY` | secret | Llave privada SSH (contenido del `.pem`) |

### SonarQube Cloud

El proyecto es [FleetOpsProyect_grupo_10_reportes](https://sonarcloud.io/project/overview?id=FleetOpsProyect_grupo_10_reportes), en la organización `fleetopsproyect-1`. El host, la organización y el `projectKey` están en el `pom.xml`; el pipeline solo necesita el secreto `SONAR_TOKEN`.

Hay que desactivar el **Automatic Analysis** del proyecto (Administration → Analysis Method), porque el análisis lo hace el pipeline. Si quedan los dos activos, el análisis del CI falla.


### Preparar la EC2 (una sola vez)

1. Instalar Docker y el plugin `docker compose`, y agregar el usuario al grupo `docker`.
2. Crear `~/reporting/.env` a partir de `.env.example`, con los datos de la rama `production` de Neon y los valores reales.
3. Security Group:
   - entrada al puerto 3010 solo desde el API Gateway y Prometheus;
   - entrada al puerto 22 para el despliegue.
4. Rol IAM de la instancia con `sqs:ReceiveMessage`, `sqs:DeleteMessage` y `sqs:GetQueueAttributes` sobre `reporting_queue`.
