package com.fleetops.reporting.application;

/** El rango pedido no es válido: responde 400 RANGO_INVALIDO (HU-01, criterio 3). */
public class RangoInvalidoException extends RuntimeException {

    public RangoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
