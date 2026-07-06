package com.distributed_systems.rag;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagQuestionServiceTests {

	@Test
	void refusesQuestionWhenRetrievalHasNoEvidence() {
		VectorStore vectorStore = mock(VectorStore.class);
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
		@SuppressWarnings("unchecked")
		ObjectProvider<ToolCallbackProvider> providers = mock(ObjectProvider.class);
		when(providers.orderedStream()).thenReturn(Stream.empty());
		RagQuestionService service = new RagQuestionService(
				mock(ChatClient.class), vectorStore,
				new RagProperties(6, 0.72, 10, 3500, 300, Path.of("corpus"), Path.of("metadata")),
				providers, new SimpleMeterRegistry(), CircuitBreakerRegistry.ofDefaults(), BulkheadRegistry.ofDefaults()
		);

		Answer answer = service.ask(new AskQuestionInput("conversation-1", "Pergunta fora do corpus", false));

		assertFalse(answer.grounded());
		assertFalse(answer.verifiedOnline());
		assertTrue(answer.sources().isEmpty());
		assertTrue(answer.answer().contains("Nao encontrei"));
	}
}
