package com.fleetops.reporting.integration.messaging;

/**
 * El mensaje está mal formado, le faltan campos obligatorios o no hay quién lo aplique: va directo a la DLQ, sin
 * reintentos (HU-02, criterio 10). Reintentarlo nunca lo arreglaría.
 */
public class MensajeInvalidoException extends RuntimeException {

    public MensajeInvalidoException(String mensaje) {
        super(mensaje);
    }

    public MensajeInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
