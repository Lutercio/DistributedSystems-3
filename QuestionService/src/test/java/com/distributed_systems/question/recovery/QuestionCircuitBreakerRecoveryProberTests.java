package com.distributed_systems.question.recovery;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionCircuitBreakerRecoveryProberTests {

	private static final String BREAKER_NAME = "ragService";
	private static final URI HEALTH_URI = URI.create("http://rag-service/actuator/health");

	@Test
	void closesHalfOpenBreakerAfterPermittedSuccessfulProbes() {
		AtomicInteger requests = new AtomicInteger();
		AtomicReference<String> requestedUri = new AtomicReference<>();
		TestContext context = context(request -> {
			requests.incrementAndGet();
			requestedUri.set(request.url().toString());
			return Mono.just(ClientResponse.create(HttpStatus.OK).build());
		}, Duration.ofSeconds(1));
		CircuitBreaker circuitBreaker = halfOpen(context.registry());

		context.prober().recoveryCycle().block();
		context.prober().recoveryCycle().block();
		context.prober().recoveryCycle().block();

		assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
		assertEquals(3, requests.get());
		assertEquals(HEALTH_URI.toString(), requestedUri.get());
		assertEquals(3.0, context.meterRegistry().get("question.circuitbreaker.recovery.probes")
				.tag("breaker", BREAKER_NAME)
				.tag("outcome", "success")
				.counter()
				.count());
	}

	@Test
	void returnsHalfOpenBreakerToOpenAfterFailedProbes() {
		AtomicInteger requests = new AtomicInteger();
		TestContext context = context(request -> {
			requests.incrementAndGet();
			return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
		}, Duration.ofSeconds(1));
		CircuitBreaker circuitBreaker = halfOpen(context.registry());

		context.prober().recoveryCycle().block();
		context.prober().recoveryCycle().block();
		context.prober().recoveryCycle().block();

		assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
		assertEquals(3, requests.get());
		assertEquals(3.0, context.meterRegistry().get("question.circuitbreaker.recovery.probes")
				.tag("breaker", BREAKER_NAME)
				.tag("outcome", "failure")
				.counter()
				.count());
	}

	@Test
	void recordsProbeTimeoutAsFailure() {
		TestContext context = context(request -> Mono.never(), Duration.ofMillis(10));
		CircuitBreaker circuitBreaker = halfOpen(context.registry());

		context.prober().recoveryCycle().block();
		context.prober().recoveryCycle().block();
		context.prober().recoveryCycle().block();

		assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
		assertEquals(3.0, context.meterRegistry().get("question.circuitbreaker.recovery.probes")
				.tag("outcome", "failure")
				.counter()
				.count());
	}

	@Test
	void skipsClosedAndOpenBreakers() {
		AtomicInteger requests = new AtomicInteger();
		TestContext context = context(request -> {
			requests.incrementAndGet();
			return Mono.just(ClientResponse.create(HttpStatus.OK).build());
		}, Duration.ofSeconds(1));
		CircuitBreaker circuitBreaker = context.registry().circuitBreaker(BREAKER_NAME);

		context.prober().recoveryCycle().block();
		circuitBreaker.transitionToOpenState();
		context.prober().recoveryCycle().block();

		assertEquals(0, requests.get());
	}

	@Test
	void preventsConcurrentProbesForSameBreaker() {
		AtomicInteger requests = new AtomicInteger();
		Sinks.One<ClientResponse> response = Sinks.one();
		TestContext context = context(request -> {
			requests.incrementAndGet();
			return response.asMono();
		}, Duration.ofSeconds(5));
		halfOpen(context.registry());

		Disposable firstCycle = context.prober().recoveryCycle().subscribe();
		context.prober().recoveryCycle().block();

		assertEquals(1, requests.get());
		firstCycle.dispose();
	}

	private CircuitBreaker halfOpen(CircuitBreakerRegistry registry) {
		CircuitBreaker circuitBreaker = registry.circuitBreaker(BREAKER_NAME);
		circuitBreaker.transitionToOpenState();
		circuitBreaker.transitionToHalfOpenState();
		return circuitBreaker;
	}

	private TestContext context(ExchangeFunction exchangeFunction, Duration timeout) {
		CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
				.failureRateThreshold(50)
				.permittedNumberOfCallsInHalfOpenState(3)
				.build();
		CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(circuitBreakerConfig);
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		QuestionCircuitBreakerRecoveryProperties properties = new QuestionCircuitBreakerRecoveryProperties(
				true,
				Duration.ofSeconds(2),
				timeout,
				List.of(new QuestionCircuitBreakerRecoveryProperties.Target(BREAKER_NAME, HEALTH_URI))
		);
		WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
		QuestionCircuitBreakerRecoveryProber prober = new QuestionCircuitBreakerRecoveryProber(
				webClient,
				registry,
				properties,
				meterRegistry
		);
		return new TestContext(prober, registry, meterRegistry);
	}

	private record TestContext(
			QuestionCircuitBreakerRecoveryProber prober,
			CircuitBreakerRegistry registry,
			SimpleMeterRegistry meterRegistry
	) {
	}
}
