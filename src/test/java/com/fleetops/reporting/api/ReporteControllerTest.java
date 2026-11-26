package com.fleetops.reporting.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fleetops.reporting.Fixtures;
import com.fleetops.reporting.application.FuentesNoDisponiblesException;
import com.fleetops.reporting.application.PeriodoConsulta;
import com.fleetops.reporting.application.RangoInvalidoException;
import com.fleetops.reporting.application.ReporteDisponibilidad;
import com.fleetops.reporting.application.ServicioReporte;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReporteController.class)
@Import({FiltroCorrelacion.class, ManejadorErrores.class, ReporteControllerTest.Reloj.class})
class ReporteControllerTest {

    private static final String RUTA = "/reportes/disponibilidad";
    private static final String UUID_V4 = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock reloj() {
            return Fixtures.RELOJ;
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioReporte servicio;

    @Test
    void sinParametrosResponde200ConElContratoCompleto() throws Exception {
        var reporte = new ReporteDisponibilidad(PeriodoConsulta.sinRango(), "2026-11-26T10:00:00-05:00");
        when(servicio.generar(isNull(), isNull(), isNull(), any())).thenReturn(reporte);

        mvc.perform(get(RUTA).param("parametro_desconocido", "x"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total_flota").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.periodo.cubierto_por_eventos").value(false))
                .andExpect(jsonPath("$.fuentes_no_disponibles").isArray())
                .andExpect(jsonPath("$.fecha_generacion").value("2026-11-26T10:00:00-05:00"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_V4)));
    }

    @Test
    void reutilizaElCorrelationIdRecibidoYLoPasaAlServicio() throws Exception {
        var reporte = new ReporteDisponibilidad(PeriodoConsulta.sinRango(), "x");
        when(servicio.generar(eq("HOY"), isNull(), isNull(), eq("abc-123"))).thenReturn(reporte);

        mvc.perform(get(RUTA).param("rango", "HOY").header("X-Correlation-Id", "abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "abc-123"));
    }

    @Test
    void unCorrelationIdConCaracteresRarosSeReemplaza() throws Exception {
        when(servicio.generar(any(), any(), any(), any()))
                .thenReturn(new ReporteDisponibilidad(PeriodoConsulta.sinRango(), "x"));

        mvc.perform(get(RUTA).header("X-Correlation-Id", "abc\nFALSO"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_V4)));
    }

    @Test
    void reporteParcialResponde200ConLasFuentesCaidas() throws Exception {
        var reporte = new ReporteDisponibilidad(PeriodoConsulta.sinRango(), "x");
        reporte.setFuentesNoDisponibles(List.of("combustible"));
        when(servicio.generar(any(), any(), any(), any())).thenReturn(reporte);

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuentes_no_disponibles[0]").value("combustible"));
    }

    @Test
    void rangoInvalidoResponde400ConElJsonComun() throws Exception {
        when(servicio.generar(eq("AYER"), any(), any(), any())).thenThrow(new RangoInvalidoException("no existe"));

        mvc.perform(get(RUTA).param("rango", "AYER").header("X-Correlation-Id", "c-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("RANGO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value("no existe"))
                .andExpect(jsonPath("$.ruta").value(RUTA))
                .andExpect(jsonPath("$.correlation_id").value("c-1"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-26T10:00:00-05:00"));
    }

    @Test
    void sinFuentesResponde503() throws Exception {
        when(servicio.generar(any(), any(), any(), any())).thenThrow(new FuentesNoDisponiblesException());

        mvc.perform(get(RUTA))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("FUENTES_NO_DISPONIBLES"))
                .andExpect(jsonPath("$.mensaje").value("Ninguna fuente de datos está disponible para generar el reporte"));
    }

    @Test
    void otroMetodoResponde405() throws Exception {
        mvc.perform(post(RUTA))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    @Test
    void unErrorInesperadoResponde500SinTrazas() throws Exception {
        when(servicio.generar(any(), any(), any(), any())).thenThrow(new IllegalStateException("detalle interno"));

        mvc.perform(get(RUTA))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(content().string(not(containsString("detalle interno"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
