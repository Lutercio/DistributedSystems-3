# UFRN Responde runbook

## Start and ingest

1. Copy `.env.example` to `.env`, create a Groq API key, and replace every secret placeholder.
2. Set `RELEASE_VERSION` to the release identifier and `BUILD_REVISION` to the exact Git commit being built.
3. Verify the codebase: `.\mvnw.cmd clean verify`.
4. Build the immutable application images once: `docker compose --env-file .env build`.
5. Validate the release configuration: `docker compose --env-file .env config --quiet`.
6. Run the release without rebuilding it: `docker compose --env-file .env up -d --no-build`.
7. When migrating from the former 1536-dimensional OpenAI embeddings, reset only the derived RAG tables once: `docker compose --env-file .env --profile admin run --rm --no-deps rag-vector-reset`.
8. Run the versioned one-off ingestion: `docker compose --env-file .env --profile admin run --rm rag-ingestion`.
9. Confirm all services with `docker compose --env-file .env ps`.

`.env` is a local Compose mechanism and must never be committed. A deployed
environment should inject the same variables through its secret/configuration
facility. Do not use `up --build` for a release because that combines the build
and run stages and makes the running artifact harder to identify.

The ingestion command is idempotent by corpus checksum. It never deletes the
vector table. Embeddings run locally through the internal Ollama service using
`bge-m3:567m`; chat uses Groq. Replacing the embedding model or dimension
requires the explicit reset above and re-ingestion. The reset preserves chat
memory and all unrelated PostgreSQL tables.

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

## Twelve-factor status

See `TWELVE_FACTOR.md` for implemented controls, evidence, and deliberate
exceptions for the monorepo, Config Server, stateful backing services, and
Compose-only release management.
