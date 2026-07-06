package com.distributed_systems.regulationmcp;

import java.nio.file.Path;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegulationToolsTests {

	private final RegulationTools tools = new RegulationTools(
			new RegulationCatalog(new RegulationProperties(
					Path.of("..", "knowledge-base", "regulamento_dos_cursos_de_graduacao.pdf"),
					Path.of("..", "knowledge-base", "metadata.properties"))),
			new SimpleMeterRegistry()
	);

	@Test
	void findsExactArticleAndRejectsUnknownArticle() {
		assertTrue(tools.buscarArtigo("Art. 1").found());
		assertFalse(tools.buscarArtigo("99999").found());
		assertFalse(tools.listarSecoes().isEmpty());
		assertTrue(tools.obterMetadadosRegulamento().sha256().matches("[0-9a-f]{64}"));
	}
}
