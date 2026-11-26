package com.fleetops.reporting.integration.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fleetops.reporting.config.ReportingProperties;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.json.JsonMapper;

class ConsumidorSqsTest {

    private static final String COLA = "http://localstack:4566/000000000000/reporting_queue";
    private static final String COLA_FIFO = "http://localstack:4566/000000000000/reporting_notifications_queue.fifo";
    private static final String VALIDO = """
            {"event_id":"e-1","event_type":"fuel_record_registered_event",
             "occurred_at":"2026-11-26T10:00:00-05:00","data":{}}""";

    private final SqsClient sqs = mock(SqsClient.class);
    private final ProcesadorEventos procesador = mock(ProcesadorEventos.class);
    private final ConsumidorSqs consumidor = new ConsumidorSqs(sqs, new LectorSobreEvento(JsonMapper.builder().build()),
            procesador, new ReportingProperties(Duration.ofSeconds(3), null, null, null, Map.of(),
            new ReportingProperties.Sqs(true, COLA, COLA_FIFO, null, null, "us-east-1", null, 20, 10)));

    private static Message mensaje(String cuerpo) {
        return Message.builder().messageId("m-1").receiptHandle("r-1").body(cuerpo)
                .attributes(Map.of(MessageSystemAttributeName.MESSAGE_GROUP_ID, "notif-7")).build();
    }

    @Test
    void derivaLasDlqConLaConvencionDeNombres() {
        assertThat(consumidor.colas()).extracting(ColaEventos::dlqUrl).containsExactly(
                "http://localstack:4566/000000000000/reporting_queue_dlq",
                "http://localstack:4566/000000000000/reporting_notifications_queue_dlq.fifo");
        assertThat(consumidor.colas()).extracting(ColaEventos::fifo).containsExactly(false, true);
    }

    @Test
    void leeConLongPollingYBorraElMensajeDespuesDeAplicarlo() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(mensaje(VALIDO)).build());
        when(procesador.procesar(any())).thenReturn(ProcesadorEventos.Resultado.APLICADO);

        consumidor.leerUnaVez(consumidor.colas().get(0));

        ArgumentCaptor<ReceiveMessageRequest> lectura = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqs).receiveMessage(lectura.capture());
        assertThat(lectura.getValue().waitTimeSeconds()).isEqualTo(20);
        assertThat(lectura.getValue().maxNumberOfMessages()).isEqualTo(10);
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void unEventoRepetidoTambienSeBorra() {
        when(procesador.procesar(any())).thenReturn(ProcesadorEventos.Resultado.DUPLICADO);

        consumidor.procesarMensaje(consumidor.colas().get(0), mensaje(VALIDO));

        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void unMensajeMalFormadoVaALaDlqSinReintentos() {
        consumidor.procesarMensaje(consumidor.colas().get(0), mensaje("no es json"));

        ArgumentCaptor<SendMessageRequest> envio = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(envio.capture());
        assertThat(envio.getValue().queueUrl()).endsWith("reporting_queue_dlq");
        assertThat(envio.getValue().messageAttributes()).containsKey("motivo");
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
        verify(procesador, never()).procesar(any());
    }

    @Test
    void enLaColaFifoLaDlqConservaElGrupoYDeduplica() {
        consumidor.procesarMensaje(consumidor.colas().get(1), mensaje("{}"));

        ArgumentCaptor<SendMessageRequest> envio = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(envio.capture());
        assertThat(envio.getValue().messageGroupId()).isEqualTo("notif-7");
        assertThat(envio.getValue().messageDeduplicationId()).isEqualTo("m-1");
    }

    @Test
    void unErrorAlAplicarNoBorraElMensajeParaQueSqsLoReintente() {
        when(procesador.procesar(any())).thenThrow(new IllegalStateException("base caída"));

        consumidor.procesarMensaje(consumidor.colas().get(0), mensaje(VALIDO));

        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(sqs, never()).sendMessage(any(SendMessageRequest.class));
    }
}
