package com.fleetops.reporting.integration.http;

import com.fleetops.reporting.application.Fuente;
import java.util.List;
import java.util.Map;

/**
 * Una llamada GET a un endpoint que ya existe en el Excel definitivo.
 *
 * @param fuente          fuente a la que pertenece
 * @param id              identificador de la llamada dentro de la fuente
 * @param ruta            ruta del endpoint, por ejemplo /vehiculos
 * @param parametros      parámetros de query, en orden
 * @param camposRequeridos la respuesta debe traer al menos uno de estos campos (por ejemplo summary o resumen);
 *                         si no, la fuente cuenta como fallida (HU-02, criterio 4)
 * @param numerico        true si el campo requerido debe ser un número; false si debe ser un objeto
 */
public record ConsultaHttp(Fuente fuente, String id, String ruta, Map<String, String> parametros,
                           List<String> camposRequeridos, boolean numerico) {
}
