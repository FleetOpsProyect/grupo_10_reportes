#!/bin/bash
# Crea en LocalStack la misma topología que en AWS (modelo híbrido, backlog V5.0).
# Cola estándar reporting_queue (+ DLQ):
#   fleetops_vehicle_events     (Vehículos, G7):     vehicle_status_changed_event, vehicle_odometer_updated_event
#   fleetops_maintenance_events (Mantenimiento, G3): maintenance_completed_event
#   fleetops_fuel_events        (Combustible, G8):   fuel_record_registered_event
#   fleetops_incident_events    (Incidentes, G5):    minor_incident_reported_event, severe_incident_reported_event
# Cola FIFO reporting_notifications_queue.fifo (+ DLQ FIFO), porque un topic FIFO solo entrega a colas FIFO:
#   fleetops_notification_events.fifo (Notificaciones, G4): notification_created/sent/failed/acknowledged_event
# Cada suscripción filtra por el message attribute event_type, así las colas solo reciben esos eventos.
set -e

BASE=http://localhost:4566/000000000000

arn_de() {
  awslocal sqs get-queue-attributes --queue-url "$BASE/$1" --attribute-names QueueArn \
    --query 'Attributes.QueueArn' --output text
}

redrive() {
  echo "\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$1\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\""
}

# Cola estándar
awslocal sqs create-queue --queue-name reporting_queue_dlq
awslocal sqs create-queue --queue-name reporting_queue --attributes "{$(redrive "$(arn_de reporting_queue_dlq)")}"
QUEUE_ARN=$(arn_de reporting_queue)

# Cola FIFO
awslocal sqs create-queue --queue-name reporting_notifications_queue_dlq.fifo --attributes '{"FifoQueue":"true"}'
awslocal sqs create-queue --queue-name reporting_notifications_queue.fifo \
  --attributes "{\"FifoQueue\":\"true\",$(redrive "$(arn_de reporting_notifications_queue_dlq.fifo)")}"
FIFO_ARN=$(arn_de reporting_notifications_queue.fifo)

# suscribir <topic> <arn de la cola> <evento> [evento...]
suscribir() {
  local topic="$1" cola="$2"
  shift 2
  local eventos topic_arn atributos_topic=""
  eventos=$(printf '\\\"%s\\\",' "$@")
  eventos="${eventos%,}"
  [[ "$topic" == *.fifo ]] && atributos_topic='--attributes FifoTopic=true'
  # shellcheck disable=SC2086
  topic_arn=$(awslocal sns create-topic --name "$topic" $atributos_topic --query 'TopicArn' --output text)
  awslocal sns subscribe --topic-arn "$topic_arn" --protocol sqs --notification-endpoint "$cola" \
    --attributes "{\"RawMessageDelivery\":\"true\",\"FilterPolicy\":\"{\\\"event_type\\\":[${eventos}]}\"}"
}

suscribir fleetops_vehicle_events "$QUEUE_ARN" vehicle_status_changed_event vehicle_odometer_updated_event
suscribir fleetops_maintenance_events "$QUEUE_ARN" maintenance_completed_event
suscribir fleetops_fuel_events "$QUEUE_ARN" fuel_record_registered_event
suscribir fleetops_incident_events "$QUEUE_ARN" minor_incident_reported_event severe_incident_reported_event
suscribir fleetops_notification_events.fifo "$FIFO_ARN" \
  notification_created_event notification_sent_event notification_failed_event notification_acknowledged_event
