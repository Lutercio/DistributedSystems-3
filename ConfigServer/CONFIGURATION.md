# Centralized configuration with Spring Cloud Config

The Config Server uses a Git environment repository. The Docker image creates
an initial `main` branch from the tracked files under `config-repository/`, so
the local stack is runnable without external credentials while still using the
same versioned backend model as a remote Git repository.

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
docker compose up --build -d
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

## Use a remote Git repository

Set these variables before starting Compose:

```powershell
$env:CONFIG_GIT_URI = 'https://example.com/organization/config-repository.git'
$env:CONFIG_GIT_DEFAULT_LABEL = 'main'
$env:CONFIG_GIT_USERNAME = '<username>'
$env:CONFIG_GIT_PASSWORD = '<token-or-password>'
docker compose up --build -d
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
