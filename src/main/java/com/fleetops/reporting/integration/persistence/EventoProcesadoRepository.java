package com.fleetops.reporting.integration.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a eventos_procesados. */
public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, String> {
}
