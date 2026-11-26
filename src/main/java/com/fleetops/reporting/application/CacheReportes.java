package com.fleetops.reporting.application;

import com.fleetops.reporting.config.ReportingProperties;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Caché opcional de reportes completos (HU-01, criterio 11): dura REPORTES_CACHE_TTL_S y la clave incluye el
 * rango. Un reporte parcial nunca se guarda. Con TTL 0 no guarda nada.
 */
@Component
public class CacheReportes {

    private record Entrada(ReporteDisponibilidad reporte, long expiraNanos) {
    }

    private final Map<String, Entrada> entradas = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final LongSupplier relojNanos;

    @Autowired
    public CacheReportes(ReportingProperties props) {
        this(props.cacheTtl(), System::nanoTime);
    }

    CacheReportes(Duration ttl, LongSupplier relojNanos) {
        this.ttl = ttl;
        this.relojNanos = relojNanos;
    }

    public Optional<ReporteDisponibilidad> buscar(PeriodoConsulta periodo) {
        if (!activa()) {
            return Optional.empty();
        }
        Entrada entrada = entradas.get(periodo.claveCache());
        if (entrada == null) {
            return Optional.empty();
        }
        if (relojNanos.getAsLong() - entrada.expiraNanos() >= 0) {
            entradas.remove(periodo.claveCache(), entrada);
            return Optional.empty();
        }
        return Optional.of(entrada.reporte());
    }

    public void guardar(PeriodoConsulta periodo, ReporteDisponibilidad reporte) {
        if (activa() && reporte.esCompleto()) {
            entradas.put(periodo.claveCache(), new Entrada(reporte, relojNanos.getAsLong() + ttl.toNanos()));
        }
    }

    private boolean activa() {
        return !ttl.isZero() && !ttl.isNegative();
    }
}
