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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
				providers, new SimpleMeterRegistry(), CircuitBreakerRegistry.ofDefaults(), BulkheadRegistry.ofDefaults(),
				new ObjectMapper()
		);

		Answer answer = service.ask(new AskQuestionInput("conversation-1", "Pergunta fora do corpus", false));

		assertFalse(answer.grounded());
		assertFalse(answer.verifiedOnline());
		assertTrue(answer.sources().isEmpty());
		assertTrue(answer.answer().contains("Nao encontrei"));
	}

	@Test
	void exposesNoModelToolsForNormalGroundedQuestions() throws Exception {
		RagQuestionService service = serviceWithTools(
				tool("buscar_artigo"),
				tool("tavily-search")
		);

		List<ToolCallback> tools = availableTools(service, false);
		List<String> names = toolNames(tools);

		assertFalse(names.contains("buscar_artigo"));
		assertFalse(names.contains("tavily-search"));
	}

	@Test
	void exposesOnlyTavilyModelToolsWhenOnlineVerificationIsRequested() throws Exception {
		RagQuestionService service = serviceWithTools(
				tool("buscar_artigo"),
				tool("tavily-search"),
				tool("tavily-extract")
		);

		List<ToolCallback> tools = availableTools(service, true);
		List<String> names = toolNames(tools);

		assertFalse(names.contains("buscar_artigo"));
		assertTrue(names.contains("tavily-search"));
		assertTrue(names.contains("tavily-extract"));
	}

	@Test
	void exactArticleQuestionsCallOwnMcpTool() throws Exception {
		RagQuestionService service = serviceWithTools(tool("buscar_artigo"));
		Set<String> toolsUsed = new LinkedHashSet<>();
		List<ToolCallback> tools = regulationTools(service, toolsUsed);

		String evidence = exactArticleEvidence(service, "Mostre o Art. 1 do regulamento.", tools);

		assertTrue(evidence.contains("buscar_artigo(1)"));
		assertTrue(toolsUsed.contains("buscar_artigo"));
	}

	@Test
	void generatedAnswerAcceptsNullGroundedFromModel() throws Exception {
		GeneratedAnswer answer = new ObjectMapper()
				.readValue("{\"answer\":\"Resposta baseada no contexto\",\"grounded\":null}", GeneratedAnswer.class);

		assertTrue(answer.answer().contains("contexto"));
		assertNull(answer.grounded());
	}

	@Test
	void parsesFirstJsonObjectWhenModelAddsTrailingJson() {
		RagQuestionService service = serviceWithTools();

		GeneratedAnswer answer = service.parseGeneratedAnswer("""
				{"answer":"Resposta baseada no contexto {com chaves}","grounded":true}
				{"extra":"ignored"}
				""");

		assertEquals("Resposta baseada no contexto {com chaves}", answer.answer());
		assertTrue(answer.grounded());
	}

	@Test
	void fallsBackToPlainTextWhenModelDoesNotReturnJson() {
		RagQuestionService service = serviceWithTools();

		GeneratedAnswer answer = service.parseGeneratedAnswer("Resposta direta baseada no contexto.");

		assertEquals("Resposta direta baseada no contexto.", answer.answer());
		assertTrue(answer.grounded());
	}

	private RagQuestionService serviceWithTools(ToolCallback... callbacks) {
		@SuppressWarnings("unchecked")
		ObjectProvider<ToolCallbackProvider> providers = mock(ObjectProvider.class);
		ToolCallbackProvider provider = () -> callbacks;
		when(providers.orderedStream()).thenReturn(Stream.of(provider));
		return new RagQuestionService(
				mock(ChatClient.class), mock(VectorStore.class),
				new RagProperties(6, 0.72, 10, 3500, 300, Path.of("corpus"), Path.of("metadata")),
				providers, new SimpleMeterRegistry(), CircuitBreakerRegistry.ofDefaults(), BulkheadRegistry.ofDefaults(),
				new ObjectMapper()
		);
	}

	@SuppressWarnings("unchecked")
	private List<ToolCallback> availableTools(RagQuestionService service, boolean verifyOnline) throws Exception {
		return availableTools(service, verifyOnline, new LinkedHashSet<String>());
	}

	@SuppressWarnings("unchecked")
	private List<ToolCallback> availableTools(
			RagQuestionService service,
			boolean verifyOnline,
			Set<String> toolsUsed
	) throws Exception {
		Method method = RagQuestionService.class.getDeclaredMethod("availableTools", boolean.class, Set.class);
		method.setAccessible(true);
		return (List<ToolCallback>) method.invoke(service, verifyOnline, toolsUsed);
	}

	private String exactArticleEvidence(RagQuestionService service, String question, List<ToolCallback> tools)
			throws Exception {
		Method method = RagQuestionService.class.getDeclaredMethod("exactArticleEvidence", String.class, List.class);
		method.setAccessible(true);
		return (String) method.invoke(service, question, tools);
	}

	@SuppressWarnings("unchecked")
	private List<ToolCallback> regulationTools(RagQuestionService service, Set<String> toolsUsed) throws Exception {
		Method method = RagQuestionService.class.getDeclaredMethod("regulationTools", Set.class);
		method.setAccessible(true);
		return (List<ToolCallback>) method.invoke(service, toolsUsed);
	}

	private List<String> toolNames(List<ToolCallback> tools) {
		return tools.stream().map(tool -> tool.getToolDefinition().name()).toList();
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
