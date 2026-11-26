package com.fleetops.reporting.integration.messaging;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Convierte el cuerpo de un mensaje SQS en {@link SobreEvento}. Las suscripciones usan raw message delivery, así
 * que el cuerpo es directamente el JSON del evento (sin el sobre de SNS).
 */
@Component
public class LectorSobreEvento {

    private final JsonMapper json;

    public LectorSobreEvento(JsonMapper json) {
        this.json = json;
    }

    public SobreEvento leer(String cuerpo) {
        JsonNode nodo;
        try {
            nodo = json.readTree(cuerpo);
        } catch (JacksonException e) {
            throw new MensajeInvalidoException("el cuerpo no es JSON válido", e);
        }
        if (nodo == null || !nodo.isObject()) {
            throw new MensajeInvalidoException("el cuerpo no es un objeto JSON");
        }
        JsonNode data = nodo.get("data");
        if (data == null || !data.isObject()) {
            throw new MensajeInvalidoException("falta el objeto data");
        }
        return new SobreEvento(
                texto(nodo, "event_id", true),
                texto(nodo, "event_type", true),
                fecha(texto(nodo, "occurred_at", true)),
                texto(nodo, "source", false),
                texto(nodo, "correlation_id", false),
                data);
    }

    private static String texto(JsonNode nodo, String campo, boolean obligatorio) {
        JsonNode valor = nodo.get(campo);
        if (valor == null || valor.isNull() || valor.asString().isBlank()) {
            if (obligatorio) {
                throw new MensajeInvalidoException("falta el campo obligatorio " + campo);
            }
            return null;
        }
        return valor.asString();
    }

    private static OffsetDateTime fecha(String valor) {
        try {
            return OffsetDateTime.parse(valor);
        } catch (DateTimeParseException e) {
            throw new MensajeInvalidoException("occurred_at no es una fecha ISO 8601 con zona", e);
        }
    }
}
