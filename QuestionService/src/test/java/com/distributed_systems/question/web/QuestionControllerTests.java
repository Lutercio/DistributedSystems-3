package com.distributed_systems.question.web;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.distributed_systems.question.api.QuestionRequest;
import com.distributed_systems.question.api.QuestionResponse;
import com.distributed_systems.question.client.DownstreamServiceException;
import com.distributed_systems.question.service.QuestionCoordinator;

import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class QuestionControllerTests {

	private WebTestClient webTestClient;
	private QuestionCoordinator coordinator;

	@BeforeEach
	void setUp() {
		coordinator = org.mockito.Mockito.mock(QuestionCoordinator.class);
		webTestClient = WebTestClient.bindToController(new QuestionController(coordinator))
				.controllerAdvice(new ApiExceptionHandler())
				.webFilter(new CorrelationIdWebFilter())
				.build();
	}

	@Test
	void returnsAnswerAndCorrelationId() {
		when(coordinator.ask(any(QuestionRequest.class), anyString())).thenReturn(Mono.just(new QuestionResponse(
				"conversation-1", "Resposta", false, false, List.of(), List.of(), "2026-07-04T00:00:00Z"
		)));

		webTestClient.post().uri("/api/questions")
				.header("X-Correlation-Id", "correlation-1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{"conversationId":"conversation-1","question":"Como funciona?","verifyOfficialSource":false}
						""")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals("X-Correlation-Id", "correlation-1")
				.expectBody()
				.jsonPath("$.conversationId").isEqualTo("conversation-1")
				.jsonPath("$.grounded").isEqualTo(false);
	}

	@Test
	void returnsStructuredBadRequestForInvalidInput() {
		webTestClient.post().uri("/api/questions")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"question\":\"\"}")
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().exists("X-Correlation-Id")
				.expectBody()
				.jsonPath("$.code").isEqualTo("INVALID_REQUEST")
				.jsonPath("$.correlationId").isNotEmpty();
	}

	@Test
	void mapsRagFailureToServiceUnavailable() {
		when(coordinator.ask(any(QuestionRequest.class), anyString()))
				.thenReturn(Mono.error(new DownstreamServiceException("RagService is unavailable")));

		webTestClient.post().uri("/api/questions")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"question\":\"Como funciona?\"}")
				.exchange()
				.expectStatus().isEqualTo(503)
				.expectBody()
				.jsonPath("$.code").isEqualTo("RAG_SERVICE_UNAVAILABLE");
	}

}
