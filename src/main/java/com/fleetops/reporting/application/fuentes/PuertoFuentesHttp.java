package com.fleetops.reporting.application.fuentes;

import com.fleetops.reporting.application.PeriodoConsulta;
import java.util.List;

/** Llamadas HTTP a los microservicios fuente que hacen falta para un período (lo implementa integration.http). */
public interface PuertoFuentesHttp {

    List<SolicitudConsulta> consultas(PeriodoConsulta periodo, String correlationId);
}
