package com.distributed_systems.question.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;

import reactor.core.publisher.Mono;

@Component
public class NormalizerClient {

	private final WebClient webClient;
	private final Duration timeout;
	private final MeterRegistry meterRegistry;

	NormalizerClient(
			@Qualifier("normalizerWebClient") WebClient webClient,
			DownstreamProperties properties,
			MeterRegistry meterRegistry
	) {
		this.webClient = webClient;
		this.timeout = properties.normalizerTimeout();
		this.meterRegistry = meterRegistry;
	}

	@CircuitBreaker(name = "normalizer")
	@Retry(name = "normalizer")
	public Mono<NormalizationResponse> normalize(String question, String correlationId) {
		return webClient.post()
				.header(CorrelationHeaders.CORRELATION_ID, correlationId)
				.bodyValue(new NormalizationRequest(question))
				.retrieve()
				.bodyToMono(NormalizationResponse.class)
				.timeout(timeout)
				.doOnError(error -> meterRegistry.counter("question.downstream.failures", "service", "normalizer").increment());
	}

}
