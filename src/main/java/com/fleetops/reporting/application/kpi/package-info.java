/**
 * Estrategias de cálculo de KPIs (patrón Strategy): una clase por grupo de indicadores del backlog
 * (flota, mantenimiento, combustible, uso, incidentes, alertas, documentos). Si la fuente de un KPI no respondió,
 * el KPI va en null, nunca en 0.
 */
package com.fleetops.reporting.application.kpi;
