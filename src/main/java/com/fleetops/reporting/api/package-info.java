/**
 * Capa de presentación (API REST).
 * <p>
 * Va aquí: el controlador de GET /reportes/disponibilidad, los DTO de respuesta, el manejador global de
 * errores (JSON común: codigo, mensaje, timestamp, ruta, correlation_id) y el filtro del X-Correlation-Id.
 * No contiene reglas de negocio: solo valida la entrada y delega en {@code application}.
 */
package com.fleetops.reporting.api;
