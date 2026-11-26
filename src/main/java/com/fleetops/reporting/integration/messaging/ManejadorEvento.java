package com.fleetops.reporting.integration.messaging;

/**
 * Aplica un tipo de evento a su proyección. Cada HU de indicadores (HU-03 a HU-08) agrega un manejador por
 * evento que consume; {@link ProcesadorEventos} los busca por event_type.
 */
public interface ManejadorEvento {

    /** event_type que atiende, por ejemplo fuel_record_registered_event. */
    String tipoEvento();

    /** Tabla de la base de lectura que actualiza, por ejemplo proyeccion_combustible (para métricas). */
    String proyeccion();

    /**
     * Aplica el evento. Corre dentro de la transacción que guarda el event_id. Si el payload no trae un campo
     * obligatorio debe lanzar {@link MensajeInvalidoException}.
     */
    void aplicar(SobreEvento evento);
}
