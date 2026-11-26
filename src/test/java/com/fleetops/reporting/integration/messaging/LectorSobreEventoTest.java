package com.fleetops.reporting.integration.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class LectorSobreEventoTest {

    private final LectorSobreEvento lector = new LectorSobreEvento(JsonMapper.builder().build());

    @Test
    void leeElSobreComunEIgnoraCamposDesconocidos() {
        SobreEvento evento = lector.leer("""
                {"event_id":"e-1","event_type":"fuel_record_registered_event",
                 "occurred_at":"2026-11-26T10:00:00-05:00","source":"combustible","correlation_id":"c-9",
                 "campo_nuevo":1,"data":{"vehicle_id":"V-1","total_cost":120000.5}}""");

        assertThat(evento.eventId()).isEqualTo("e-1");
        assertThat(evento.eventType()).isEqualTo("fuel_record_registered_event");
        assertThat(evento.occurredAt().getOffset().getTotalSeconds()).isEqualTo(-5 * 3600);
        assertThat(evento.correlationId()).isEqualTo("c-9");
        assertThat(evento.data().get("vehicle_id").asString()).isEqualTo("V-1");
    }

    @Test
    void elCorrelationIdEsOpcional() {
        SobreEvento evento = lector.leer("""
                {"event_id":"e-1","event_type":"t","occurred_at":"2026-11-26T15:00:00Z","data":{}}""");

        assertThat(evento.correlationId()).isNull();
        assertThat(evento.source()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "no es json",
            "[1,2]",
            "{\"event_type\":\"t\",\"occurred_at\":\"2026-11-26T15:00:00Z\",\"data\":{}}",
            "{\"event_id\":\"e\",\"occurred_at\":\"2026-11-26T15:00:00Z\",\"data\":{}}",
            "{\"event_id\":\"e\",\"event_type\":\"t\",\"data\":{}}",
            "{\"event_id\":\"e\",\"event_type\":\"t\",\"occurred_at\":\"ayer\",\"data\":{}}",
            "{\"event_id\":\"e\",\"event_type\":\"t\",\"occurred_at\":\"2026-11-26T15:00:00Z\"}",
            "{\"event_id\":\" \",\"event_type\":\"t\",\"occurred_at\":\"2026-11-26T15:00:00Z\",\"data\":{}}"})
    void unMensajeMalFormadoOSinCamposObligatoriosEsInvalido(String cuerpo) {
        assertThatThrownBy(() -> lector.leer(cuerpo)).isInstanceOf(MensajeInvalidoException.class);
    }
}
