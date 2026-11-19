#!/bin/bash
# Crea en LocalStack la misma topología que en AWS: cola + DLQ y los topics de los que consume Reportes (MVP).
#   fleetops_fuel_events    (Combustible, G8): fuel_record_registered_event
#   fleetops_vehicle_events (Vehículos, G7):   vehicle_odometer_updated_event
# Cada suscripción filtra por el message attribute event_type, así la cola solo recibe esos dos eventos.
set -e

awslocal sqs create-queue --queue-name reporting_queue_dlq
DLQ_ARN=$(awslocal sqs get-queue-attributes --queue-url http://localhost:4566/000000000000/reporting_queue_dlq \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

awslocal sqs create-queue --queue-name reporting_queue \
  --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"
QUEUE_ARN=$(awslocal sqs get-queue-attributes --queue-url http://localhost:4566/000000000000/reporting_queue \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

suscribir() {
  local topic="$1" evento="$2"
  local topic_arn
  topic_arn=$(awslocal sns create-topic --name "$topic" --query 'TopicArn' --output text)
  awslocal sns subscribe --topic-arn "$topic_arn" --protocol sqs --notification-endpoint "$QUEUE_ARN" \
    --attributes "{\"RawMessageDelivery\":\"true\",\"FilterPolicy\":\"{\\\"event_type\\\":[\\\"${evento}\\\"]}\"}"
}

suscribir fleetops_fuel_events fuel_record_registered_event
suscribir fleetops_vehicle_events vehicle_odometer_updated_event
