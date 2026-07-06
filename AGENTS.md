# Repository Guidelines

## Project Structure & Module Organization

This is a Java 26, Spring Boot, multi-module Maven project. The root `pom.xml` manages dependency and plugin versions for:

- `ConfigServer/`: Spring Cloud Config Server backed by the local Git repository created from `config-repository/config/`.
- `EurekaServer/`: replicated Eureka service-discovery nodes.
- `APIGateway/`: reactive Spring Cloud Gateway using Eureka, Spring LoadBalancer, Resilience4j, tracing, and metrics.

Each module follows Maven conventions: production code is under `src/main/java`, runtime settings under `src/main/resources`, and tests under `src/test`. Docker and observability definitions live in `compose.yml` and `infrastructure/`.

## Build, Test, and Development Commands

Use the checked-in Maven wrapper; Java 26 is required.

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -pl APIGateway test
Copy-Item .env.example .env
docker compose --env-file .env up -d --build
docker compose --env-file .env ps
docker compose --env-file .env down
```

`clean verify` compiles every module and runs all tests. `-pl` targets one module. Compose builds and starts Config Server, three Eureka peers behind HAProxy, two Gateway instances, Prometheus, and Zipkin. Do not commit `.env`; add documented defaults to `.env.example`.

## Coding Style & Naming Conventions

Use tabs where existing Java and XML files use tabs, four-column visual indentation, and standard Java naming: `UpperCamelCase` types, `lowerCamelCase` methods and fields, and `UPPER_SNAKE_CASE` constants. Keep packages beneath `com.distributed_systems.<module>`. Prefer constructor injection and configuration through environment variables or Config Server. No formatter or linter is configured, so match surrounding code and keep imports organized.

## Testing Guidelines

Tests use JUnit 5 and Spring Boot Test. Name test classes `*Tests` and test methods after observable behavior, such as `returnsServiceUnavailableResponse`. Add unit tests for controllers and focused context tests for configuration changes. There is no enforced coverage threshold; new behavior should still include regression coverage. Run `clean verify` before submitting changes.

## Commit & Pull Request Guidelines

History currently uses Conventional Commit-style subjects (`chore: initial commit`). Continue with concise, imperative subjects such as `feat: add gateway retry policy` or `fix: preserve Eureka failover`. Keep commits scoped to one concern.

Pull requests should explain the change, affected services, configuration or port changes, and verification performed. Link relevant issues. Include screenshots only for dashboard or UI changes, and never include secrets, generated `target/` content, or local IDE files.
