package com.fleetops.reporting.integration.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Evento ya aplicado a las proyecciones (tabla eventos_procesados, idempotencia). */
@Entity
@Table(name = "eventos_procesados")
public class EventoProcesado {

    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "procesado_en", nullable = false)
    private OffsetDateTime procesadoEn;

    protected EventoProcesado() {
        // JPA
    }

    public EventoProcesado(String eventId, String eventType, String source, OffsetDateTime occurredAt,
                           OffsetDateTime procesadoEn) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.source = source;
        this.occurredAt = occurredAt;
        this.procesadoEn = procesadoEn;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSource() {
        return source;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public OffsetDateTime getProcesadoEn() {
        return procesadoEn;
    }
}
