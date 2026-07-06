package com.distributed_systems.regulationmcp;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegulationCatalogTests {

	@Test
	void exposesArticlesSectionsAndVerifiedMetadata() {
		RegulationCatalog catalog = new RegulationCatalog(new RegulationProperties(
				Path.of("..", "knowledge-base", "regulamento_dos_cursos_de_graduacao.pdf"),
				Path.of("..", "knowledge-base", "metadata.properties")
		));

		RegulationCatalog.Snapshot snapshot = catalog.snapshot();

		assertTrue(snapshot.articles().size() > 300);
		assertNotNull(snapshot.articles().get("1"));
		assertFalse(snapshot.sections().isEmpty());
		assertTrue(snapshot.metadata().sha256().matches("[0-9a-f]{64}"));
	}
}
