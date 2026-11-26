package com.fleetops.reporting.application.kpi;

import com.fleetops.reporting.application.PeriodoConsulta;
import com.fleetops.reporting.application.ReporteDisponibilidad;
import com.fleetops.reporting.application.fuentes.ResultadosFuentes;

/**
 * Estrategia de cálculo de un grupo de KPIs (patrón Strategy). Cada HU de indicadores (HU-03 a HU-09) agrega
 * una implementación como bean de Spring y {@code ServicioReporte} las aplica todas.
 * <p>
 * Regla común: si la fuente de un KPI no respondió, no se toca el campo y queda en null.
 */
public interface CalculadorKpi {

    void calcular(ResultadosFuentes resultados, PeriodoConsulta periodo, ReporteDisponibilidad reporte);
}
