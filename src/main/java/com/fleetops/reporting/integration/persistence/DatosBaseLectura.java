package com.fleetops.reporting.integration.persistence;

import java.time.OffsetDateTime;

/**
 * Lo que se lee de la base de lectura para un reporte. Por ahora confirma que la base responde y trae el último
 * evento aplicado; cada HU de indicadores (HU-03 a HU-08) agrega aquí los agregados de su proyección.
 *
 * @param ultimoEventoAplicado occurred_at del evento más reciente aplicado, o null si todavía no hay ninguno
 */
public record DatosBaseLectura(OffsetDateTime ultimoEventoAplicado) {
}
