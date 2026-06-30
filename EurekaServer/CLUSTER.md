# Eureka cluster with Docker Compose

This project runs three peer-aware Eureka servers from one image. Config Server
starts first and supplies their shared Eureka settings from its Git repository.
The peers then use Docker DNS names to replicate registry state directly:

- `eureka-1:8761`
- `eureka-2:8761`
- `eureka-3:8761`

Spring Cloud LoadBalancer has a separate role: after a client fetches the
registry, it selects an instance of a registered business service for an
`lb://SERVICE-ID` request. Connections to the registry itself are managed by
the Eureka client using the configured cluster URLs.

## Start the cluster

From the repository root:

```powershell
docker compose up --build -d
docker compose ps
```

Wait until all three services report `healthy`. Their dashboards are available
at:

- <http://localhost:8761>
- <http://localhost:8762>
- <http://localhost:8763>

The gateway defaults to all three published URLs. Start it from another
terminal:

```powershell
cd APIGateway
.\mvnw.cmd spring-boot:run
```

After Eureka's registration interval, `GATEWAY` should be visible on every
dashboard. The registry can also be queried directly:

```powershell
Invoke-RestMethod http://localhost:8761/eureka/apps/GATEWAY
Invoke-RestMethod http://localhost:8762/eureka/apps/GATEWAY
Invoke-RestMethod http://localhost:8763/eureka/apps/GATEWAY
```

## Verify failure and recovery

Stop one peer:

```powershell
docker compose stop eureka-1
docker compose ps
```

The remaining dashboards and the gateway should stay available. Recreate the
peer and watch it synchronize the registry:

```powershell
docker compose up -d --force-recreate eureka-1
docker compose logs -f eureka-1
```

Press `Ctrl+C` to stop following the logs. Unexpected container exits are also
covered by the `unless-stopped` restart policy; an explicit `compose stop`
keeps the selected peer down for the failure demonstration.

## Stop the cluster

```powershell
docker compose down
```

Eureka's registry is intentionally not persisted. A recreated peer restores
state from the surviving peers instead of loading stale local registry data.
