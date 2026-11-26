package com.fleetops.reporting.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON común de error de FleetOps (HU-01, criterio 9).
 *
 * @param codigo        código en MAYÚSCULAS_CON_GUION_BAJO
 * @param mensaje       texto para personas, sin trazas
 * @param timestamp     ISO 8601 en hora de Colombia
 * @param ruta          ruta pedida
 * @param correlationId correlación de la petición
 */
public record ErrorRespuesta(
        @JsonProperty("codigo") String codigo,
        @JsonProperty("mensaje") String mensaje,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("ruta") String ruta,
        @JsonProperty("correlation_id") String correlationId) {
}
