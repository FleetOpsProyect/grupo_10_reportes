-- Idempotencia del consumo de eventos (HU-02, criterio 9): un mensaje se borra de la cola solo después de guardar
-- su cambio y su event_id aquí, en la misma transacción. Si el event_id ya existe, el evento se ignora.
CREATE TABLE eventos_procesados (
    event_id     VARCHAR(100) PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    source       VARCHAR(100),
    occurred_at  TIMESTAMPTZ  NOT NULL,
    procesado_en TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Antigüedad del último evento aplicado por tipo (métrica de /metrics).
CREATE INDEX ix_eventos_procesados_tipo_fecha ON eventos_procesados (event_type, occurred_at DESC);
