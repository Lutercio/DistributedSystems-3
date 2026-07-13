package com.distributed_systems.question.recovery;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class QuestionCircuitBreakerRecoveryProber {

	private static final Logger LOGGER = LoggerFactory.getLogger(QuestionCircuitBreakerRecoveryProber.class);
	private static final String PROBE_METRIC = "question.circuitbreaker.recovery.probes";

	private final WebClient webClient;
	private final CircuitBreakerRegistry circuitBreakerRegistry;
	private final QuestionCircuitBreakerRecoveryProperties properties;
	private final MeterRegistry meterRegistry;
	private final Set<String> probesInFlight = ConcurrentHashMap.newKeySet();

	QuestionCircuitBreakerRecoveryProber(
			WebClient webClient,
			CircuitBreakerRegistry circuitBreakerRegistry,
			QuestionCircuitBreakerRecoveryProperties properties,
			MeterRegistry meterRegistry
	) {
		this.webClient = webClient;
		this.circuitBreakerRegistry = circuitBreakerRegistry;
		this.properties = properties;
		this.meterRegistry = meterRegistry;
	}

	@Scheduled(fixedDelayString = "${app.question-service.circuit-breaker-recovery.interval:2s}")
	void recoverHalfOpenCircuitBreakers() {
		recoveryCycle().subscribe(
				nothing -> { },
				error -> LOGGER.error("QuestionService circuit-breaker recovery cycle failed", error)
		);
	}

	Mono<Void> recoveryCycle() {
		return Flux.fromIterable(properties.targets())
				.map(target -> new ProbeCandidate(target, circuitBreakerRegistry.circuitBreaker(target.breakerName())))
				.filter(candidate -> candidate.circuitBreaker().getState() == CircuitBreaker.State.HALF_OPEN)
				.filter(candidate -> probesInFlight.add(candidate.target().breakerName()))
				.flatMap(this::probe)
				.then();
	}

	private Mono<Void> probe(ProbeCandidate candidate) {
		QuestionCircuitBreakerRecoveryProperties.Target target = candidate.target();
		CircuitBreaker circuitBreaker = candidate.circuitBreaker();
		Mono<Void> healthRequest = webClient.get()
				.uri(target.healthUri())
				.retrieve()
				.toBodilessEntity()
				.timeout(properties.timeout())
				.then();

		return healthRequest
				.transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
				.doOnSuccess(nothing -> recordSuccess(circuitBreaker))
				.doOnError(error -> recordFailure(target.breakerName(), error))
				.onErrorResume(error -> Mono.empty())
				.doFinally(signal -> probesInFlight.remove(target.breakerName()));
	}

	private void recordSuccess(CircuitBreaker circuitBreaker) {
		meterRegistry.counter(PROBE_METRIC, "breaker", circuitBreaker.getName(), "outcome", "success")
				.increment();
		if (circuitBreaker.getState() == CircuitBreaker.State.CLOSED) {
			LOGGER.info("Circuit breaker {} recovered and closed", circuitBreaker.getName());
		}
		else {
			LOGGER.debug("Circuit breaker {} recovery probe succeeded", circuitBreaker.getName());
		}
	}

	private void recordFailure(String breakerName, Throwable error) {
		meterRegistry.counter(PROBE_METRIC, "breaker", breakerName, "outcome", "failure").increment();
		LOGGER.info("Circuit breaker {} recovery probe failed: {}", breakerName, error.getMessage());
	}

	private record ProbeCandidate(
			QuestionCircuitBreakerRecoveryProperties.Target target,
			CircuitBreaker circuitBreaker
	) {
	}
}
