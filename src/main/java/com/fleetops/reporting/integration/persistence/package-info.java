/**
 * Acceso a la base de lectura (PostgreSQL, CQRS).
 * <p>
 * Va aquí: entidades JPA y repositorios de las proyecciones (documentos, combustible, alertas) y de la tabla
 * eventos_procesados. Las tablas se crean con migraciones Flyway en src/main/resources/db/migration.
 */
package com.fleetops.reporting.integration.persistence;
