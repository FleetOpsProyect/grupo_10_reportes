# FleetOps · MS Reportes Operativos (Grupo 10)

Microservicio de Reportes Operativos. Expone `GET /reportes/disponibilidad` con los KPIs gerenciales de la flota. Combina dos caminos:

- consultas síncronas en paralelo a 7 microservicios (Scatter-Gather con Circuit Breaker);
- eventos de AWS SNS recibidos en la cola SQS `reporting_queue`, que alimentan una base de lectura en PostgreSQL (Neon).

## Stack

| Tema | Herramienta |
|---|---|
| Lenguaje / framework | Java 21 · Spring Boot 4.1 (Spring MVC) |
| Resiliencia | Spring Cloud Circuit Breaker + Resilience4j |
| Mensajería | AWS SDK v2 (SQS) |
| Base de lectura | Neon (PostgreSQL serverless) · Spring Data JPA · Flyway |
| Operación | Actuator (`/health`, `/health/readiness`, `/metrics`) · Micrometer Prometheus · logs JSON |
| Calidad | JUnit 5 · JaCoCo · SonarQube |
| Entrega | Docker · GitHub Actions · GHCR (GitHub Packages) · AWS EC2 |

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
| `production` | La EC2 (lo que se despliega desde `main`) | Secretos del environment `production` en GitHub; el deploy genera `~/reporting/.env` |

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
| `ci.yml` | PR hacia `develop`, o manual sobre `develop` | `mvn verify` (compilación, pruebas, JaCoCo) + análisis SonarQube. En el PR el Quality Gate bloquea el merge; la ejecución manual (*Actions → CI → Run workflow*) actualiza el Overview de `develop` en SonarCloud |
| `pr-main.yml` | PR hacia `main` | Solo valida que el PR venga de `develop` o `hotfix/*` |
| `deploy.yml` | Push a `main` (merge) o manual | Construye la imagen, la publica en GHCR (GitHub Packages) y la despliega en la EC2 por SSH junto con Prometheus, Grafana y node_exporter; luego verifica `/health/readiness` |
| `pr-agent.yml` | PR hacia `develop`, o comentario `/…` en un PR | Revisión con IA (Qodo PR-Agent + Gemini): `/review` y `/improve` automáticos al abrir el PR. No bloquea el merge |

### Protección de ramas (Settings → Branches)

- `develop`: requerir PR y el check **CI / Build, pruebas y SonarQube**.
- `main`: requerir PR y el check **PR a main / Validar rama de origen**.

### Secretos y variables

**Del repositorio** (Settings → Secrets and variables → Actions → *Repository secrets*):

| Nombre | Tipo | Para qué |
|---|---|---|
| `SONAR_TOKEN` | secret | Token de análisis de SonarQube Cloud |
| `GEMINI_API_KEY` | secret | API key de Google AI Studio para Qodo PR-Agent |
| `QODO_MODEL` | variable (opcional) | Modelo de Gemini para PR-Agent, por defecto `gemini/gemini-3.8-flash` (con `gemini/gemini-3.5-flash` de respaldo si el principal da 503) |

**Del environment `production`** (Settings → Environments → `production`). Solo los ve el job de despliegue:

| Nombre | Tipo | Para qué |
|---|---|---|
| `EC2_HOST` | secret | Elastic IP de la EC2 |
| `EC2_USER` | secret | Usuario SSH (`ubuntu`) |
| `EC2_SSH_KEY` | secret | Llave privada SSH (contenido completo del `.pem`) |
| `DATABASE_URL` | secret | URL JDBC de la rama `production` de Neon, **sin** `user=` ni `password=` |
| `DATABASE_USER` | secret | Usuario de esa rama de Neon |
| `DATABASE_PASSWORD` | secret | Clave de esa rama de Neon |
| `GRAFANA_ADMIN_PASSWORD` | secret | Clave del usuario `admin` de Grafana en la EC2 |
| `REPORTES_TIMEOUT_MS`, `REPORTES_CB_FALLOS`, `REPORTES_CB_ESPERA_S`, `REPORTES_CACHE_TTL_S` | variables (opcionales) | Si no se definen, se usan los valores por defecto de `application.yml` |
| `FUENTE_*_URL` (7) | variables (opcionales) | URLs internas de los otros microservicios, a medida que se desplieguen |
| `SQS_ENABLED`, `SQS_QUEUE_URL`, `AWS_REGION` | variables (opcionales) | Consumo de eventos, cuando se active |

En cada deploy, `deploy.yml` arma `~/reporting/.env` con estos valores, lo copia a la EC2 con permisos `600` y lo borra del runner. El paso falla con un mensaje claro si falta un secreto de la base de datos o si `DATABASE_URL` trae credenciales. Para cambiar un valor: se edita en GitHub y se relanza el workflow (*Actions → Deploy EC2 → Run workflow*). Nunca se edita el `.env` a mano en la EC2, porque el siguiente deploy lo sobrescribe.

### SonarQube Cloud

El proyecto es [FleetOpsProyect_grupo_10_reportes](https://sonarcloud.io/project/overview?id=FleetOpsProyect_grupo_10_reportes), en la organización `fleetopsproyect-1`. El host, la organización y el `projectKey` están en el `pom.xml`; el pipeline solo necesita el secreto `SONAR_TOKEN`.

Hay que desactivar el **Automatic Analysis** del proyecto (Administration → Analysis Method), porque el análisis lo hace el pipeline. Si quedan los dos activos, el análisis del CI falla.

### Revisión con IA (Qodo PR-Agent)

[PR-Agent](https://github.com/the-pr-agent/pr-agent) corre como GitHub Action y usa **Gemini** de Google AI Studio. No reemplaza a SonarCloud: Sonar mide reglas estáticas y cobertura, y PR-Agent revisa la lógica del cambio y deja comentarios en español.

- Al **abrir** un PR hacia `develop` publica automáticamente una revisión (`/review`) y sugerencias (`/improve`).
- En cualquier PR se le pueden pedir cosas comentando: `/review`, `/improve`, `/describe` (escribe la descripción del PR) o `/ask "¿pregunta?"`.
- Los comandos por comentario solo funcionan cuando `pr-agent.yml` ya está en `main`, porque GitHub ejecuta los workflows de comentarios desde la rama por defecto.

La API key se crea en [Google AI Studio → Get API key](https://aistudio.google.com/apikey) y va en el secreto `GEMINI_API_KEY`. Si Google cambia o retira el modelo, basta con definir la variable `QODO_MODEL` (por ejemplo `gemini/gemini-2.5-flash`).

## Observabilidad (Prometheus + Grafana)

En la EC2, junto a la API, corren:

| Servicio | Qué hace | Acceso |
|---|---|---|
| Prometheus | Recoge `/metrics` de la API cada 15 s y guarda 7 días | Solo desde la EC2 (`127.0.0.1:9090`); por túnel SSH: `ssh -i group_10.pem -L 9090:localhost:9090 ubuntu@IP` y luego `http://localhost:9090` |
| node_exporter | Métricas del host: CPU, RAM y disco | Interno (lo lee Prometheus) |
| Grafana | Dashboard **FleetOps · Reportes Operativos (G10)**, ya provisionado | `http://ELASTIC_IP:3000`, usuario `admin` y la clave del secreto `GRAFANA_ADMIN_PASSWORD` |

La configuración está versionada en `deploy/monitoring/`: el `prometheus.yml`, la fuente de datos de Grafana y el dashboard en JSON. El dashboard no se edita desde la UI de Grafana: se cambia el JSON en el repo y se despliega.

En local: `docker compose --profile monitoring up --build`, con Grafana en `http://localhost:3000` (`admin`/`admin`).

#### Memoria en la `t3.small` (2 GiB)

Los límites salen del consumo medido con `docker stats` en la EC2:

| Contenedor | Consumo medido | Límite |
|---|---|---|
| API (JVM con `MaxRAMPercentage=60`) | ~250 MB | 640 MB |
| Grafana (sin plugins preinstalados) | ~250 MB con plugins; menos sin ellos | 512 MB |
| Prometheus | ~35 MB | 192 MB |
| node_exporter | ~12 MB | 64 MB |

La EC2 tiene además 2 GB de swap como red de seguridad: es más lento que la RAM, pero evita que el kernel mate procesos en un pico. Los logs de Docker rotan en 3 archivos de 10 MB. Para revisar el consumo:

```bash
free -h && docker stats --no-stream
```

Si un contenedor pasa del 85–90 % de su límite, hay que subírselo. Si la RAM total no alcanza, se pasa a `t3.medium` (4 GiB): se detiene la instancia, se cambia el tipo y la Elastic IP se mantiene.


### Preparar la EC2 (una sola vez)

1. **Crear la instancia:** Ubuntu, `t3.small`, 20 GiB gp3, con un key pair (`.pem`). En *Advanced details → User data* va este script, que instala Docker y deja lista la carpeta del despliegue:

   ```bash
   #!/bin/bash
   apt-get update -y
   apt-get install -y docker.io docker-compose-v2 curl
   systemctl enable --now docker
   usermod -aG docker ubuntu
   mkdir -p /home/ubuntu/reporting
   chown ubuntu:ubuntu /home/ubuntu/reporting
   # 2 GB de swap (Ubuntu en EC2 no trae)
   fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
   echo '/swapfile none swap sw 0 0' >> /etc/fstab
   ```

   En una instancia que ya existe, el swap se crea una vez por SSH:

   ```bash
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile && echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   ```

2. **Asignarle una Elastic IP**, para que `EC2_HOST` no cambie al detener e iniciar la instancia.
3. **Configurar el Security Group:**
   - puerto 22 (SSH) desde `0.0.0.0/0`, porque GitHub Actions entra desde IPs que cambian. Solo se accede con la llave `.pem`.
   - puerto 3010 (API) desde `0.0.0.0/0` mientras se prueba; luego restringirlo al API Gateway.
   - puerto 3000 (Grafana) solo desde las IPs del equipo (`/32`), no desde `0.0.0.0/0`.
4. **Cargar los secretos del environment `production`** en GitHub (ver la tabla de arriba). El `.env` de la EC2 lo genera el deploy, no se crea a mano.
5. **Rol IAM de la instancia** con `sqs:ReceiveMessage`, `sqs:DeleteMessage` y `sqs:GetQueueAttributes` sobre `reporting_queue` (cuando se active el consumo de eventos).

### Seguridad de los workflows

- Todas las GitHub Actions están fijadas al **SHA de un commit** (`uses: owner/action@<sha> # vX.Y.Z`), no a una etiqueta. Una etiqueta se puede mover a otro código; un SHA no. SonarCloud lo exige: una action sin SHA baja el *Security Rating* y rompe el Quality Gate.
- **Dependabot** (`.github/dependabot.yml`) revisa cada semana si hay versiones nuevas y abre un PR hacia `develop` con el SHA actualizado. Los PR de Dependabot no reciben los secretos normales del repo, así que para que su CI pase hay que cargar `SONAR_TOKEN` también en *Settings → Secrets and variables → Dependabot*.
- Dentro de un `run:` no se usa `${{ … }}` directamente: los valores entran por `env:`, para evitar inyección de comandos.
