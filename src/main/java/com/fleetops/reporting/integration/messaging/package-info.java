/**
 * Integración asíncrona: consumo de eventos AWS SNS desde la cola SQS reporting_queue.
 * <p>
 * En el MVP solo se consumen dos eventos que ya existen y que ya listan a Reportes como suscriptor:
 * <ul>
 *   <li>fuel_record_registered_event (topic fleetops_fuel_events, Combustible): costo por vehículo y
 *       rendimiento promedio del período.</li>
 *   <li>vehicle_odometer_updated_event (topic fleetops_vehicle_events, Vehículos): kilómetros recorridos por
 *       período (distance_km de cada ruta finalizada).</li>
 * </ul>
 * Va aquí: el consumidor SQS (long polling), el sobre común del evento (event_id, event_type, occurred_at,
 * source, correlation_id, data) y un handler por evento. Reglas: idempotencia por event_id, borrar el mensaje
 * solo después de confirmar en BD, reintentos y DLQ.
 */
package com.fleetops.reporting.integration.messaging;
