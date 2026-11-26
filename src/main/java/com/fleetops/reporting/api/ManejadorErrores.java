package com.fleetops.reporting.api;

import com.fleetops.reporting.application.FuentesNoDisponiblesException;
import com.fleetops.reporting.application.RangoInvalidoException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Convierte las excepciones en el JSON común de error (HU-01, criterio 9). Nunca devuelve trazas.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    private final Clock reloj;

    public ManejadorErrores(Clock reloj) {
        this.reloj = reloj;
    }

    @ExceptionHandler(RangoInvalidoException.class)
    ResponseEntity<ErrorRespuesta> rangoInvalido(RangoInvalidoException e, HttpServletRequest peticion) {
        return responder(HttpStatus.BAD_REQUEST, "RANGO_INVALIDO", e.getMessage(), peticion);
    }

    @ExceptionHandler(FuentesNoDisponiblesException.class)
    ResponseEntity<ErrorRespuesta> sinFuentes(FuentesNoDisponiblesException e, HttpServletRequest peticion) {
        return responder(HttpStatus.SERVICE_UNAVAILABLE, "FUENTES_NO_DISPONIBLES", FuentesNoDisponiblesException.MENSAJE,
                peticion);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorRespuesta> metodoNoPermitido(HttpRequestMethodNotSupportedException e,
                                                     HttpServletRequest peticion) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO",
                "El método " + e.getMethod() + " no está permitido; use GET", peticion);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorRespuesta> noEncontrado(NoResourceFoundException e, HttpServletRequest peticion) {
        return responder(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", "La ruta pedida no existe", peticion);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorRespuesta> inesperado(Exception e, HttpServletRequest peticion) {
        log.error("Error no controlado en {}", peticion.getRequestURI(), e);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Ocurrió un error inesperado al generar el reporte", peticion);
    }

    private ResponseEntity<ErrorRespuesta> responder(HttpStatus estado, String codigo, String mensaje,
                                                     HttpServletRequest peticion) {
        String ahora = OffsetDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return ResponseEntity.status(estado)
                .body(new ErrorRespuesta(codigo, mensaje, ahora, peticion.getRequestURI(),
                        FiltroCorrelacion.de(peticion)));
    }
}
