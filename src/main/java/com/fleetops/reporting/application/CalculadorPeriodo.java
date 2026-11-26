package com.fleetops.reporting.application;

import com.fleetops.reporting.config.ReportingProperties;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Convierte los parámetros {@code rango}, {@code fecha_desde} y {@code fecha_hasta} en un {@link PeriodoConsulta}
 * (HU-01, criterios 3 a 5) y decide si el período sale de la base de lectura (HU-02, criterios 6 y 7).
 * Las fechas se calculan en hora de Colombia: el {@link Clock} del contexto usa America/Bogota.
 */
@Component
public class CalculadorPeriodo {

    private final Clock reloj;
    private final Optional<LocalDate> eventosDesde;

    public CalculadorPeriodo(Clock reloj, ReportingProperties props) {
        this.reloj = reloj;
        this.eventosDesde = props.fechaEventosDesde();
    }

    public PeriodoConsulta calcular(String rango, String fechaDesde, String fechaHasta) {
        if (rango == null || rango.isBlank()) {
            return PeriodoConsulta.sinRango();
        }
        RangoReporte tipo = RangoReporte.desde(rango.trim())
                .orElseThrow(() -> new RangoInvalidoException("El rango '" + rango + "' no existe. Valores aceptados: "
                        + "HOY, SEMANA_ACTUAL, MES_ACTUAL o PERSONALIZADO"));
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate desde;
        LocalDate hasta = hoy;
        switch (tipo) {
            case HOY -> desde = hoy;
            case SEMANA_ACTUAL -> desde = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES_ACTUAL -> desde = hoy.withDayOfMonth(1);
            default -> {
                desde = leerFecha("fecha_desde", fechaDesde);
                hasta = leerFecha("fecha_hasta", fechaHasta);
                if (desde.isAfter(hasta)) {
                    throw new RangoInvalidoException("fecha_desde no puede ser posterior a fecha_hasta");
                }
            }
        }
        return new PeriodoConsulta(tipo, desde, hasta, cubiertoPorEventos(desde));
    }

    private boolean cubiertoPorEventos(LocalDate desde) {
        return eventosDesde.map(inicio -> !desde.isBefore(inicio)).orElse(false);
    }

    private static LocalDate leerFecha(String nombre, String valor) {
        if (valor == null || valor.isBlank()) {
            throw new RangoInvalidoException("El rango PERSONALIZADO exige " + nombre + " (AAAA-MM-DD)");
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new RangoInvalidoException(nombre + " debe tener el formato AAAA-MM-DD");
        }
    }
}
