# Twelve-Factor compliance

This repository applies the [Twelve-Factor App](https://12factor.net/)
principles to the Config Server, Eureka registry, and API Gateway. Future
business services must inherit the root Maven parent and follow the same
runtime contract.

## Build and release

The root Maven wrapper is the only supported build entry point. It pins Maven
3.9.16, Java 26, Spring Boot 4.1.0, Spring Cloud 2025.1.2, build plugins, and
dependency convergence rules.

```powershell
.\mvnw.cmd verify
```

Prepare deploy configuration without committing it:

```powershell
Copy-Item .env.example .env
# Replace BUILD_REVISION and review every value in .env.
```

Build and run are separate stages. A release image is built once and is not
rebuilt during startup:

```powershell
docker compose --env-file .env build
docker compose --env-file .env up -d --no-build
```

`RELEASE_VERSION` is the immutable release identifier used in all application
image tags. Change it for every release. Rollback means restoring the previous
`.env` release/config label and running `up -d --no-build`; never overwrite a
published release tag.

## Environment contract

Applications fail during bootstrap when required configuration is absent.
Compose supplies each process's `PORT`; direct JVM launches must supply it
themselves.

Required attached-resource handles are:

- `CONFIG_SERVER_URL` for Config clients.
- `CONFIG_GIT_URI` and `CONFIG_GIT_DEFAULT_LABEL` for Config Server.
- `EUREKA_PEERS` for registry replication and `EUREKA_URL` for clients.
- `ZIPKIN_ENDPOINT` and `TRACING_SAMPLING_PROBABILITY` for Gateway tracing.

`CONFIG_GIT_USERNAME` and `CONFIG_GIT_PASSWORD` are optional because public
Git repositories do not require credentials. Never store real
credentials in `.env.example`, Git, an image, or application properties.

The local Compose stack runs Git as a separate, internal HTTP-backed service
seeded from `config-repository/`. Deployed environments replace
`CONFIG_GIT_URI` with their managed Git service without rebuilding Config
Server.

Config Server stores deploy-invariant application policy such as route,
timeout, retry, and circuit-breaker behavior. Resource URLs, credentials,
hostnames, release labels, and ports come from environment variables.

## Compliance matrix

| Factor | Implementation |
| --- | --- |
| I. Codebase | One Git repository and one release version for the current distributed system; each service remains independently buildable. |
| II. Dependencies | Root Maven manifest, Boot/Cloud BOMs, Enforcer convergence, release-only dependencies, checked Maven wrapper, and container image pins. |
| III. Config | Required orthogonal environment variables; `.env` is ignored and only a non-secret example is tracked. |
| IV. Backing services | Config Git, Eureka, Zipkin, and other services are referenced through environment-provided URLs. |
| V. Build/release/run | Maven/Docker build, versioned release image, and `up --no-build` runtime are distinct stages. |
| VI. Processes | Gateway and registry processes are stateless; registry state is reconstructed from surviving peers. |
| VII. Port binding | Every Spring process receives `PORT` and embeds its HTTP server. |
| VIII. Concurrency | Gateway and Eureka scale through multiple independent containers rather than application-managed worker processes. |
| IX. Disposability | Health checks, restart policies, graceful Spring shutdown, and bounded 25-second container termination. |
| X. Dev/prod parity | The same JARs and container definitions are used locally and in deployment; only environment values change. |
| XI. Logs | Applications and HAProxy write event streams to stdout/stderr; no application log files are used. |
| XII. Admin processes | No migrations or administrative jobs exist yet. Future one-off jobs must run from the same versioned image and environment as the release. |

## Service requirements

A new service is compliant only when it:

1. Is added as a module of the root Maven build and inherits its parent.
2. Declares every direct dependency in its POM without dynamic or snapshot
   versions; versions come from the central BOM or root dependency management.
3. Reads deploy-specific resource handles and credentials from environment
   variables without committed fallbacks.
4. Stores no durable state in memory or its container filesystem.
5. Logs to stdout/stderr, binds to `PORT`, supports graceful shutdown, and can
   run more than one instance.
