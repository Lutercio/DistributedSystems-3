# UFRN Responde runbook

## Start and ingest

1. Copy `.env.example` to `.env` and replace every secret placeholder.
2. Start the platform: `docker compose --env-file .env up -d --build`.
3. Run the versioned one-off ingestion: `docker compose --env-file .env --profile admin run --rm rag-ingestion`.
4. Confirm all services with `docker compose --env-file .env ps`.

The ingestion command is idempotent by corpus checksum. It never deletes the
vector table. Replacing the embedding model or dimension requires an explicit
database migration and re-ingestion.

## Functional checks

- Public API: `POST http://localhost:8080/question-service/api/questions`
- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090`
- Zipkin: `http://localhost:9411`

All functional and load tests must use the Gateway URL. Run JMeter from the
`jmeter/` directory so `questions.csv` resolves correctly. Override the URL
with `-JbaseUrl=http://host:port` when necessary.

## Resilience demonstration

Run `resilience.jmx`, stop one `rag-service` or `question-service` container,
observe failures and the circuit breaker transition in Grafana, then restore
the container and verify `HALF_OPEN` to `CLOSED` recovery.

Do not include Tavily traffic in the primary Knee Capacity measurement.
