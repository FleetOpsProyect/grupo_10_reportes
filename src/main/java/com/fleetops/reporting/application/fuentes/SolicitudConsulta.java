package com.fleetops.reporting.application.fuentes;

import com.fleetops.reporting.application.Fuente;
import java.util.concurrent.Callable;

/**
 * Una llamada a una fuente. Una fuente puede tener varias (por ejemplo, Documentación hace cuatro); si falla
 * cualquiera, toda la fuente cuenta como no disponible.
 *
 * @param fuente fuente a la que pertenece
 * @param id     identificador de la llamada dentro de la fuente (lo usan los cálculos de KPIs para leer su dato)
 * @param accion la llamada; devuelve el dato leído o lanza {@link FuenteFallidaException}
 */
public record SolicitudConsulta(Fuente fuente, String id, Callable<Object> accion) {
}
