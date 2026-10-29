#!/bin/bash
# Crea en LocalStack la misma topología que en AWS: cola + DLQ y los topics de los que consume Reportes.
set -e

awslocal sqs create-queue --queue-name reporting_queue_dlq
DLQ_ARN=$(awslocal sqs get-queue-attributes --queue-url http://localhost:4566/000000000000/reporting_queue_dlq \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

awslocal sqs create-queue --queue-name reporting_queue \
  --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"
QUEUE_ARN=$(awslocal sqs get-queue-attributes --queue-url http://localhost:4566/000000000000/reporting_queue \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

for TOPIC in fleetops_document_events fleetops_fuel_events fleetops_notification_events; do
  TOPIC_ARN=$(awslocal sns create-topic --name "$TOPIC" --query 'TopicArn' --output text)
  awslocal sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs --notification-endpoint "$QUEUE_ARN" \
    --attributes RawMessageDelivery=true
done
