# Centralized configuration with Spring Cloud Config

The Config Server uses a Git environment repository supplied through
`CONFIG_GIT_URI`. Configuration is deliberately not copied into the application
image, so the same release artifact can be promoted through every environment.
For local development, Compose seeds `config-repository/` into a separate,
internal Git-over-HTTP backing service. A deployed environment replaces only
`CONFIG_GIT_URI` with its remote repository URL.

The repository is organized by application:

- `config/application.properties` contains shared defaults.
- `config/gateway/application.properties` contains Gateway configuration.
- `config/eureka-server/application.properties` contains Eureka configuration.

Only bootstrap properties remain inside each client application. Runtime
service settings are returned by Config Server according to the requested
`{application}`, `{profile}`, and `{label}`.

## Start and inspect

From the repository root:

```powershell
Copy-Item .env.example .env
docker compose --env-file .env build
docker compose --env-file .env up -d --no-build
docker compose ps
```

Inspect the effective configuration:

```powershell
Invoke-RestMethod http://localhost:8888/gateway/default
Invoke-RestMethod http://localhost:8888/eureka-server/default
Invoke-RestMethod http://localhost:8888/gateway/default/main
```

The Eureka containers wait for a healthy Config Server and use fail-fast,
timeouts, and retry during bootstrap.

## Configure the Git repository

Set these variables before starting Compose:

```powershell
# For a deployed environment, edit the corresponding values in .env:
# CONFIG_GIT_URI=https://example.com/organization/config-repository.git
# CONFIG_GIT_DEFAULT_LABEL=release-0.0.1
# CONFIG_GIT_USERNAME=<username>
# CONFIG_GIT_PASSWORD=<token-or-password>
docker compose --env-file .env up -d --no-build
```

Do not commit credentials. For a public repository, leave the username and
password unset.

## Refresh a running client

After committing and pushing a configuration change, request the updated
environment from a client with its Actuator refresh endpoint:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/actuator/refresh
```

Configuration-bound or refresh-scoped beans will receive the refreshed values.
Properties that require recreating the web server, such as `server.port`, still
require an application restart.
