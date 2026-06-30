# Spring Cloud API Gateway

The public entry point is `http://localhost:8080`. Two reactive Gateway
instances run behind HAProxy, fetch their configuration from Config Server,
and discover downstream instances through the Eureka cluster endpoint.

## Start the stack

From the repository root:

```powershell
docker compose up --build -d
docker compose ps
```

Operational endpoints:

- Gateway routes: `http://localhost:8080/actuator/gateway/routes`
- Gateway metrics: `http://localhost:8080/actuator/prometheus`
- Prometheus: `http://localhost:9090`
- Zipkin: `http://localhost:9411`

## Publish a service through the Gateway

Services are private by default. A business service must register this Eureka
metadata before the Gateway creates a route for it:

```properties
eureka.instance.metadata-map.gateway-exposed=true
```

For a service named `orders-service`, the Gateway exposes
`/orders-service/**`. The prefix is removed before forwarding, so a request to
`/orders-service/orders/42` reaches the service as `/orders/42`.

The route URI is `lb://orders-service`; Spring LoadBalancer selects a healthy
instance returned by Eureka. Gateway, Eureka, and Config Server do not carry
the opt-in metadata and therefore are not published.

## Failure behavior

Each discovered service receives its own circuit breaker. GET and HEAD
requests are retried at most twice for HTTP 502, 503, and 504 responses using
bounded exponential backoff and jitter. Mutating requests are not retried.
After the circuit breaker rejects or exhausts the call, the Gateway returns a
structured HTTP 503 response.

The Gateway uses one Eureka URL:

```text
http://eureka-cluster:8761/eureka/
```

Two internal HAProxy replicas distribute that traffic across the three Eureka
peers. The Eureka peer list remains private to the registry servers.

## Failure checks

After the stack is healthy, stop one Gateway or Eureka instance and repeat a
request through port 8080. HAProxy and Eureka discovery should keep the public
endpoint available:

```powershell
docker compose stop gateway-1
Invoke-RestMethod http://localhost:8080/actuator/health
docker compose start gateway-1
```

Use Prometheus to inspect `spring_cloud_gateway_requests_seconds` and the
Resilience4j circuit-breaker series. Zipkin shows request traces for traffic
passing through either Gateway instance.
