package com.fleetops.reporting.integration.messaging;

import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;

/**
 * Sobre común de todos los eventos de FleetOps (Excel definitivo).
 *
 * @param eventId       identificador único del evento (clave de idempotencia)
 * @param eventType     tipo, por ejemplo vehicle_status_changed_event
 * @param occurredAt    cuándo ocurrió en el servicio que lo publica
 * @param source        servicio que lo publica
 * @param correlationId correlación de la operación que lo originó (puede faltar)
 * @param data          payload propio del evento; los campos desconocidos se ignoran
 */
public record SobreEvento(String eventId, String eventType, OffsetDateTime occurredAt, String source,
                          String correlationId, JsonNode data) {
}
