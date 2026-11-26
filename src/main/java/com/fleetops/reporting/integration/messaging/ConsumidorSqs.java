package com.fleetops.reporting.integration.messaging;

import com.fleetops.reporting.config.ReportingProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Consumidor de las dos colas (HU-02, criterios 8 a 11): un hilo por cola con long polling.
 * <ul>
 *   <li>Evento aplicado o repetido: se borra de la cola.</li>
 *   <li>Mensaje inválido: se copia a la DLQ con el motivo y se borra (sin reintentos).</li>
 *   <li>Cualquier otro error: no se borra; SQS lo vuelve a entregar y, tras 3 intentos, la RedrivePolicy lo
 *       mueve a la DLQ. Nunca se descarta un mensaje en silencio.</li>
 * </ul>
 * Solo se activa con SQS_ENABLED=true.
 */
@Component
@ConditionalOnProperty(name = "reporting.sqs.enabled", havingValue = "true")
public class ConsumidorSqs implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorSqs.class);
    private static final String MDC_CORRELACION = "correlation_id";

    private final SqsClient sqs;
    private final LectorSobreEvento lector;
    private final ProcesadorEventos procesador;
    private final List<ColaEventos> colas = new ArrayList<>();
    private final int espera;
    private final int maxMensajes;
    private final List<Thread> hilos = new ArrayList<>();
    private volatile boolean activo;

    public ConsumidorSqs(SqsClient sqs, LectorSobreEvento lector, ProcesadorEventos procesador,
                         ReportingProperties props) {
        this.sqs = sqs;
        this.lector = lector;
        this.procesador = procesador;
        ReportingProperties.Sqs conf = props.sqs();
        this.espera = Math.clamp(conf.waitSeconds(), 0, 20);
        this.maxMensajes = Math.clamp(conf.maxMessages(), 1, 10);
        agregar("reporting_queue", conf.queueUrl(), conf.dlqUrl());
        agregar("reporting_notifications_queue.fifo", conf.notificationsQueueUrl(), conf.notificationsDlqUrl());
    }

    private void agregar(String nombre, String url, String dlq) {
        if (url == null || url.isBlank()) {
            log.warn("La cola {} no tiene URL configurada: no se consumirá", nombre);
            return;
        }
        colas.add(ColaEventos.de(nombre, url, dlq));
    }

    @Override
    public void start() {
        activo = true;
        for (ColaEventos cola : colas) {
            hilos.add(Thread.ofVirtual().name("sqs-" + cola.nombre()).start(() -> escuchar(cola)));
        }
    }

    @Override
    public void stop() {
        activo = false;
        hilos.forEach(Thread::interrupt);
        hilos.clear();
    }

    @Override
    public boolean isRunning() {
        return activo;
    }

    private void escuchar(ColaEventos cola) {
        log.info("Escuchando la cola {}", cola.nombre());
        while (activo) {
            try {
                leerUnaVez(cola);
            } catch (RuntimeException e) {
                if (!activo) {
                    return;
                }
                log.error("Error leyendo la cola {}: {}", cola.nombre(), e.getMessage());
                pausar();
            }
        }
    }

    /** Una lectura de la cola (long polling) y el procesamiento de lo que llegó. */
    void leerUnaVez(ColaEventos cola) {
        List<Message> mensajes = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(cola.url())
                .waitTimeSeconds(espera)
                .maxNumberOfMessages(maxMensajes)
                .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT,
                        MessageSystemAttributeName.MESSAGE_GROUP_ID)
                .build()).messages();
        mensajes.forEach(m -> procesarMensaje(cola, m));
    }

    void procesarMensaje(ColaEventos cola, Message mensaje) {
        SobreEvento evento = null;
        try {
            evento = lector.leer(mensaje.body());
            MDC.put(MDC_CORRELACION, evento.correlationId());
            ProcesadorEventos.Resultado resultado = procesador.procesar(evento);
            borrar(cola, mensaje);
            log.atInfo()
                    .addKeyValue("event_id", evento.eventId())
                    .addKeyValue("event_type", evento.eventType())
                    .addKeyValue("source", evento.source())
                    .addKeyValue("resultado", resultado.name())
                    .log("Evento {} {} ({})", evento.eventType(), resultado == ProcesadorEventos.Resultado.APLICADO
                            ? "aplicado" : "repetido, se ignora", evento.eventId());
        } catch (MensajeInvalidoException e) {
            log.atWarn()
                    .addKeyValue("event_id", evento == null ? null : evento.eventId())
                    .addKeyValue("message_id", mensaje.messageId())
                    .log("Mensaje inválido en {}, va a la DLQ sin reintentos: {}", cola.nombre(), e.getMessage());
            enviarADlq(cola, mensaje, e.getMessage());
            borrar(cola, mensaje);
        } catch (RuntimeException e) {
            log.atError()
                    .addKeyValue("event_id", evento == null ? null : evento.eventId())
                    .addKeyValue("message_id", mensaje.messageId())
                    .addKeyValue("intento", mensaje.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT))
                    .log("No se pudo aplicar el mensaje de {}; SQS lo reintentará: {}", cola.nombre(), e.getMessage());
        } finally {
            MDC.remove(MDC_CORRELACION);
        }
    }

    private void enviarADlq(ColaEventos cola, Message mensaje, String motivo) {
        SendMessageRequest.Builder envio = SendMessageRequest.builder()
                .queueUrl(cola.dlqUrl())
                .messageBody(mensaje.body())
                .messageAttributes(Map.of("motivo", MessageAttributeValue.builder()
                        .dataType("String").stringValue(motivo).build()));
        if (cola.fifo()) {
            String grupo = mensaje.attributes().getOrDefault(MessageSystemAttributeName.MESSAGE_GROUP_ID, "invalidos");
            envio.messageGroupId(grupo).messageDeduplicationId(mensaje.messageId());
        }
        sqs.sendMessage(envio.build());
    }

    private void borrar(ColaEventos cola, Message mensaje) {
        sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(cola.url()).receiptHandle(mensaje.receiptHandle()).build());
    }

    private static void pausar() {
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    List<ColaEventos> colas() {
        return List.copyOf(colas);
    }
}
