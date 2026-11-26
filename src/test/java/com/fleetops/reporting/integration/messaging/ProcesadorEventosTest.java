package com.fleetops.reporting.integration.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fleetops.reporting.Fixtures;
import com.fleetops.reporting.integration.persistence.EventoProcesado;
import com.fleetops.reporting.integration.persistence.EventoProcesadoRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

class ProcesadorEventosTest {

    private final EventoProcesadoRepository repositorio = mock(EventoProcesadoRepository.class);
    private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    private final List<SobreEvento> aplicados = new ArrayList<>();
    private ProcesadorEventos procesador;

    private final ManejadorEvento manejadorCombustible = new ManejadorEvento() {
        @Override
        public String tipoEvento() {
            return "fuel_record_registered_event";
        }

        @Override
        public String proyeccion() {
            return "proyeccion_combustible";
        }

        @Override
        public void aplicar(SobreEvento evento) {
            aplicados.add(evento);
        }
    };

    @BeforeEach
    void preparar() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ManejadorEvento> proveedor = mock(ObjectProvider.class);
        when(proveedor.orderedStream()).thenAnswer(i -> Stream.of(manejadorCombustible));
        procesador = new ProcesadorEventos(repositorio, proveedor, metricas, Fixtures.RELOJ);
    }

    private static SobreEvento evento(String id, String tipo) {
        return new SobreEvento(id, tipo, OffsetDateTime.parse("2026-11-26T09:00:00-05:00"), "combustible", "c",
                JsonMapper.builder().build().createObjectNode());
    }

    @Test
    void aplicaElEventoYGuardaSuEventId() {
        when(repositorio.existsById("e-1")).thenReturn(false);

        var resultado = procesador.procesar(evento("e-1", "fuel_record_registered_event"));

        assertThat(resultado).isEqualTo(ProcesadorEventos.Resultado.APLICADO);
        assertThat(aplicados).hasSize(1);
        ArgumentCaptor<EventoProcesado> guardado = ArgumentCaptor.forClass(EventoProcesado.class);
        verify(repositorio).save(guardado.capture());
        assertThat(guardado.getValue().getEventId()).isEqualTo("e-1");
        assertThat(guardado.getValue().getEventType()).isEqualTo("fuel_record_registered_event");
        // 10:00 del reloj fijo - 09:00 del evento = 3600 s
        assertThat(metricas.get("reportes_evento_antiguedad_segundos").tag("proyeccion", "proyeccion_combustible")
                .gauge().value()).isEqualTo(3600);
    }

    @Test
    void unEventIdRepetidoNoSeAplicaOtraVez() {
        when(repositorio.existsById("e-1")).thenReturn(true);

        var resultado = procesador.procesar(evento("e-1", "fuel_record_registered_event"));

        assertThat(resultado).isEqualTo(ProcesadorEventos.Resultado.DUPLICADO);
        assertThat(aplicados).isEmpty();
        verify(repositorio, never()).save(any());
    }

    @Test
    void unTipoSinManejadorEsInvalidoYNoSeDescartaEnSilencio() {
        assertThatThrownBy(() -> procesador.procesar(evento("e-2", "evento_desconocido")))
                .isInstanceOf(MensajeInvalidoException.class);
        verify(repositorio, never()).save(any());
    }
}
