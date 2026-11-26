package com.fleetops.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fleetops.reporting.Fixtures;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalculadorPeriodoTest {

    private final CalculadorPeriodo calculador = new CalculadorPeriodo(Fixtures.RELOJ, Fixtures.props("2026-11-01"));

    @Test
    void sinRangoEsTodoElHistoricoYSalePorHttp() {
        PeriodoConsulta periodo = calculador.calcular(null, null, null);

        assertThat(periodo.tieneRango()).isFalse();
        assertThat(periodo.fechaDesde()).isNull();
        assertThat(periodo.cubiertoPorEventos()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "HOY, 2026-11-26, 2026-11-26",
            "SEMANA_ACTUAL, 2026-11-23, 2026-11-26",
            "MES_ACTUAL, 2026-11-01, 2026-11-26"})
    void calculaLosRangosEnHoraDeColombia(String rango, LocalDate desde, LocalDate hasta) {
        PeriodoConsulta periodo = calculador.calcular(rango, null, null);

        assertThat(periodo.fechaDesde()).isEqualTo(desde);
        assertThat(periodo.fechaHasta()).isEqualTo(hasta);
        assertThat(periodo.cubiertoPorEventos()).isTrue();
    }

    @Test
    void hoyUsaLaFechaDeColombiaAunqueEnUtcYaSeaOtroDia() {
        // 26/11 a las 23:30 en Colombia = 27/11 04:30 UTC
        var reloj = java.time.Clock.fixed(java.time.Instant.parse("2026-11-27T04:30:00Z"), Fixtures.BOGOTA);
        var periodo = new CalculadorPeriodo(reloj, Fixtures.props(null)).calcular("HOY", null, null);

        assertThat(periodo.fechaDesde()).isEqualTo(LocalDate.of(2026, 11, 26));
    }

    @Test
    void personalizadoAntesDeLasSuscripcionesSalePorHttp() {
        PeriodoConsulta periodo = calculador.calcular("PERSONALIZADO", "2026-10-15", "2026-11-10");

        assertThat(periodo.rango()).isEqualTo(RangoReporte.PERSONALIZADO);
        assertThat(periodo.cubiertoPorEventos()).isFalse();
    }

    @Test
    void sinFechaDeSuscripcionNuncaEstaCubierto() {
        var sinFecha = new CalculadorPeriodo(Fixtures.RELOJ, Fixtures.props(""));

        assertThat(sinFecha.calcular("HOY", null, null).cubiertoPorEventos()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "AYER, , ",
            "hoy, , ",
            "PERSONALIZADO, , 2026-11-10",
            "PERSONALIZADO, 2026-11-01, ",
            "PERSONALIZADO, 2026-11-10, 2026-11-01",
            "PERSONALIZADO, 10/11/2026, 2026-11-20"}, nullValues = "")
    void rangoInvalido(String rango, String desde, String hasta) {
        assertThatThrownBy(() -> calculador.calcular(rango, desde, hasta)).isInstanceOf(RangoInvalidoException.class);
    }
}
