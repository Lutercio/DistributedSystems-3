package com.distributed_systems.gateway;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CircuitBreakerRecoveryProberTests {

	private static final String SERVICE_ID = "question-service";

	@Test
	void closesHalfOpenBreakerAfterPermittedSuccessfulProbes() {
		AtomicInteger requests = new AtomicInteger();
		AtomicReference<String> requestedPath = new AtomicReference<>();
		TestContext context = context(request -> {
			requests.incrementAndGet();
			requestedPath.set(request.url().toString());
			return Mono.just(ClientResponse.create(HttpStatus.OK).build());
		}, Duration.ofSeconds(1));
		CircuitBreaker circuitBreaker = halfOpen(context.registry());

		context.prober().recoveryCycle().block();
		context.prober().recoveryCycle().block();
		context.prober().recoveryCycle().block();

		assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
		assertEquals(3, requests.get());
		assertEquals("http://question-service/actuator/health", requestedPath.get());
		assertEquals(3.0, context.meterRegistry().get("gateway.circuitbreaker.recovery.probes")
				.tag("service", SERVICE_ID)
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
		assertEquals(3.0, context.meterRegistry().get("gateway.circuitbreaker.recovery.probes")
				.tag("service", SERVICE_ID)
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
		assertEquals(3.0, context.meterRegistry().get("gateway.circuitbreaker.recovery.probes")
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
		CircuitBreaker circuitBreaker = context.registry().circuitBreaker(SERVICE_ID);

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
		CircuitBreaker circuitBreaker = registry.circuitBreaker(SERVICE_ID);
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
		Resilience4JConfigurationProperties factoryProperties = new Resilience4JConfigurationProperties();
		factoryProperties.setDisableTimeLimiter(true);
		ReactiveResilience4JCircuitBreakerFactory factory = new ReactiveResilience4JCircuitBreakerFactory(
				registry,
				TimeLimiterRegistry.ofDefaults(),
				null,
				factoryProperties
		);
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		CircuitBreakerRecoveryProperties properties = new CircuitBreakerRecoveryProperties(
				true,
				Duration.ofSeconds(2),
				timeout,
				"/actuator/health"
		);
		WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
		CircuitBreakerRecoveryProber prober = new CircuitBreakerRecoveryProber(
				webClient,
				factory,
				registry,
				properties,
				meterRegistry
		);
		return new TestContext(prober, registry, meterRegistry);
	}

	private record TestContext(
			CircuitBreakerRecoveryProber prober,
			CircuitBreakerRegistry registry,
			SimpleMeterRegistry meterRegistry
	) {
	}
}
