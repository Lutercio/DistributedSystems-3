package com.distributed_systems.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionGraphQlControllerTests {

	private final QuestionGraphQlController controller = new QuestionGraphQlController();

	@Test
	void returnsExplicitlyUngroundedTemporaryAnswer() {
		Answer answer = controller.askQuestion(new AskQuestionInput(
				"conversation-1",
				"Como funciona o trancamento?",
				false
		));

		assertEquals("conversation-1", answer.conversationId());
		assertEquals(
				"Resposta temporária do RagService para: Como funciona o trancamento?",
				answer.answer()
		);
		assertFalse(answer.grounded());
		assertFalse(answer.verifiedOnline());
		assertTrue(answer.sources().isEmpty());
		assertTrue(answer.toolsUsed().isEmpty());
	}

	@Test
	void exposesServiceStatusQuery() {
		assertEquals("UP", controller.serviceStatus());
	}

}
