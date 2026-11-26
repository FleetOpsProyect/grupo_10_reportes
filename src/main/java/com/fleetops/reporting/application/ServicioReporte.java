package com.fleetops.reporting.application;

import com.fleetops.reporting.application.fuentes.EjecutorConsultas;
import com.fleetops.reporting.application.fuentes.PuertoBaseLectura;
import com.fleetops.reporting.application.fuentes.PuertoFuentesHttp;
import com.fleetops.reporting.application.fuentes.ResultadosFuentes;
import com.fleetops.reporting.application.fuentes.SolicitudConsulta;
import com.fleetops.reporting.application.kpi.CalculadorKpi;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Orquestador del reporte (patrón Aggregator): calcula el período, pide en paralelo las fuentes necesarias
 * (Scatter-Gather), aplica los cálculos de KPIs y arma la respuesta con fuentes_no_disponibles.
 * <p>
 * Modelo híbrido (HU-02): el estado actual siempre sale por HTTP; el período sale de la base de lectura cuando
 * el rango empieza en REPORTES_EVENTOS_DESDE o después, y si no, de los resúmenes HTTP como respaldo.
 */
@Service
public class ServicioReporte {

    private static final Logger log = LoggerFactory.getLogger(ServicioReporte.class);

    private final CalculadorPeriodo calculadorPeriodo;
    private final PuertoFuentesHttp fuentesHttp;
    private final PuertoBaseLectura baseLectura;
    private final EjecutorConsultas ejecutor;
    private final List<CalculadorKpi> calculadores;
    private final CacheReportes cache;
    private final Clock reloj;
    private final MeterRegistry metricas;

    public ServicioReporte(CalculadorPeriodo calculadorPeriodo, PuertoFuentesHttp fuentesHttp,
                           PuertoBaseLectura baseLectura, EjecutorConsultas ejecutor,
                           ObjectProvider<CalculadorKpi> calculadores, CacheReportes cache, Clock reloj,
                           MeterRegistry metricas) {
        this.calculadorPeriodo = calculadorPeriodo;
        this.fuentesHttp = fuentesHttp;
        this.baseLectura = baseLectura;
        this.ejecutor = ejecutor;
        this.calculadores = calculadores.orderedStream().toList();
        this.cache = cache;
        this.reloj = reloj;
        this.metricas = metricas;
    }

    public ReporteDisponibilidad generar(String rango, String fechaDesde, String fechaHasta, String correlationId) {
        PeriodoConsulta periodo = calculadorPeriodo.calcular(rango, fechaDesde, fechaHasta);
        Optional<ReporteDisponibilidad> enCache = cache.buscar(periodo);
        if (enCache.isPresent()) {
            contar("cache");
            return enCache.get();
        }

        List<SolicitudConsulta> solicitudes = new ArrayList<>(fuentesHttp.consultas(periodo, correlationId));
        solicitudes.add(baseLectura.consulta(periodo));
        ResultadosFuentes resultados = ejecutor.ejecutar(solicitudes);

        if (resultados.todasFallaron()) {
            contar("sin_fuentes");
            log.error("Ninguna fuente de datos respondió: {}", resultados.codigosNoDisponibles());
            throw new FuentesNoDisponiblesException();
        }

        ReporteDisponibilidad reporte = new ReporteDisponibilidad(periodo, ahora());
        calculadores.forEach(c -> c.calcular(resultados, periodo, reporte));
        reporte.setFuentesNoDisponibles(resultados.codigosNoDisponibles());

        if (reporte.esCompleto()) {
            contar("completo");
            cache.guardar(periodo, reporte);
        } else {
            contar("parcial");
            log.warn("Reporte generado parcialmente: fuentes no disponibles {}", reporte.getFuentesNoDisponibles());
        }
        return reporte;
    }

    private String ahora() {
        return OffsetDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private void contar(String resultado) {
        Counter.builder("reportes_solicitudes_total")
                .description("Solicitudes de reporte por resultado")
                .tag("resultado", resultado)
                .register(metricas)
                .increment();
    }
}
