package com.fleetops.reporting;

import com.fleetops.reporting.config.ReportingProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

/** Datos comunes de las pruebas. */
public final class Fixtures {

    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    /** Jueves 26/11/2026, 10:00 en Colombia. */
    public static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-26T15:00:00Z"), BOGOTA);

    private Fixtures() {
    }

    public static ReportingProperties props(String eventosDesde, Duration timeout, int fallos, Map<String, String> fuentes) {
        return new ReportingProperties(timeout, new ReportingProperties.CircuitBreaker(fallos, Duration.ofSeconds(30)),
                Duration.ZERO, eventosDesde, fuentes, null);
    }

    public static ReportingProperties props(String eventosDesde) {
        return props(eventosDesde, Duration.ofMillis(500), 5, Map.of());
    }
}
