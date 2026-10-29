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
| Base de lectura | PostgreSQL 16 · Spring Data JPA · Flyway |
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
├── application.yml           Configuración (todo por variables de entorno)
└── db/migration/             Migraciones Flyway
deploy/docker-compose.prod.yml  Compose que usa la EC2
docker/localstack/init-aws.sh   SNS/SQS simulados para desarrollo local
```

Cada paquete tiene un `package-info.java` que explica qué va ahí.

## Correr en local

```bash
cp .env.example .env
docker compose up --build
```

Esto levanta la API en `http://localhost:3010`, PostgreSQL y LocalStack. LocalStack crea `reporting_queue`, su DLQ y los topics a los que nos suscribimos.

Si quieres correr solo la aplicación desde el IDE, necesitas PostgreSQL arriba (`docker compose up postgres`). Después:

```bash
./mvnw spring-boot:run
```

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
| `SONAR_TOKEN` | secret | Token de análisis de SonarQube |
| `SONAR_HOST_URL` | secret | URL del servidor SonarQube (o `https://sonarcloud.io`) |
| `SONAR_ORGANIZATION` | variable | Solo si usan SonarQube Cloud |
| `EC2_HOST` | secret | IP o DNS público de la EC2 |
| `EC2_USER` | secret | Usuario SSH (ej. `ubuntu` o `ec2-user`) |
| `EC2_SSH_KEY` | secret | Llave privada SSH (contenido del `.pem`) |

El `projectKey` de Sonar está en el `pom.xml` (`fleetops-reporting`). Si en SonarQube el proyecto tiene otra clave, hay que cambiarla allí.


### Preparar la EC2 (una sola vez)

1. Instalar Docker y el plugin `docker compose`, y agregar el usuario al grupo `docker`.
2. Crear `~/reporting/.env` a partir de `.env.example`, con los valores reales.
3. Security Group:
   - entrada al puerto 3010 solo desde el API Gateway y Prometheus;
   - entrada al puerto 22 para el despliegue.
4. Rol IAM de la instancia con `sqs:ReceiveMessage`, `sqs:DeleteMessage` y `sqs:GetQueueAttributes` sobre `reporting_queue`.
