package com.fleetops.reporting.application;

/** Ninguna fuente respondió: responde 503 FUENTES_NO_DISPONIBLES (HU-02, criterio 3). */
public class FuentesNoDisponiblesException extends RuntimeException {

    public static final String MENSAJE = "Ninguna fuente de datos está disponible para generar el reporte";

    public FuentesNoDisponiblesException() {
        super(MENSAJE);
    }
}
