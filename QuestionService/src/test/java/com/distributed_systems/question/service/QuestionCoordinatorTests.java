package com.distributed_systems.question.service;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.distributed_systems.question.api.QuestionRequest;
import com.distributed_systems.question.api.QuestionResponse;
import com.distributed_systems.question.client.NormalizationResponse;
import com.distributed_systems.question.client.NormalizerClient;
import com.distributed_systems.question.client.RagAnswer;
import com.distributed_systems.question.client.RagClient;
import com.distributed_systems.question.client.RagSource;

import reactor.core.publisher.Mono;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionCoordinatorTests {

	private final NormalizerClient normalizerClient = mock(NormalizerClient.class);
	private final RagClient ragClient = mock(RagClient.class);
	private final QuestionCoordinator coordinator = new QuestionCoordinator(
			normalizerClient, ragClient, new SimpleMeterRegistry()
	);

	@Test
	void preservesConversationIdAndMapsAnswer() {
		when(normalizerClient.normalize(anyString(), anyString()))
				.thenReturn(Mono.just(new NormalizationResponse("Pergunta normalizada", true, List.of())));
		when(ragClient.ask(anyString(), anyString(), anyBoolean(), anyString()))
				.thenReturn(Mono.just(answer("conversation-1")));

		QuestionResponse response = coordinator.ask(
				new QuestionRequest("conversation-1", " Pergunta normalizada ", false),
				"correlation-1"
		).block();

		assertEquals("conversation-1", response.conversationId());
		assertFalse(response.grounded());
		assertEquals("Art. 1", response.sources().getFirst().article());
		verify(ragClient).ask("conversation-1", "Pergunta normalizada", false, "correlation-1");
	}

	@Test
	void generatesConversationIdWhenMissing() {
		when(normalizerClient.normalize(anyString(), anyString()))
				.thenReturn(Mono.just(new NormalizationResponse("Pergunta", true, List.of())));
		when(ragClient.ask(anyString(), anyString(), anyBoolean(), anyString()))
				.thenAnswer(invocation -> Mono.just(answer(invocation.getArgument(0))));

		QuestionResponse response = coordinator.ask(
				new QuestionRequest("", "Pergunta", false),
				"correlation-1"
		).block();

		assertFalse(response.conversationId().isBlank());
	}

	@Test
	void fallsBackToTrimmedQuestionWhenNormalizerIsUnavailable() {
		when(normalizerClient.normalize(anyString(), anyString()))
				.thenReturn(Mono.error(new IllegalStateException("unavailable")));
		when(ragClient.ask(anyString(), anyString(), anyBoolean(), anyString()))
				.thenReturn(Mono.just(answer("conversation-1")));

		coordinator.ask(
				new QuestionRequest("conversation-1", "  Pergunta original  ", false),
				"correlation-1"
		).block();

		verify(ragClient).ask("conversation-1", "Pergunta original", false, "correlation-1");
	}

	@Test
	void rejectsNormalizerViolationsWithoutCallingRagService() {
		when(normalizerClient.normalize(anyString(), anyString()))
				.thenReturn(Mono.just(new NormalizationResponse("oi", false, List.of("QUESTION_TOO_SHORT"))));

		assertThrows(InvalidQuestionException.class, () -> coordinator.ask(
				new QuestionRequest("conversation-1", "oi", false),
				"correlation-1"
		).block());
	}

	@Test
	void validatesFallbackWhenNormalizerIsUnavailable() {
		when(normalizerClient.normalize(anyString(), anyString()))
				.thenReturn(Mono.error(new IllegalStateException("unavailable")));

		assertThrows(InvalidQuestionException.class, () -> coordinator.ask(
				new QuestionRequest("conversation-1", "oi", false),
				"correlation-1"
		).block());
	}

	private RagAnswer answer(String conversationId) {
		return new RagAnswer(
				conversationId,
				"Resposta",
				false,
				false,
				List.of(new RagSource("Regulamento", "Art. 1", "Trecho", "https://ufrn.br")),
				List.of(),
				"2026-07-04T00:00:00Z"
		);
	}

}
