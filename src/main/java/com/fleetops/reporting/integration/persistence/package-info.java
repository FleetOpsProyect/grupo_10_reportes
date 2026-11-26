/**
 * Acceso a la base de lectura propia (Neon PostgreSQL, CQRS), que guarda lo que pasó en cada período.
 * <p>
 * Va aquí: entidades JPA y repositorios de las proyecciones alimentadas por eventos:
 * <ul>
 *   <li>proyeccion_estado_vehiculo (vehicle_status_changed_event): tramos de estado por vehículo.</li>
 *   <li>proyeccion_kilometraje (vehicle_odometer_updated_event).</li>
 *   <li>proyeccion_mantenimiento (maintenance_completed_event).</li>
 *   <li>proyeccion_combustible (fuel_record_registered_event).</li>
 *   <li>proyeccion_incidentes (minor_ y severe_incident_reported_event).</li>
 *   <li>proyeccion_alertas (notification_*_event), una fila por notification_id.</li>
 * </ul>
 * Además, la tabla eventos_procesados (idempotencia). Las tablas se crean con migraciones Flyway en
 * src/main/resources/db/migration. El estado actual (flota hoy, órdenes abiertas, rutas en curso, alertas
 * pendientes, documentos) se consulta por HTTP y no se guarda.
 */
package com.fleetops.reporting.integration.persistence;
