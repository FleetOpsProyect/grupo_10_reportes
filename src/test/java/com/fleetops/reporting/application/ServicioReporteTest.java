package com.fleetops.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fleetops.reporting.Fixtures;
import com.fleetops.reporting.application.fuentes.EjecutorConsultas;
import com.fleetops.reporting.application.fuentes.FuenteFallidaException;
import com.fleetops.reporting.application.fuentes.PuertoBaseLectura;
import com.fleetops.reporting.application.fuentes.PuertoFuentesHttp;
import com.fleetops.reporting.application.fuentes.SolicitudConsulta;
import com.fleetops.reporting.application.kpi.CalculadorKpi;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ServicioReporteTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(8);
    private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    private final PuertoFuentesHttp http = mock(PuertoFuentesHttp.class);
    private final PuertoBaseLectura base = mock(PuertoBaseLectura.class);
    private final CalculadorKpi calculador = (resultados, periodo, reporte) ->
            resultados.dato(Fuente.VEHICULOS, "resumen", Integer.class).ifPresent(v -> reporte.poner("total_flota", v));

    @AfterEach
    void cerrar() {
        pool.shutdownNow();
    }

    private ServicioReporte servicio(Duration cacheTtl) {
        var props = new com.fleetops.reporting.config.ReportingProperties(Duration.ofMillis(300),
                new com.fleetops.reporting.config.ReportingProperties.CircuitBreaker(5, Duration.ofSeconds(30)),
                cacheTtl, "2026-11-01", Map.of(), null);
        @SuppressWarnings("unchecked")
        ObjectProvider<CalculadorKpi> proveedor = mock(ObjectProvider.class);
        when(proveedor.orderedStream()).thenAnswer(i -> Stream.of(calculador));
        return new ServicioReporte(new CalculadorPeriodo(Fixtures.RELOJ, props), http, base,
                new EjecutorConsultas(pool, CircuitBreakerRegistry.ofDefaults(), metricas, props), proveedor,
                new CacheReportes(props), Fixtures.RELOJ, metricas);
    }

    private static SolicitudConsulta ok(Fuente fuente, Object dato) {
        return new SolicitudConsulta(fuente, "resumen", () -> dato);
    }

    private static SolicitudConsulta falla(Fuente fuente) {
        return new SolicitudConsulta(fuente, "resumen", () -> {
            throw new FuenteFallidaException("caída");
        });
    }

    @Test
    void reporteCompletoConPeriodoYFechaDeGeneracion() {
        when(http.consultas(any(), eq("corr-1"))).thenReturn(List.of(ok(Fuente.VEHICULOS, 150)));
        when(base.consulta(any())).thenReturn(ok(Fuente.BASE_LECTURA, "ok"));

        ReporteDisponibilidad reporte = servicio(Duration.ZERO).generar("MES_ACTUAL", null, null, "corr-1");

        assertThat(reporte.kpi("total_flota")).isEqualTo(150);
        assertThat(reporte.getFuentesNoDisponibles()).isEmpty();
        assertThat(reporte.getPeriodo().cubiertoPorEventos()).isTrue();
        assertThat(reporte.getFechaGeneracion()).isEqualTo("2026-11-26T10:00:00-05:00");
        assertThat(metricas.counter("reportes_solicitudes_total", "resultado", "completo").count()).isEqualTo(1);
    }

    @Test
    void siUnaFuenteFallaResponde200ConSusKpisEnNull() {
        when(http.consultas(any(), any())).thenReturn(List.of(falla(Fuente.VEHICULOS), ok(Fuente.TELEMETRIA, 1)));
        when(base.consulta(any())).thenReturn(ok(Fuente.BASE_LECTURA, "ok"));

        ReporteDisponibilidad reporte = servicio(Duration.ZERO).generar(null, null, null, "c");

        assertThat(reporte.kpi("total_flota")).isNull();
        assertThat(reporte.getFuentesNoDisponibles()).containsExactly("vehiculos");
        assertThat(metricas.counter("reportes_solicitudes_total", "resultado", "parcial").count()).isEqualTo(1);
    }

    @Test
    void siLaBaseDeLecturaFallaSeSiguenEntregandoLosKpisPorHttp() {
        when(http.consultas(any(), any())).thenReturn(List.of(ok(Fuente.VEHICULOS, 150)));
        when(base.consulta(any())).thenReturn(falla(Fuente.BASE_LECTURA));

        ReporteDisponibilidad reporte = servicio(Duration.ZERO).generar(null, null, null, "c");

        assertThat(reporte.kpi("total_flota")).isEqualTo(150);
        assertThat(reporte.getFuentesNoDisponibles()).containsExactly("base_lectura");
    }

    @Test
    void siFallanTodasLasFuentesLanza503() {
        when(http.consultas(any(), any())).thenReturn(List.of(falla(Fuente.VEHICULOS)));
        when(base.consulta(any())).thenReturn(falla(Fuente.BASE_LECTURA));

        assertThatThrownBy(() -> servicio(Duration.ZERO).generar(null, null, null, "c"))
                .isInstanceOf(FuentesNoDisponiblesException.class)
                .hasMessage("Ninguna fuente de datos está disponible para generar el reporte");
    }

    @Test
    void cuandoLasFuentesSeRecuperanLaSiguienteConsultaVieneCompleta() {
        when(base.consulta(any())).thenReturn(ok(Fuente.BASE_LECTURA, "ok"));
        when(http.consultas(any(), any()))
                .thenReturn(List.of(falla(Fuente.VEHICULOS)))
                .thenReturn(List.of(ok(Fuente.VEHICULOS, 150)));
        ServicioReporte servicio = servicio(Duration.ZERO);

        assertThat(servicio.generar(null, null, null, "c").getFuentesNoDisponibles()).containsExactly("vehiculos");
        assertThat(servicio.generar(null, null, null, "c").getFuentesNoDisponibles()).isEmpty();
    }

    @Test
    void unReporteCompletoSeSirveDesdeLaCache() {
        when(http.consultas(any(), any())).thenReturn(List.of(ok(Fuente.VEHICULOS, 150)));
        when(base.consulta(any())).thenReturn(ok(Fuente.BASE_LECTURA, "ok"));
        ServicioReporte servicio = servicio(Duration.ofSeconds(60));

        ReporteDisponibilidad primero = servicio.generar("HOY", null, null, "c");
        ReporteDisponibilidad segundo = servicio.generar("HOY", null, null, "c");

        assertThat(segundo).isSameAs(primero);
        verify(http, org.mockito.Mockito.times(1)).consultas(any(), any());
    }

    @Test
    void unRangoInvalidoNoConsultaNingunaFuente() {
        ServicioReporte servicio = servicio(Duration.ZERO);

        assertThatThrownBy(() -> servicio.generar("AYER", null, null, "c")).isInstanceOf(RangoInvalidoException.class);
        verify(http, org.mockito.Mockito.never()).consultas(any(), any());
    }
}
