/**
 * Integración síncrona con las fuentes (patrón Adapter + Decorator).
 * <p>
 * Va aquí: un cliente por microservicio (vehiculos, assignment, incidents, mantenimientos, fuel_records, rutas,
 * notifications/status) que hace GET con page=0&amp;size=1 y lee el bloque summary, envuelto con timeout y
 * Circuit Breaker de Resilience4j (uno por fuente) y propagando X-Correlation-Id.
 */
package com.fleetops.reporting.integration.http;
