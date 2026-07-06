package com.distributed_systems.rag;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionGraphQlControllerTests {

	private final RagQuestionService questionService = mock(RagQuestionService.class);
	private final QuestionGraphQlController controller = new QuestionGraphQlController(questionService);

	@Test
	void delegatesQuestionToRagPipeline() {
		AskQuestionInput input = new AskQuestionInput(
				"conversation-1",
				"Como funciona o trancamento?",
				false
		);
		when(questionService.ask(input)).thenReturn(new Answer(
				"conversation-1", "Resposta fundamentada", true, false,
				List.of(new Source("Regulamento", "Art. 120", "Trecho", "https://ufrn.br")),
				List.of(), "2026-07-06T00:00:00Z"
		));

		Answer answer = controller.askQuestion(input).block();

		assertEquals("conversation-1", answer.conversationId());
		assertEquals("Resposta fundamentada", answer.answer());
		assertTrue(answer.grounded());
		assertFalse(answer.verifiedOnline());
		assertFalse(answer.sources().isEmpty());
		assertTrue(answer.toolsUsed().isEmpty());
	}

	@Test
	void exposesServiceStatusQuery() {
		assertEquals("UP", controller.serviceStatus());
	}
}
