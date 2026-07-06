package com.distributed_systems.regulationmcp;

import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
class RegulationTools {

	private final RegulationCatalog catalog;
	private final MeterRegistry meterRegistry;

	RegulationTools(RegulationCatalog catalog, MeterRegistry meterRegistry) {
		this.catalog = catalog;
		this.meterRegistry = meterRegistry;
	}

	@McpTool(name = "buscar_artigo", description = "Busca um artigo exato no Regulamento dos Cursos de Graduacao da UFRN")
	ArticleResult buscarArtigo(
			@McpToolParam(description = "Numero do artigo, apenas algarismos", required = true) String numero
	) {
		meterRegistry.counter("regulation.mcp.calls", "tool", "buscar_artigo").increment();
		String normalized = numero == null ? "" : numero.replaceAll("\\D", "");
		RegulationCatalog.Article article = catalog.snapshot().articles().get(normalized);
		if (article == null) {
			return new ArticleResult(normalized, false, "", 0, "Artigo nao encontrado");
		}
		return new ArticleResult(article.number(), true, article.section(), article.page(), article.text());
	}

	@McpTool(name = "listar_secoes", description = "Lista titulos, capitulos e secoes do regulamento versionado")
	List<String> listarSecoes() {
		meterRegistry.counter("regulation.mcp.calls", "tool", "listar_secoes").increment();
		return catalog.snapshot().sections();
	}

	@McpTool(name = "obter_metadados_regulamento", description = "Retorna versao, origem e checksum do regulamento")
	RegulationCatalog.Metadata obterMetadadosRegulamento() {
		meterRegistry.counter("regulation.mcp.calls", "tool", "obter_metadados_regulamento").increment();
		return catalog.snapshot().metadata();
	}

	record ArticleResult(String article, boolean found, String section, int page, String text) {
	}
}
