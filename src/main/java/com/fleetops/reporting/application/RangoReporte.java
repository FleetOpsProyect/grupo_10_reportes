package com.fleetops.reporting.application;

import java.util.Arrays;
import java.util.Optional;

/** Valores aceptados del parámetro {@code rango} (backlog HU-01, criterio 3). */
public enum RangoReporte {
    HOY, SEMANA_ACTUAL, MES_ACTUAL, PERSONALIZADO;

    /** Busca el rango por su nombre exacto (en mayúsculas). */
    public static Optional<RangoReporte> desde(String valor) {
        return Arrays.stream(values()).filter(r -> r.name().equals(valor)).findFirst();
    }
}
