# Twelve-factor assessment

This assessment applies to the Docker Compose deployment. It distinguishes the
application processes from attached infrastructure because databases, model
runtimes, discovery, and observability have different state and lifecycle
requirements.

| Factor | Status | Implementation and evidence | Deliberate limitation |
|---|---|---|---|
| I. Codebase | Partial | One Git repository produces independently tagged service images from separate Maven modules. Modules do not share runtime state. | Strict one-codebase-per-service is not used. The monorepo keeps the academic system, configuration, and release revision atomic. |
| II. Dependencies | Compliant | The Maven wrapper, parent BOMs, Enforcer rules, multi-stage Dockerfiles, explicit image versions, and available image digests declare runtime dependencies. | The Ollama model registry is tag-based, so `bge-m3:567m` must be revalidated when its local volume is recreated. Some third-party images use fixed versions when a verified index digest is unavailable. |
| III. Config | Pragmatic | Secrets are required environment variables. Deploy-varying resource handles are environment-overridable. Config Server stores only non-secret policy outside application images. | Strict environment-only configuration is not used because Config Server and refresh behavior are intentional distributed-system features. `.env` is for local Compose only. |
| IV. Backing services | Compliant | PostgreSQL, Ollama, Groq, Tavily, MCP, Eureka, Zipkin, Prometheus, and Config Git are attached through configurable resource handles or Compose DNS. Changing an attachment does not require rebuilding an application image. | Local Compose supplies concrete defaults, which a deployment must override when a resource moves. |
| V. Build, release, run | Partial | Maven verification and `docker compose build` create tagged images with version/revision OCI labels. `docker compose up --no-build` runs the selected release. | Compose does not provide a release registry, promotion record, or immutable deployment controller; operators must preserve tags and revisions. |
| VI. Processes | Application-compliant | Gateway, Question Service, RAG, Normalizer, MCP, Config Server, and Eureka containers do not store authoritative business state in their writable filesystems. Chat memory and vectors live in PostgreSQL; model files live in the Ollama volume. | PostgreSQL, Ollama, Config Git, Prometheus, and Grafana are intentionally stateful backing services. Eureka keeps an ephemeral replicated registry. |
| VII. Port binding | Application-compliant | Every Java process binds the environment-provided `PORT` and exports HTTP directly. Only the edge and operational UIs are published to localhost. | Third-party images use their standard internal ports and are configured at the attachment boundary instead. |
| VIII. Concurrency | Partial | Gateway, Question Service, RAG, Eureka, and HAProxy scale by adding processes. Business state is external, so replicas are interchangeable. | Local PostgreSQL and Ollama are single instances. Normalizer and MCP remain single replicas to fit local resource limits, although their processes are stateless. Rate limits are per RAG process, while Groq enforces the final organization-wide limit. |
| IX. Disposability | Compliant | Spring uses graceful shutdown, Compose provides stop grace periods and health checks, restart attempts are bounded for application processes, and ingestion is checksum-idempotent. Request-path dependencies wait for usable upstream health. | Large Ollama model loading makes cold startup slower than the Java services. |
| X. Dev/prod parity | Partial | The same application images, protocols, PGVector schema, and environment contract are used throughout the Compose workflow. | Tests use isolated doubles; local Config Git and Ollama cannot reproduce external infrastructure capacity, latency, or Groq quotas exactly. |
| XI. Logs | Compliant | Spring and HAProxy write event streams to stdout/stderr with application, trace, and span correlation. Compose or the deployment platform owns collection and retention. | Local Compose does not provide durable centralized log storage. Prometheus and Zipkin cover metrics and traces, not log retention. |
| XII. Admin processes | Mostly compliant | Corpus ingestion is an idempotent one-off execution of the same versioned RAG image and code used by the service. | Vector-table reset uses the PostgreSQL client image and model pull uses the Ollama image because these administer backing-service state; allowing the application process to destroy its own live schema would weaken isolation and safety. |

## Operational rules

- Build once and run with `--no-build`; never rebuild as part of release startup.
- Keep `.env`, API keys, database passwords, and remote Git credentials out of Git, images, logs, and Config Server.
- Change resource URLs through environment variables, not source edits or image rebuilds.
- Scale only stateless application processes. Treat PostgreSQL and Ollama scaling as separate backing-service architecture work.
- Run ingestion and vector reset explicitly through the `admin` Compose profile.
- Revalidate this matrix when adding a service, persistent volume, local cache, background process, or new deployment target.
