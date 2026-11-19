/**
 * Integración síncrona con las fuentes (patrón Adapter + Decorator).
 * <p>
 * Va aquí un cliente por microservicio, todos sobre endpoints que ya existen en FleetOps_Endpoints_y_Eventos
 * (sin pedir endpoints nuevos a otros equipos). Se lee solo el bloque resumen con page=0&amp;size=1:
 * <ul>
 *   <li>Vehículos: GET /vehiculos (resumen: total y conteo por estado)</li>
 *   <li>Asignaciones: GET /assignment/active (resumen: conteo por estado)</li>
 *   <li>Incidentes: GET /incidents?reported_from&amp;reported_to (summary.by_severity)</li>
 *   <li>Mantenimiento: GET /maintenance (resumen: by_status, by_type, avg_repair_hours)</li>
 *   <li>Combustible: GET /fuel_records?date_from&amp;date_to (summary: total_cost, total_gallons, anomalous_records)</li>
 *   <li>Telemetría: GET /rutas (resumen: distancia_total_km, rutas_en_curso)</li>
 *   <li>Notificaciones: GET /notifications/status y GET /notifications?date_from&amp;date_to (summary)</li>
 *   <li>Documentación: GET /documents/expiring-soon?days=30 y GET /documents?status=EXPIRED (total_elements)</li>
 * </ul>
 * Cada llamada lleva timeout y Circuit Breaker de Resilience4j (uno por fuente) y propaga X-Correlation-Id.
 */
package com.fleetops.reporting.integration.http;
