package com.fleetops.reporting.application;

import java.time.LocalDate;

/**
 * Período pedido en la consulta.
 *
 * @param rango              rango pedido; null si la consulta no trae rango (todo el histórico)
 * @param fechaDesde         primer día del período (hora de Colombia); null sin rango
 * @param fechaHasta         último día del período, incluido; null sin rango
 * @param cubiertoPorEventos true si el período sale de la base de lectura (empieza en REPORTES_EVENTOS_DESDE o
 *                           después); false si sale por el respaldo HTTP
 */
public record PeriodoConsulta(RangoReporte rango, LocalDate fechaDesde, LocalDate fechaHasta, boolean cubiertoPorEventos) {

    /** Sin rango: todo el histórico, y los totales del período salen por HTTP (HU-02, criterio 7). */
    public static PeriodoConsulta sinRango() {
        return new PeriodoConsulta(null, null, null, false);
    }

    public boolean tieneRango() {
        return rango != null;
    }

    /** Clave de caché: el rango forma parte de la clave (HU-01, criterio 11). */
    public String claveCache() {
        return rango + "|" + fechaDesde + "|" + fechaHasta;
    }
}
