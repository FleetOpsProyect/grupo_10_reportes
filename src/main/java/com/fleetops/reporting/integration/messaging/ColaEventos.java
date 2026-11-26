package com.fleetops.reporting.integration.messaging;

/**
 * Una cola SQS que consume el servicio.
 *
 * @param nombre nombre para los logs
 * @param url    URL de la cola
 * @param dlqUrl URL de su DLQ, a donde van los mensajes inválidos sin reintentos
 * @param fifo   true para reporting_notifications_queue.fifo
 */
public record ColaEventos(String nombre, String url, String dlqUrl, boolean fifo) {

    /**
     * Arma la cola y deriva la DLQ con la convención de nombres del despliegue si no viene configurada:
     * reporting_queue → reporting_queue_dlq y reporting_notifications_queue.fifo →
     * reporting_notifications_queue_dlq.fifo.
     */
    public static ColaEventos de(String nombre, String url, String dlqUrl) {
        boolean fifo = url.endsWith(".fifo");
        String dlq = dlqUrl;
        if (dlq == null || dlq.isBlank()) {
            dlq = fifo ? url.substring(0, url.length() - ".fifo".length()) + "_dlq.fifo" : url + "_dlq";
        }
        return new ColaEventos(nombre, url, dlq, fifo);
    }
}
