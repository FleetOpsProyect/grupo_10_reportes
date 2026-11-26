package com.fleetops.reporting.config;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades {@code reporting.*} de application.yml. Todas llegan por variables de entorno (ver .env.example).
 *
 * @param timeout         tiempo máximo de cada llamada a una fuente (REPORTES_TIMEOUT_MS)
 * @param circuitBreaker  umbral de fallos y espera del Circuit Breaker de cada fuente
 * @param cacheTtl        cuánto se guarda un reporte completo (REPORTES_CACHE_TTL_S; 0 la desactiva)
 * @param eventosDesde    fecha AAAA-MM-DD desde la que están activas las suscripciones (REPORTES_EVENTOS_DESDE)
 * @param fuentes         URL base de cada microservicio fuente, por nombre de fuente
 * @param sqs             colas de eventos
 */
@ConfigurationProperties("reporting")
public record ReportingProperties(
        Duration timeout,
        CircuitBreaker circuitBreaker,
        Duration cacheTtl,
        String eventosDesde,
        Map<String, String> fuentes,
        Sqs sqs) {

    public ReportingProperties {
        timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
        circuitBreaker = circuitBreaker == null ? new CircuitBreaker(5, Duration.ofSeconds(30)) : circuitBreaker;
        cacheTtl = cacheTtl == null ? Duration.ZERO : cacheTtl;
        fuentes = fuentes == null ? Map.of() : Map.copyOf(fuentes);
        sqs = sqs == null ? new Sqs(false, null, null, null, null, "us-east-1", null, 20, 10) : sqs;
    }

    /** Fecha de inicio de las suscripciones, si está configurada. */
    public Optional<LocalDate> fechaEventosDesde() {
        if (eventosDesde == null || eventosDesde.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(LocalDate.parse(eventosDesde.trim()));
    }

    /**
     * @param fallos fallos consecutivos que abren el circuito (REPORTES_CB_FALLOS)
     * @param espera tiempo con el circuito abierto antes de dejar pasar una llamada de prueba (REPORTES_CB_ESPERA_S)
     */
    public record CircuitBreaker(int fallos, Duration espera) {
    }

    /**
     * @param enabled                 activa los consumidores (SQS_ENABLED)
     * @param queueUrl                cola estándar reporting_queue
     * @param notificationsQueueUrl   cola FIFO reporting_notifications_queue.fifo
     * @param dlqUrl                  DLQ de la cola estándar; si está vacía se deriva de queueUrl
     * @param notificationsDlqUrl     DLQ de la cola FIFO; si está vacía se deriva de notificationsQueueUrl
     * @param region                  región de AWS
     * @param endpoint                endpoint alterno (LocalStack); vacío en AWS
     * @param waitSeconds             long polling
     * @param maxMessages             mensajes por lectura (máximo 10)
     */
    public record Sqs(
            boolean enabled,
            String queueUrl,
            String notificationsQueueUrl,
            String dlqUrl,
            String notificationsDlqUrl,
            String region,
            String endpoint,
            int waitSeconds,
            int maxMessages) {
    }
}
