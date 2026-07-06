package com.distributed_systems.question.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.distributed_systems.question.api.QuestionRequest;
import com.distributed_systems.question.api.QuestionResponse;
import com.distributed_systems.question.api.QuestionSource;
import com.distributed_systems.question.client.NormalizationResponse;
import com.distributed_systems.question.client.NormalizerClient;
import com.distributed_systems.question.client.RagAnswer;
import com.distributed_systems.question.client.RagClient;

import reactor.core.publisher.Mono;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class QuestionCoordinator {

	private final NormalizerClient normalizerClient;
	private final RagClient ragClient;
	private final MeterRegistry meterRegistry;

	QuestionCoordinator(NormalizerClient normalizerClient, RagClient ragClient, MeterRegistry meterRegistry) {
		this.normalizerClient = normalizerClient;
		this.ragClient = ragClient;
		this.meterRegistry = meterRegistry;
	}

	public Mono<QuestionResponse> ask(QuestionRequest request, String correlationId) {
		String conversationId = request.conversationId() == null || request.conversationId().isBlank()
				? UUID.randomUUID().toString()
				: request.conversationId();

		return normalizerClient.normalize(request.question(), correlationId)
				.onErrorResume(error -> {
					meterRegistry.counter("question.normalizer.fallbacks").increment();
					return Mono.just(NormalizationResponse.fallback(request.question()));
				})
				.flatMap(normalized -> requireValid(normalized)
						.then(Mono.defer(() -> ragClient.ask(
								conversationId,
								normalized.normalizedQuestion(),
								Boolean.TRUE.equals(request.verifyOfficialSource()),
								correlationId
						))))
				.map(this::toResponse);
	}

	private Mono<Void> requireValid(NormalizationResponse response) {
		if (!response.valid()) {
			return Mono.error(new InvalidQuestionException(response.violations()));
		}
		return Mono.empty();
	}

	private QuestionResponse toResponse(RagAnswer answer) {
		return new QuestionResponse(
				answer.conversationId(),
				answer.answer(),
				answer.grounded(),
				answer.verifiedOnline(),
				answer.sources().stream()
						.map(source -> new QuestionSource(
								source.documentTitle(),
								source.article(),
								source.excerpt(),
								source.officialUrl()
						))
						.toList(),
				answer.toolsUsed(),
				answer.timestamp()
		);
	}

}
