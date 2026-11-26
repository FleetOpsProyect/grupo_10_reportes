package com.fleetops.reporting.application.fuentes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fleetops.reporting.Fixtures;
import com.fleetops.reporting.application.Fuente;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EjecutorConsultasTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(8);
    private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    private final EjecutorConsultas ejecutor = new EjecutorConsultas(pool, CircuitBreakerRegistry.ofDefaults(), metricas,
            Fixtures.props(null, Duration.ofMillis(300), 2, Map.of()));

    @AfterEach
    void cerrar() {
        pool.shutdownNow();
    }

    @Test
    void juntaLosDatosDeLasFuentesQueResponden() {
        var resultados = ejecutor.ejecutar(List.of(
                new SolicitudConsulta(Fuente.VEHICULOS, "resumen", () -> "dato-vehiculos"),
                new SolicitudConsulta(Fuente.TELEMETRIA, "resumen", () -> 42)));

        assertThat(resultados.dato(Fuente.VEHICULOS, "resumen", String.class)).contains("dato-vehiculos");
        assertThat(resultados.dato(Fuente.TELEMETRIA, "resumen", Integer.class)).contains(42);
        assertThat(resultados.codigosNoDisponibles()).isEmpty();
        assertThat(metricas.find("reportes_fuente_latencia").tag("fuente", "vehiculos").tag("resultado", "ok").timer())
                .isNotNull();
    }

    @Test
    void unaFuenteQueFallaQuedaUnaSolaVezEnNoDisponiblesYSusDatosNoSeUsan() {
        var resultados = ejecutor.ejecutar(List.of(
                new SolicitudConsulta(Fuente.VEHICULOS, "resumen", () -> "ok"),
                new SolicitudConsulta(Fuente.DOCUMENTACION, "por_vencer_VEHICLE", () -> 3),
                new SolicitudConsulta(Fuente.DOCUMENTACION, "vencidos_VEHICLE", () -> {
                    throw new FuenteFallidaException("respondió HTTP 500");
                }),
                new SolicitudConsulta(Fuente.DOCUMENTACION, "vencidos_DRIVER", () -> {
                    throw new FuenteFallidaException("respondió HTTP 500");
                })));

        assertThat(resultados.codigosNoDisponibles()).containsExactly("documentacion");
        assertThat(resultados.dato(Fuente.DOCUMENTACION, "por_vencer_VEHICLE", Integer.class)).isEmpty();
        assertThat(resultados.disponible(Fuente.VEHICULOS)).isTrue();
        assertThat(resultados.todasFallaron()).isFalse();
    }

    @Test
    void unaFuenteLentaNoRetieneLaRespuestaMasDeTimeoutMasMedioSegundo() {
        long inicio = System.nanoTime();
        var resultados = ejecutor.ejecutar(List.of(
                new SolicitudConsulta(Fuente.VEHICULOS, "resumen", () -> "ok"),
                new SolicitudConsulta(Fuente.TELEMETRIA, "resumen", () -> {
                    Thread.sleep(5_000);
                    return "tarde";
                })));
        long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

        assertThat(ms).isLessThan(300 + 500 + 200);
        assertThat(resultados.codigosNoDisponibles()).containsExactly("telemetria");
        assertThat(resultados.disponible(Fuente.VEHICULOS)).isTrue();
    }

    @Test
    void conElCircuitoAbiertoNoSeHaceLaLlamada() {
        AtomicInteger llamadas = new AtomicInteger();
        SolicitudConsulta fallida = new SolicitudConsulta(Fuente.COMBUSTIBLE, "periodo", () -> {
            llamadas.incrementAndGet();
            throw new FuenteFallidaException("respondió HTTP 503");
        });

        ejecutor.ejecutar(List.of(fallida));
        ejecutor.ejecutar(List.of(fallida));
        assertThat(ejecutor.circuito(Fuente.COMBUSTIBLE).getState()).isEqualTo(CircuitBreaker.State.OPEN);

        var resultados = ejecutor.ejecutar(List.of(fallida));
        assertThat(llamadas).hasValue(2);
        assertThat(resultados.codigosNoDisponibles()).containsExactly("combustible");
        // La caída de una fuente no abre el circuito de las demás
        assertThat(ejecutor.circuito(Fuente.VEHICULOS).getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void conTodosLosCircuitosAbiertosResponde503EnMenosDeMedioSegundo() {
        List<SolicitudConsulta> todas = java.util.Arrays.stream(Fuente.values())
                .map(f -> new SolicitudConsulta(f, "x", () -> {
                    throw new FuenteFallidaException("caída");
                }))
                .toList();
        ejecutor.ejecutar(todas);
        ejecutor.ejecutar(todas);

        long inicio = System.nanoTime();
        var resultados = ejecutor.ejecutar(todas);

        assertThat(Duration.ofNanos(System.nanoTime() - inicio).toMillis()).isLessThan(500);
        assertThat(resultados.todasFallaron()).isTrue();
    }
}
