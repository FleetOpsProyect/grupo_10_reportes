package com.fleetops.reporting.application.fuentes;

/**
 * Una fuente respondió mal: 4xx o 5xx, conexión rechazada o respuesta 200 sin los campos que necesitamos
 * (HU-02, criterio 4). Los timeouts y el circuito abierto se tratan aparte en {@link EjecutorConsultas}.
 */
public class FuenteFallidaException extends RuntimeException {

    public FuenteFallidaException(String mensaje) {
        super(mensaje);
    }

    public FuenteFallidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
