package com.fleetops.reporting.integration.messaging;

import com.fleetops.reporting.integration.persistence.EventoProcesado;
import com.fleetops.reporting.integration.persistence.EventoProcesadoRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica un evento con idempotencia (HU-02, criterio 9): el cambio en la proyección y el event_id en
 * eventos_procesados se guardan en la misma transacción. Un event_id repetido no se aplica otra vez.
 */
@Service
public class ProcesadorEventos {

    /** Qué pasó con un evento. En los dos casos el mensaje se puede borrar de la cola. */
    public enum Resultado { APLICADO, DUPLICADO }

    private final EventoProcesadoRepository repositorio;
    private final Map<String, ManejadorEvento> manejadores;
    private final Map<String, AtomicReference<Instant>> ultimoPorProyeccion = new ConcurrentHashMap<>();
    private final MeterRegistry metricas;
    private final Clock reloj;

    public ProcesadorEventos(EventoProcesadoRepository repositorio, ObjectProvider<ManejadorEvento> manejadores,
                             MeterRegistry metricas, Clock reloj) {
        this.repositorio = repositorio;
        this.manejadores = manejadores.orderedStream()
                .collect(Collectors.toUnmodifiableMap(ManejadorEvento::tipoEvento, Function.identity()));
        this.metricas = metricas;
        this.reloj = reloj;
    }

    @Transactional
    public Resultado procesar(SobreEvento evento) {
        if (repositorio.existsById(evento.eventId())) {
            return Resultado.DUPLICADO;
        }
        ManejadorEvento manejador = manejadores.get(evento.eventType());
        if (manejador == null) {
            throw new MensajeInvalidoException("no hay manejador para " + evento.eventType());
        }
        manejador.aplicar(evento);
        repositorio.save(new EventoProcesado(evento.eventId(), evento.eventType(), evento.source(),
                evento.occurredAt(), OffsetDateTime.now(reloj)));
        registrarUltimo(manejador.proyeccion(), evento.occurredAt().toInstant());
        return Resultado.APLICADO;
    }

    /** Gauge reportes_evento_antiguedad_segundos{proyeccion}: segundos desde el último evento aplicado. */
    private void registrarUltimo(String proyeccion, Instant ocurrido) {
        ultimoPorProyeccion.computeIfAbsent(proyeccion, p -> {
            AtomicReference<Instant> ref = new AtomicReference<>(ocurrido);
            Gauge.builder("reportes_evento_antiguedad_segundos", ref,
                            r -> Duration.between(r.get(), reloj.instant()).toSeconds())
                    .description("Segundos desde el último evento aplicado a la proyección")
                    .tag("proyeccion", p)
                    .register(metricas);
            return ref;
        }).accumulateAndGet(ocurrido, (actual, nuevo) -> nuevo.isAfter(actual) ? nuevo : actual);
    }
}
