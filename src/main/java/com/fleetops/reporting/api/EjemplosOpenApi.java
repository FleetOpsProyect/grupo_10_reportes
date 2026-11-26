package com.fleetops.reporting.api;

/** Ejemplos de respuesta para la documentación OpenAPI (HU-01, criterio 14). Valores ilustrativos. */
final class EjemplosOpenApi {

    static final String COMPLETO = """
            {
              "total_flota": 150, "vehiculos_disponibles": 108, "vehiculos_en_asignacion": 30,
              "porcentaje_disponibilidad_flota": "72.0%", "porcentaje_flota_operativa": "92.0%",
              "ordenes_abiertas": 7, "porcentaje_mantenimiento_preventivo": "89.36%",
              "costo_total_combustible": 2350000.50, "incidentes_total": 4, "alertas_criticas_pendientes": 2,
              "periodo": {"rango": "MES_ACTUAL", "fecha_desde": "2026-11-01", "fecha_hasta": "2026-11-26",
                          "cubierto_por_eventos": true},
              "fuentes_no_disponibles": [],
              "fecha_generacion": "2026-11-26T10:15:00-05:00"
            }""";

    static final String PARCIAL = """
            {
              "total_flota": 150, "vehiculos_disponibles": 108,
              "costo_total_combustible": null, "galones_totales": null, "registros_consumo_anomalo": null,
              "periodo": {"rango": null, "fecha_desde": null, "fecha_hasta": null, "cubierto_por_eventos": false},
              "fuentes_no_disponibles": ["combustible"],
              "fecha_generacion": "2026-11-26T10:15:00-05:00"
            }""";

    static final String RANGO_INVALIDO = """
            {"codigo": "RANGO_INVALIDO", "mensaje": "fecha_desde no puede ser posterior a fecha_hasta",
             "timestamp": "2026-11-26T10:15:00-05:00", "ruta": "/reportes/disponibilidad",
             "correlation_id": "3f2b6c1e-8d4a-4f7e-9b1a-2c5d6e7f8a9b"}""";

    static final String SIN_FUENTES = """
            {"codigo": "FUENTES_NO_DISPONIBLES",
             "mensaje": "Ninguna fuente de datos está disponible para generar el reporte",
             "timestamp": "2026-11-26T10:15:00-05:00", "ruta": "/reportes/disponibilidad",
             "correlation_id": "3f2b6c1e-8d4a-4f7e-9b1a-2c5d6e7f8a9b"}""";

    private EjemplosOpenApi() {
    }
}
