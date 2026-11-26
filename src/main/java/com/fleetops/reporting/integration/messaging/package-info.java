/**
 * Integración asíncrona: consumo de eventos AWS SNS desde dos colas SQS (modelo híbrido, backlog V5.0).
 * <p>
 * Todos los eventos ya existen en el Excel definitivo; ningún equipo tiene que crear nada nuevo.
 * <p>
 * Cola estándar reporting_queue:
 * <ul>
 *   <li>vehicle_status_changed_event (fleetops_vehicle_events, Vehículos): tiempo disponible y fuera de
 *       servicio por vehículo. Se ignora un evento con version_number menor o igual al ya aplicado.</li>
 *   <li>vehicle_odometer_updated_event (fleetops_vehicle_events, Vehículos): kilómetros por período.</li>
 *   <li>maintenance_completed_event (fleetops_maintenance_events, Mantenimiento): mantenimientos
 *       completados por período y por vehículo.</li>
 *   <li>fuel_record_registered_event (fleetops_fuel_events, Combustible): costo, galones, anomalías,
 *       top de vehículos y rendimiento del período.</li>
 *   <li>minor_incident_reported_event y severe_incident_reported_event (fleetops_incident_events,
 *       Incidentes): incidentes por período y por vehículo.</li>
 * </ul>
 * Cola FIFO reporting_notifications_queue.fifo (el topic fleetops_notification_events.fifo es FIFO):
 * <ul>
 *   <li>notification_created/sent/failed/acknowledged_event (Notificaciones): alertas y envíos del período y
 *       tiempo promedio de atención por alert_id.</li>
 * </ul>
 * Va aquí: un consumidor SQS por cola (long polling), el sobre común del evento (event_id, event_type,
 * occurred_at, source, correlation_id, data) y un handler por evento. Reglas: idempotencia por event_id, borrar
 * el mensaje solo después de confirmar en BD, reintentos y DLQ.
 */
package com.fleetops.reporting.integration.messaging;
