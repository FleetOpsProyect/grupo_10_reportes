package com.fleetops.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CacheReportesTest {

    private final AtomicLong ahora = new AtomicLong();
    private final PeriodoConsulta hoy = new PeriodoConsulta(RangoReporte.HOY, LocalDate.of(2026, 11, 26),
            LocalDate.of(2026, 11, 26), true);

    @Test
    void guardaUnReporteCompletoHastaQueVence() {
        var cache = new CacheReportes(Duration.ofSeconds(60), ahora::get);
        var reporte = new ReporteDisponibilidad(hoy, "2026-11-26T10:00:00-05:00");

        cache.guardar(hoy, reporte);
        assertThat(cache.buscar(hoy)).containsSame(reporte);

        ahora.addAndGet(Duration.ofSeconds(61).toNanos());
        assertThat(cache.buscar(hoy)).isEmpty();
    }

    @Test
    void nuncaGuardaUnReporteParcial() {
        var cache = new CacheReportes(Duration.ofSeconds(60), ahora::get);
        var parcial = new ReporteDisponibilidad(hoy, "2026-11-26T10:00:00-05:00");
        parcial.setFuentesNoDisponibles(List.of("combustible"));

        cache.guardar(hoy, parcial);

        assertThat(cache.buscar(hoy)).isEmpty();
    }

    @Test
    void elRangoEsParteDeLaClave() {
        var cache = new CacheReportes(Duration.ofSeconds(60), ahora::get);
        cache.guardar(hoy, new ReporteDisponibilidad(hoy, "x"));

        assertThat(cache.buscar(PeriodoConsulta.sinRango())).isEmpty();
    }

    @Test
    void conTtlCeroNoGuardaNada() {
        var cache = new CacheReportes(Duration.ZERO, ahora::get);
        cache.guardar(hoy, new ReporteDisponibilidad(hoy, "x"));

        assertThat(cache.buscar(hoy)).isEmpty();
    }
}
