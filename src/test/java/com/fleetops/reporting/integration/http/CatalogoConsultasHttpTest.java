package com.fleetops.reporting.integration.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fleetops.reporting.application.Fuente;
import com.fleetops.reporting.application.PeriodoConsulta;
import com.fleetops.reporting.application.RangoReporte;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogoConsultasHttpTest {

    private final CatalogoConsultasHttp catalogo = new CatalogoConsultasHttp(mock(ClienteHttpFuentes.class));

    @Test
    void conElPeriodoCubiertoPorEventosSoloSeConsultaElEstadoActual() {
        var periodo = new PeriodoConsulta(RangoReporte.MES_ACTUAL, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 26), true);

        List<ConsultaHttp> consultas = catalogo.planificar(periodo);

        assertThat(consultas).hasSize(9);
        assertThat(consultas).extracting(ConsultaHttp::fuente)
                .doesNotContain(Fuente.INCIDENTES, Fuente.COMBUSTIBLE);
        assertThat(consultas).extracting(ConsultaHttp::ruta).doesNotContain("/notifications");
    }

    @Test
    void sinCoberturaSeAgreganLosTresRespaldosDelPeriodoConSusFechas() {
        var periodo = new PeriodoConsulta(RangoReporte.PERSONALIZADO, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), false);

        List<ConsultaHttp> consultas = catalogo.planificar(periodo);

        assertThat(consultas).hasSize(12);
        ConsultaHttp incidentes = consultas.stream().filter(c -> c.fuente() == Fuente.INCIDENTES).findFirst().orElseThrow();
        assertThat(incidentes.ruta()).isEqualTo("/incidents");
        assertThat(incidentes.parametros()).containsEntry("reported_from", "2026-10-01").containsEntry("reported_to", "2026-10-31");
        ConsultaHttp combustible = consultas.stream().filter(c -> c.fuente() == Fuente.COMBUSTIBLE).findFirst().orElseThrow();
        assertThat(combustible.parametros()).containsEntry("date_from", "2026-10-01").containsEntry("date_to", "2026-10-31");
    }

    @Test
    void sinRangoLosRespaldosNoLlevanFechas() {
        List<ConsultaHttp> consultas = catalogo.planificar(PeriodoConsulta.sinRango());

        assertThat(consultas).hasSize(12);
        assertThat(consultas).filteredOn(c -> c.fuente() == Fuente.COMBUSTIBLE)
                .allSatisfy(c -> assertThat(c.parametros()).doesNotContainKeys("date_from", "date_to"));
    }

    @Test
    void losListadosPidenUnaSolaFilaParaLeerSoloElResumen() {
        List<ConsultaHttp> consultas = catalogo.planificar(PeriodoConsulta.sinRango());

        assertThat(consultas).filteredOn(c -> c.ruta().equals("/vehiculos")).singleElement()
                .satisfies(c -> assertThat(c.parametros()).containsEntry("page", "0").containsEntry("size", "1"));
        assertThat(consultas).filteredOn(c -> c.fuente() == Fuente.DOCUMENTACION).hasSize(4);
    }
}
