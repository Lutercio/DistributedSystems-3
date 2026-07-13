# Question Service

Question Service coordinates normalization and RAG calls. Each downstream has
an independent Resilience4j circuit breaker in every Question Service replica.

## Automatic circuit-breaker recovery

The Compose stack automatically probes a downstream health endpoint while its
breaker is half open. The probes use the same breaker instance as application
traffic, so the three permitted trial calls evaluate recovery without another
question request. Excessive failed probes return the breaker to its configured
open-state cooldown.

The configured targets are `ragService` at
`http://rag-service/actuator/health` and `normalizer` at
`http://question-normalizer/actuator/health`. Disable recovery with:

```properties
QUESTION_SERVICE_CIRCUIT_BREAKER_RECOVERY_ENABLED=false
```

Prometheus exposes probe counts as
`question_circuitbreaker_recovery_probes_total`, tagged by `breaker` and
`outcome`. The effective settings are under
`app.question-service.circuit-breaker-recovery` in centralized configuration.
