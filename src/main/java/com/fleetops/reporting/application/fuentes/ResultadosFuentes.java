package com.fleetops.reporting.application.fuentes;

import com.fleetops.reporting.application.Fuente;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Lo que respondió cada fuente consultada y cuáles fallaron. */
public final class ResultadosFuentes {

    private final Map<Fuente, Map<String, Object>> datos = new EnumMap<>(Fuente.class);
    private final Set<Fuente> consultadas = EnumSet.noneOf(Fuente.class);
    private final Set<Fuente> fallidas = EnumSet.noneOf(Fuente.class);

    public void registrarExito(Fuente fuente, String id, Object dato) {
        consultadas.add(fuente);
        datos.computeIfAbsent(fuente, f -> new HashMap<>()).put(id, dato);
    }

    public void registrarFallo(Fuente fuente) {
        consultadas.add(fuente);
        fallidas.add(fuente);
    }

    /** Una fuente está disponible si se consultó y todas sus llamadas respondieron bien. */
    public boolean disponible(Fuente fuente) {
        return consultadas.contains(fuente) && !fallidas.contains(fuente);
    }

    /** Dato de una llamada; vacío si la fuente falló o no se consultó (el KPI va en null, nunca en 0). */
    public <T> Optional<T> dato(Fuente fuente, String id, Class<T> tipo) {
        if (!disponible(fuente)) {
            return Optional.empty();
        }
        Object valor = datos.getOrDefault(fuente, Map.of()).get(id);
        return tipo.isInstance(valor) ? Optional.of(tipo.cast(valor)) : Optional.empty();
    }

    public Set<Fuente> consultadas() {
        return Collections.unmodifiableSet(consultadas);
    }

    /** Códigos para fuentes_no_disponibles: cada fuente una sola vez y en orden fijo (HU-02, criterio 2). */
    public List<String> codigosNoDisponibles() {
        return fallidas.stream().map(Fuente::codigo).toList();
    }

    public boolean todasFallaron() {
        return !consultadas.isEmpty() && fallidas.containsAll(consultadas);
    }
}
