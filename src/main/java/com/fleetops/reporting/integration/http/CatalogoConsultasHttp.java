package com.fleetops.reporting.integration.http;

import com.fleetops.reporting.application.Fuente;
import com.fleetops.reporting.application.PeriodoConsulta;
import com.fleetops.reporting.application.fuentes.PuertoFuentesHttp;
import com.fleetops.reporting.application.fuentes.SolicitudConsulta;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Qué endpoints se llaman para un período (modelo híbrido, HU-02). Todos existen en el Excel definitivo; no se
 * le pide a ningún equipo un endpoint nuevo.
 * <ul>
 *   <li>Estado actual, siempre por HTTP (9 llamadas): Vehículos, Asignaciones, Mantenimiento, Telemetría,
 *       estado de Notificaciones y 4 de Documentación.</li>
 *   <li>Respaldo del período (3 llamadas más), solo sin rango o con un rango que empieza antes de
 *       REPORTES_EVENTOS_DESDE: Incidentes, Combustible y el listado de Notificaciones.</li>
 * </ul>
 */
@Component
public class CatalogoConsultasHttp implements PuertoFuentesHttp {

    public static final String ID_RESUMEN = "resumen";
    public static final String ID_ESTADO = "estado";
    public static final String ID_PERIODO = "periodo";

    private static final List<String> BLOQUE_RESUMEN = List.of("summary", "resumen");
    private static final List<String> TOTAL_ELEMENTOS = List.of("total_elements");
    private static final String VEHICULO = "VEHICLE";
    private static final String CONDUCTOR = "DRIVER";

    private final ClienteHttpFuentes cliente;

    public CatalogoConsultasHttp(ClienteHttpFuentes cliente) {
        this.cliente = cliente;
    }

    @Override
    public List<SolicitudConsulta> consultas(PeriodoConsulta periodo, String correlationId) {
        return planificar(periodo).stream()
                .map(c -> new SolicitudConsulta(c.fuente(), c.id(), () -> cliente.consultar(c, correlationId)))
                .toList();
    }

    /** Las llamadas HTTP que necesita el período, sin ejecutarlas. */
    public List<ConsultaHttp> planificar(PeriodoConsulta periodo) {
        List<ConsultaHttp> consultas = new ArrayList<>();
        // Estado actual (HU-03, HU-04, HU-06, HU-08, HU-09)
        consultas.add(resumen(Fuente.VEHICULOS, "/vehiculos"));
        consultas.add(resumen(Fuente.ASIGNACIONES, "/assignment/active"));
        consultas.add(resumen(Fuente.MANTENIMIENTO, "/maintenance"));
        consultas.add(resumen(Fuente.TELEMETRIA, "/rutas"));
        consultas.add(new ConsultaHttp(Fuente.NOTIFICACIONES, ID_ESTADO, "/notifications/status", Map.of(),
                List.of("critical_alerts_pending"), true));
        for (String dueno : List.of(VEHICULO, CONDUCTOR)) {
            consultas.add(new ConsultaHttp(Fuente.DOCUMENTACION, "por_vencer_" + dueno, "/documents/expiring-soon",
                    parametros("days", "30", "owner_type", dueno, "page", "0", "size", "1"), TOTAL_ELEMENTOS, true));
            consultas.add(new ConsultaHttp(Fuente.DOCUMENTACION, "vencidos_" + dueno, "/documents",
                    parametros("status", "EXPIRED", "owner_type", dueno, "page", "0", "size", "1"), TOTAL_ELEMENTOS, true));
        }
        // Respaldo del período (HU-05, HU-07, HU-08) cuando no lo cubre la base de lectura
        if (!periodo.cubiertoPorEventos()) {
            consultas.add(periodo(Fuente.INCIDENTES, "/incidents", "reported_from", "reported_to", periodo));
            consultas.add(periodo(Fuente.COMBUSTIBLE, "/fuel_records", "date_from", "date_to", periodo));
            consultas.add(periodo(Fuente.NOTIFICACIONES, "/notifications", "date_from", "date_to", periodo));
        }
        return consultas;
    }

    private static ConsultaHttp resumen(Fuente fuente, String ruta) {
        return new ConsultaHttp(fuente, ID_RESUMEN, ruta, parametros("page", "0", "size", "1"), BLOQUE_RESUMEN, false);
    }

    private static ConsultaHttp periodo(Fuente fuente, String ruta, String paramDesde, String paramHasta,
                                        PeriodoConsulta periodo) {
        Map<String, String> parametros = parametros("page", "0", "size", "1");
        if (periodo.tieneRango()) {
            parametros.put(paramDesde, periodo.fechaDesde().toString());
            parametros.put(paramHasta, periodo.fechaHasta().toString());
        }
        return new ConsultaHttp(fuente, ID_PERIODO, ruta, parametros, List.of("summary"), false);
    }

    private static Map<String, String> parametros(String... claveValor) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            mapa.put(claveValor[i], claveValor[i + 1]);
        }
        return mapa;
    }
}
