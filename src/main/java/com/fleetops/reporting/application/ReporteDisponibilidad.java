package com.fleetops.reporting.application;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Respuesta de GET /reportes/disponibilidad (pestaña "Contrato de respuesta" del backlog).
 * <p>
 * Todos los KPIs del contrato están siempre presentes. Empiezan en null y cada {@code CalculadorKpi} (HU-03 a
 * HU-09) llena los suyos; si su fuente no respondió, el KPI se queda en null (nunca 0 ni un valor estimado).
 * La única excepción es km_recorridos_periodo, que se omite cuando la consulta no trae rango (HU-06).
 */
@JsonPropertyOrder({"periodo", "fuentes_no_disponibles", "fecha_generacion"})
public final class ReporteDisponibilidad {

    /** Campos de KPIs del contrato, en el orden de la pestaña "Contrato de respuesta". */
    public static final List<String> CAMPOS_KPI = List.of(
            // HU-03 Flota
            "total_flota", "vehiculos_disponibles", "vehiculos_en_asignacion", "vehiculos_bloqueados",
            "vehiculos_en_mantenimiento", "vehiculos_inactivos_por_documentos", "porcentaje_disponibilidad_flota",
            "porcentaje_flota_operativa", "porcentaje_tiempo_disponible_periodo", "top_vehiculos_fuera_de_servicio",
            // HU-04 Mantenimiento
            "ordenes_abiertas", "ordenes_preventivas", "ordenes_correctivas", "porcentaje_mantenimiento_preventivo",
            "tiempo_medio_reparacion_horas", "mantenimientos_completados_periodo", "top_vehiculos_mantenimiento",
            // HU-05 Combustible
            "costo_total_combustible", "galones_totales", "registros_consumo_anomalo", "top_vehiculos_costo",
            "top_vehiculos_anomalias", "rendimiento_promedio_flota_km_gal",
            // HU-06 Uso de la flota
            "distancia_total_recorrida_km", "rutas_en_curso", "asignaciones_pendientes", "km_recorridos_periodo",
            // HU-07 Incidentes
            "incidentes_total", "incidentes_leves", "incidentes_graves", "top_vehiculos_incidentes",
            // HU-08 Alertas
            "alertas_criticas_pendientes", "alertas_por_severidad", "envios", "alertas_periodo_por_severidad",
            "envios_periodo", "tiempo_promedio_atencion_minutos",
            // HU-09 Documentos
            "documentos_por_vencer_30d", "documentos_vencidos");

    static final String KM_PERIODO = "km_recorridos_periodo";

    private final Map<String, Object> kpis = new LinkedHashMap<>();
    private final Periodo periodo;
    private final String fechaGeneracion;
    private List<String> fuentesNoDisponibles = List.of();

    public ReporteDisponibilidad(PeriodoConsulta consulta, String fechaGeneracion) {
        CAMPOS_KPI.forEach(campo -> kpis.put(campo, null));
        if (!consulta.tieneRango()) {
            kpis.remove(KM_PERIODO);
        }
        this.periodo = new Periodo(consulta.rango() == null ? null : consulta.rango().name(), consulta.fechaDesde(),
                consulta.fechaHasta(), consulta.cubiertoPorEventos());
        this.fechaGeneracion = fechaGeneracion;
    }

    /** Asigna el valor de un KPI del contrato. Un campo que no está en el contrato es un error de programación. */
    public void poner(String campo, Object valor) {
        if (!kpis.containsKey(campo)) {
            throw new IllegalArgumentException("El campo " + campo + " no está en el contrato de respuesta");
        }
        kpis.put(campo, valor);
    }

    public Object kpi(String campo) {
        return kpis.get(campo);
    }

    @JsonAnyGetter
    public Map<String, Object> getKpis() {
        return Collections.unmodifiableMap(kpis);
    }

    @JsonProperty("periodo")
    public Periodo getPeriodo() {
        return periodo;
    }

    @JsonProperty("fuentes_no_disponibles")
    public List<String> getFuentesNoDisponibles() {
        return fuentesNoDisponibles;
    }

    public void setFuentesNoDisponibles(List<String> fuentesNoDisponibles) {
        this.fuentesNoDisponibles = List.copyOf(fuentesNoDisponibles);
    }

    @JsonProperty("fecha_generacion")
    public String getFechaGeneracion() {
        return fechaGeneracion;
    }

    /** Un reporte es completo si todas las fuentes respondieron; solo esos se guardan en caché. */
    public boolean esCompleto() {
        return fuentesNoDisponibles.isEmpty();
    }

    /**
     * @param rango              rango pedido o null
     * @param fechaDesde         AAAA-MM-DD o null
     * @param fechaHasta         AAAA-MM-DD o null
     * @param cubiertoPorEventos true si el período sale de la base de lectura
     */
    public record Periodo(
            @JsonProperty("rango") String rango,
            @JsonProperty("fecha_desde") LocalDate fechaDesde,
            @JsonProperty("fecha_hasta") LocalDate fechaHasta,
            @JsonProperty("cubierto_por_eventos") boolean cubiertoPorEventos) {
    }
}
