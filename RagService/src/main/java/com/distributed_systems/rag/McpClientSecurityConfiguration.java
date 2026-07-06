package com.distributed_systems.rag;

import java.util.Set;

import org.springframework.ai.mcp.McpConnectionInfo;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.modelcontextprotocol.spec.McpSchema;

@Configuration(proxyBeanMethods = false)
class McpClientSecurityConfiguration {

	private static final Set<String> ALLOWED_TOOLS = Set.of(
			"buscar_artigo", "listar_secoes", "obter_metadados_regulamento",
			"tavily-search", "tavily-extract", "tavily_search", "tavily_extract"
	);

	@Bean
	McpToolFilter allowedMcpTools() {
		return (McpConnectionInfo connection, McpSchema.Tool tool) -> ALLOWED_TOOLS.contains(tool.name());
	}

}
