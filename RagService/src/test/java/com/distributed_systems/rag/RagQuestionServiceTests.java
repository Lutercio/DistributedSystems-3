package com.distributed_systems.rag;

import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

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

	@Test
	void exposesNoToolsForNormalGroundedQuestions() throws Exception {
		RagQuestionService service = serviceWithTools(
				tool("buscar_artigo"),
				tool("tavily-search")
		);

		List<ToolCallback> tools = availableTools(service, false);

		assertTrue(tools.isEmpty());
	}

	@Test
	void exposesOnlyTavilyToolsWhenOnlineVerificationIsRequested() throws Exception {
		RagQuestionService service = serviceWithTools(
				tool("buscar_artigo"),
				tool("tavily-search"),
				tool("tavily-extract")
		);

		List<ToolCallback> tools = availableTools(service, true);

		assertTrue(tools.stream().map(tool -> tool.getToolDefinition().name()).allMatch(name -> name.startsWith("tavily")));
		assertFalse(tools.isEmpty());
	}

	@Test
	void generatedAnswerAcceptsNullGroundedFromModel() throws Exception {
		GeneratedAnswer answer = new ObjectMapper()
				.readValue("{\"answer\":\"Resposta baseada no contexto\",\"grounded\":null}", GeneratedAnswer.class);

		assertTrue(answer.answer().contains("contexto"));
		assertTrue(answer.grounded() == null);
	}

	private RagQuestionService serviceWithTools(ToolCallback... callbacks) {
		@SuppressWarnings("unchecked")
		ObjectProvider<ToolCallbackProvider> providers = mock(ObjectProvider.class);
		ToolCallbackProvider provider = () -> callbacks;
		when(providers.orderedStream()).thenReturn(Stream.of(provider));
		return new RagQuestionService(
				mock(ChatClient.class), mock(VectorStore.class),
				new RagProperties(6, 0.72, 10, 3500, 300, Path.of("corpus"), Path.of("metadata")),
				providers, new SimpleMeterRegistry(), CircuitBreakerRegistry.ofDefaults(), BulkheadRegistry.ofDefaults()
		);
	}

	@SuppressWarnings("unchecked")
	private List<ToolCallback> availableTools(RagQuestionService service, boolean verifyOnline) throws Exception {
		Method method = RagQuestionService.class.getDeclaredMethod("availableTools", boolean.class, Set.class);
		method.setAccessible(true);
		return (List<ToolCallback>) method.invoke(service, verifyOnline, new LinkedHashSet<String>());
	}

	private ToolCallback tool(String name) {
		ToolDefinition definition = ToolDefinition.builder()
				.name(name)
				.description(name)
				.inputSchema("{\"type\":\"object\"}")
				.build();
		return new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return definition;
			}

			@Override
			public String call(String input) {
				return "{}";
			}
		};
	}
}
