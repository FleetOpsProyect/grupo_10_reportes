package com.fleetops.reporting.application;

/**
 * Fuentes de datos del reporte (backlog HU-02, criterio 1). El {@link #codigo()} es el texto que aparece en
 * fuentes_no_disponibles y el nombre del Circuit Breaker de cada una.
 */
public enum Fuente {
    VEHICULOS("vehiculos"),
    ASIGNACIONES("asignaciones"),
    MANTENIMIENTO("mantenimiento"),
    TELEMETRIA("telemetria"),
    NOTIFICACIONES("notificaciones"),
    DOCUMENTACION("documentacion"),
    INCIDENTES("incidentes"),
    COMBUSTIBLE("combustible"),
    BASE_LECTURA("base_lectura");

    private final String codigo;

    Fuente(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
