# Notification Service

Phase-2 email notification worker for the ecommerce event stream. It consumes the
shared Kafka event envelope, creates a durable MongoDB `notification_logs` record
for consumer-side idempotency, and sends rendered email through a pluggable provider.

## Local development

The default provider is `log`, which records only metadata (recipient, template and
event id) and reports a simulated success. It deliberately never prints the email
body or verification/reset links. This makes `bootRun` safe without SMTP credentials.

The default queue is `mongo`: pending jobs are claimed atomically from MongoDB by a
scheduled worker. It is a durable local-development fallback and restart recovery
mechanism. Production should set `NOTIFICATION_QUEUE_TYPE=redis`; then a Redis sorted
set schedules due jobs while MongoDB remains the source of truth for logs and dedup.

To use SMTP in a non-local environment:

```properties
NOTIFICATION_EMAIL_PROVIDER=smtp
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=...
SPRING_MAIL_PASSWORD=...
NOTIFICATION_EMAIL_FROM=no-reply@example.com
```

Kafka DLQ publishing is opt-in locally (`NOTIFICATION_DLQ_PUBLISH_ENABLED=false` by
default). Every terminal failure is still recorded in MongoDB collection
`notification_dead_letters`; enable the flag in deployment to also publish the
metadata envelope to Kafka topic `notification.dlq`.

Run with an existing Gradle wrapper from the repository root, for example:

```powershell
..\Product-Catalog-Service\gradlew.bat -p . test
..\Product-Catalog-Service\gradlew.bat -p . bootRun
```

## Event contract

The consumer expects the versioned envelope documented in `docs/requirements/08-external-interfaces.md`:

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreatedEvent",
  "version": 1,
  "occurredAt": "2026-08-11T12:00:00Z",
  "traceId": "...",
  "data": { "email": "buyer@example.com", "orderId": "..." }
}
```

Order and payment events must include one of `email`, `recipientEmail`, `userEmail`,
or `customerEmail` in `data`. Identity events must include `verifyLink` or `resetLink`
respectively. Unknown events are acknowledged without side effects so this service can
consume the shared topic safely.
