package com.fleetops.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ReporteDisponibilidadTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void serializaTodosLosCamposDelContratoAunqueSeanNull() {
        var periodo = new PeriodoConsulta(RangoReporte.HOY, LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 26), true);
        var reporte = new ReporteDisponibilidad(periodo, "2026-11-26T10:00:00-05:00");
        reporte.poner("total_flota", 150);

        JsonNode nodo = json.readTree(json.writeValueAsString(reporte));

        ReporteDisponibilidad.CAMPOS_KPI.forEach(campo -> assertThat(nodo.has(campo)).as(campo).isTrue());
        assertThat(nodo.get("total_flota").asInt()).isEqualTo(150);
        assertThat(nodo.get("galones_totales").isNull()).isTrue();
        assertThat(nodo.get("periodo").get("rango").asString()).isEqualTo("HOY");
        assertThat(nodo.get("periodo").get("fecha_desde").asString()).isEqualTo("2026-11-26");
        assertThat(nodo.get("periodo").get("cubierto_por_eventos").asBoolean()).isTrue();
        assertThat(nodo.get("fuentes_no_disponibles").isArray()).isTrue();
        assertThat(nodo.get("fecha_generacion").asString()).isEqualTo("2026-11-26T10:00:00-05:00");
    }

    @Test
    void sinRangoOmiteLosKilometrosDelPeriodo() {
        var reporte = new ReporteDisponibilidad(PeriodoConsulta.sinRango(), "x");

        JsonNode nodo = json.readTree(json.writeValueAsString(reporte));

        assertThat(nodo.has("km_recorridos_periodo")).isFalse();
        assertThat(nodo.get("periodo").get("rango").isNull()).isTrue();
    }

    @Test
    void rechazaUnCampoQueNoEstaEnElContrato() {
        var reporte = new ReporteDisponibilidad(PeriodoConsulta.sinRango(), "x");

        assertThatThrownBy(() -> reporte.poner("inventado", 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
