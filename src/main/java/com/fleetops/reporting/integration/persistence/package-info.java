/**
 * Acceso a la base de lectura (Neon PostgreSQL, CQRS).
 * <p>
 * Va aquí: entidades JPA y repositorios de las dos proyecciones del MVP, alimentadas por eventos
 * (proyeccion_combustible desde fuel_record_registered_event y proyeccion_kilometraje desde
 * vehicle_odometer_updated_event) y de la tabla eventos_procesados (idempotencia). Las tablas se crean con
 * migraciones Flyway en src/main/resources/db/migration. El resto de KPIs se consultan por HTTP y no se guardan.
 */
package com.fleetops.reporting.integration.persistence;
