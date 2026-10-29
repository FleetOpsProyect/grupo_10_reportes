/**
 * Integración asíncrona: consumo de eventos AWS SNS desde la cola SQS reporting_queue.
 * <p>
 * Va aquí: el consumidor SQS (long polling), el sobre común del evento (event_id, event_type, occurred_at,
 * source, correlation_id, data) y un handler por familia de eventos (documentos, combustible, notificaciones).
 * Reglas: idempotencia por event_id, borrar el mensaje solo después de confirmar en BD, reintentos y DLQ.
 */
package com.fleetops.reporting.integration.messaging;
