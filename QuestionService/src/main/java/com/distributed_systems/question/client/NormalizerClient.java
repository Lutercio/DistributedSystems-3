package com.distributed_systems.question.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Component
public class NormalizerClient {

	private final WebClient webClient;
	private final Duration timeout;

	NormalizerClient(
			@Qualifier("normalizerWebClient") WebClient webClient,
			DownstreamProperties properties
	) {
		this.webClient = webClient;
		this.timeout = properties.normalizerTimeout();
	}

	public Mono<NormalizationResponse> normalize(String question, String correlationId) {
		return webClient.post()
				.header(CorrelationHeaders.CORRELATION_ID, correlationId)
				.bodyValue(new NormalizationRequest(question))
				.retrieve()
				.bodyToMono(NormalizationResponse.class)
				.timeout(timeout);
	}

}
