package com.fleetops.reporting.application.fuentes;

import com.fleetops.reporting.application.Fuente;
import com.fleetops.reporting.config.ReportingProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Scatter-Gather (HU-02, criterios 13 a 17): lanza todas las llamadas en paralelo en un pool propio, cada una
 * protegida por el Circuit Breaker de su fuente, y espera como máximo timeout + 500 ms en total. Lo que no
 * respondió a tiempo cuenta como fuente no disponible.
 */
@Component
public class EjecutorConsultas {

    private static final Logger log = LoggerFactory.getLogger(EjecutorConsultas.class);
    private static final Duration MARGEN = Duration.ofMillis(500);

    private final ExecutorService ejecutor;
    private final CircuitBreakerRegistry registroCircuitos;
    private final CircuitBreakerConfig configCircuito;
    private final MeterRegistry metricas;
    private final Duration timeout;

    public EjecutorConsultas(ExecutorService ejecutorFuentes, CircuitBreakerRegistry registroCircuitos,
                             MeterRegistry metricas, ReportingProperties props) {
        this.ejecutor = ejecutorFuentes;
        this.registroCircuitos = registroCircuitos;
        this.metricas = metricas;
        this.timeout = props.timeout();
        int fallos = Math.max(1, props.circuitBreaker().fallos());
        // Ventana de las últimas N llamadas con umbral del 100 %: el circuito se abre con N fallos seguidos.
        this.configCircuito = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(fallos)
                .minimumNumberOfCalls(fallos)
                .failureRateThreshold(100)
                .waitDurationInOpenState(props.circuitBreaker().espera())
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();
    }

    public ResultadosFuentes ejecutar(List<SolicitudConsulta> solicitudes) {
        long limite = System.nanoTime() + timeout.plus(MARGEN).toNanos();
        List<CompletableFuture<Object>> futuros = new ArrayList<>(solicitudes.size());
        for (SolicitudConsulta solicitud : solicitudes) {
            futuros.add(lanzar(solicitud));
        }
        ResultadosFuentes resultados = new ResultadosFuentes();
        for (int i = 0; i < solicitudes.size(); i++) {
            recoger(solicitudes.get(i), futuros.get(i), limite, resultados);
        }
        return resultados;
    }

    private static void recoger(SolicitudConsulta solicitud, CompletableFuture<Object> futuro, long limite,
                                ResultadosFuentes resultados) {
        try {
            long restante = Math.max(0, limite - System.nanoTime());
            resultados.registrarExito(solicitud.fuente(), solicitud.id(), futuro.get(restante, TimeUnit.NANOSECONDS));
        } catch (TimeoutException e) {
            futuro.cancel(true);
            log.warn("Tiempo de espera agotado al consultar la fuente {}", solicitud.fuente().codigo());
            resultados.registrarFallo(solicitud.fuente());
        } catch (ExecutionException e) {
            registrarFallo(solicitud, e.getCause(), resultados);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resultados.registrarFallo(solicitud.fuente());
        }
    }

    private CompletableFuture<Object> lanzar(SolicitudConsulta solicitud) {
        CircuitBreaker circuito = circuito(solicitud.fuente());
        return CompletableFuture.supplyAsync(() -> {
            Timer.Sample muestra = Timer.start(metricas);
            String resultado = "error";
            try {
                Object dato = circuito.executeCallable(solicitud.accion());
                resultado = "ok";
                return dato;
            } catch (CallNotPermittedException e) {
                resultado = "circuito_abierto";
                throw e;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                muestra.stop(Timer.builder("reportes_fuente_latencia")
                        .description("Latencia de cada llamada a una fuente")
                        .tag("fuente", solicitud.fuente().codigo())
                        .tag("resultado", resultado)
                        .register(metricas));
            }
        }, ejecutor);
    }

    private static void registrarFallo(SolicitudConsulta solicitud, Throwable causa, ResultadosFuentes resultados) {
        Throwable raiz = causa instanceof CompletionException && causa.getCause() != null ? causa.getCause() : causa;
        String fuente = solicitud.fuente().codigo();
        if (raiz instanceof CallNotPermittedException) {
            log.warn("Circuito abierto para la fuente {}", fuente);
        } else if (raiz instanceof HttpTimeoutException || raiz instanceof TimeoutException) {
            log.warn("Tiempo de espera agotado al consultar la fuente {}", fuente);
        } else {
            log.atWarn().addKeyValue("fuente", fuente).addKeyValue("consulta", solicitud.id())
                    .log("La fuente {} falló: {}", fuente, raiz == null ? "sin detalle" : raiz.getMessage());
        }
        resultados.registrarFallo(solicitud.fuente());
    }

    /** Circuit Breaker de una fuente; se crea la primera vez con la configuración de REPORTES_CB_*. */
    public CircuitBreaker circuito(Fuente fuente) {
        return registroCircuitos.circuitBreaker(fuente.codigo(), configCircuito);
    }
}
