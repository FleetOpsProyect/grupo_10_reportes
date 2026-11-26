package com.fleetops.reporting.application.fuentes;

import com.fleetops.reporting.application.PeriodoConsulta;

/** Lectura de la base de lectura propia (lo implementa integration.persistence). */
public interface PuertoBaseLectura {

    SolicitudConsulta consulta(PeriodoConsulta periodo);
}
