package com.distributed_systems.gateway;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class CircuitBreakerRecoveryProber {

	private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerRecoveryProber.class);
	private static final String PROBE_METRIC = "gateway.circuitbreaker.recovery.probes";

	private final WebClient webClient;
	private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;
	private final CircuitBreakerRegistry circuitBreakerRegistry;
	private final CircuitBreakerRecoveryProperties properties;
	private final MeterRegistry meterRegistry;
	private final Set<String> probesInFlight = ConcurrentHashMap.newKeySet();

	CircuitBreakerRecoveryProber(
			WebClient webClient,
			ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory,
			CircuitBreakerRegistry circuitBreakerRegistry,
			CircuitBreakerRecoveryProperties properties,
			MeterRegistry meterRegistry
	) {
		this.webClient = webClient;
		this.circuitBreakerFactory = circuitBreakerFactory;
		this.circuitBreakerRegistry = circuitBreakerRegistry;
		this.properties = properties;
		this.meterRegistry = meterRegistry;
	}

	@Scheduled(fixedDelayString = "${app.gateway.circuit-breaker-recovery.interval:2s}")
	void recoverHalfOpenCircuitBreakers() {
		recoveryCycle().subscribe(
				nothing -> { },
				error -> LOGGER.error("Circuit-breaker recovery cycle failed", error)
		);
	}

	Mono<Void> recoveryCycle() {
		return Flux.fromIterable(circuitBreakerRegistry.getAllCircuitBreakers())
				.filter(circuitBreaker -> circuitBreaker.getState() == CircuitBreaker.State.HALF_OPEN)
				.filter(circuitBreaker -> probesInFlight.add(circuitBreaker.getName()))
				.flatMap(this::probe)
				.then();
	}

	private Mono<Void> probe(CircuitBreaker circuitBreaker) {
		String serviceId = circuitBreaker.getName();
		URI healthUri = URI.create("http://" + serviceId + properties.healthPath());
		Mono<Void> healthRequest = webClient.get()
				.uri(healthUri)
				.retrieve()
				.toBodilessEntity()
				.timeout(properties.timeout())
				.then();

		return circuitBreakerFactory.create(serviceId)
				.run(healthRequest, Mono::error)
				.doOnSuccess(nothing -> recordSuccess(circuitBreaker))
				.doOnError(error -> recordFailure(serviceId, error))
				.onErrorResume(error -> Mono.empty())
				.doFinally(signal -> probesInFlight.remove(serviceId));
	}

	private void recordSuccess(CircuitBreaker circuitBreaker) {
		meterRegistry.counter(PROBE_METRIC, "service", circuitBreaker.getName(), "outcome", "success")
				.increment();
		if (circuitBreaker.getState() == CircuitBreaker.State.CLOSED) {
			LOGGER.info("Circuit breaker {} recovered and closed", circuitBreaker.getName());
		}
		else {
			LOGGER.debug("Circuit breaker {} recovery probe succeeded", circuitBreaker.getName());
		}
	}

	private void recordFailure(String serviceId, Throwable error) {
		meterRegistry.counter(PROBE_METRIC, "service", serviceId, "outcome", "failure").increment();
		LOGGER.info("Circuit breaker {} recovery probe failed: {}", serviceId, error.getMessage());
	}
}
