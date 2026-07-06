package com.distributed_systems.rag;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
class TavilyMcpToolCallbackProvider implements ToolCallbackProvider, AutoCloseable {

	private static final String SEARCH_SCHEMA = """
			{"type":"object","properties":{"query":{"type":"string"},"max_results":{"type":"integer","minimum":1,"maximum":3}},"required":["query"],"additionalProperties":false}
			""";
	private static final String EXTRACT_SCHEMA = """
			{"type":"object","properties":{"urls":{"type":"array","items":{"type":"string"},"maxItems":3}},"required":["urls"],"additionalProperties":false}
			""";

	private final WebClient.Builder webClientBuilder;
	private final ObjectMapper objectMapper;
	private final String apiKey;
	private volatile McpSyncClient client;

	TavilyMcpToolCallbackProvider(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, Environment environment) {
		this.webClientBuilder = webClientBuilder;
		this.objectMapper = objectMapper;
		this.apiKey = environment.getProperty("TAVILY_API_KEY", "");
	}

	@Override
	public ToolCallback[] getToolCallbacks() {
		return new ToolCallback[] {
				callback("tavily-search", "Busca somente paginas oficiais da UFRN para verificar origem e vigencia", SEARCH_SCHEMA),
				callback("tavily-extract", "Extrai ate tres paginas oficiais da UFRN", EXTRACT_SCHEMA)
		};
	}

	private ToolCallback callback(String name, String description, String schema) {
		ToolDefinition definition = ToolDefinition.builder()
				.name(name)
				.description(description)
				.inputSchema(schema)
				.build();
		return new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return definition;
			}

			@Override
			public String call(String input) {
				Map<String, Object> arguments = parse(input);
				Map<String, Object> restricted = name.equals("tavily-search")
						? restrictSearch(arguments)
						: restrictExtract(arguments);
				return client().callTool(new McpSchema.CallToolRequest(name, restricted)).toString();
			}
		};
	}

	private Map<String, Object> parse(String input) {
		try {
			return objectMapper.readValue(input, new TypeReference<Map<String, Object>>() { });
		}
		catch (Exception exception) {
			throw new IllegalArgumentException("Invalid Tavily tool arguments", exception);
		}
	}

	private Map<String, Object> restrictSearch(Map<String, Object> arguments) {
		String query = String.valueOf(arguments.getOrDefault("query", "")).trim();
		if (query.isBlank()) {
			throw new IllegalArgumentException("Tavily search query is required");
		}
		int requested = arguments.get("max_results") instanceof Number number ? number.intValue() : 3;
		Map<String, Object> restricted = new LinkedHashMap<>();
		restricted.put("query", query);
		restricted.put("include_domains", List.of("ufrn.br"));
		restricted.put("max_results", Math.clamp(requested, 1, 3));
		restricted.put("search_depth", "basic");
		restricted.put("include_images", false);
		restricted.put("include_raw_content", false);
		return restricted;
	}

	private Map<String, Object> restrictExtract(Map<String, Object> arguments) {
		Object rawUrls = arguments.get("urls");
		List<?> values = rawUrls instanceof List<?> list ? list : List.of(rawUrls);
		List<String> urls = new ArrayList<>();
		for (Object value : values) {
			if (value == null || urls.size() == 3) {
				continue;
			}
			URI uri = URI.create(value.toString());
			String host = uri.getHost();
			if ("https".equalsIgnoreCase(uri.getScheme()) && host != null
					&& (host.equalsIgnoreCase("ufrn.br") || host.toLowerCase().endsWith(".ufrn.br"))) {
				urls.add(uri.toString());
			}
		}
		if (urls.isEmpty()) {
			throw new IllegalArgumentException("Only official HTTPS UFRN URLs may be extracted");
		}
		return Map.of("urls", urls, "include_images", false, "extract_depth", "basic");
	}

	private McpSyncClient client() {
		if (apiKey.isBlank()) {
			throw new IllegalStateException("TAVILY_API_KEY is not configured");
		}
		McpSyncClient value = client;
		if (value == null) {
			synchronized (this) {
				value = client;
				if (value == null) {
					WebClientStreamableHttpTransport transport = WebClientStreamableHttpTransport
							.builder(webClientBuilder.clone()
									.baseUrl("https://mcp.tavily.com")
									.defaultHeader("Authorization", "Bearer " + apiKey))
							.endpoint("/mcp/")
							.openConnectionOnStartup(false)
							.build();
					value = McpClient.sync(transport)
							.clientInfo(new McpSchema.Implementation("ufrn-responde", "0.0.1"))
							.requestTimeout(Duration.ofSeconds(3))
							.initializationTimeout(Duration.ofSeconds(3))
							.build();
					value.initialize();
					client = value;
				}
			}
		}
		return value;
	}

	@Override
	public void close() {
		McpSyncClient value = client;
		if (value != null) {
			value.closeGracefully();
		}
	}
}
