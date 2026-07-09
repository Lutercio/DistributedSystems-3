package com.distributed_systems.rag;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegulationCorpusReaderTests {

	@Test
	void verifiesAndExtractsVersionedRegulation() {
		RagProperties properties = new RagProperties(
				6, 0.72, 10, 3500, 300,
				Path.of("..", "knowledge-base", "regulamento_dos_cursos_de_graduacao.pdf"),
				Path.of("..", "knowledge-base", "metadata.properties")
		);
		RegulationCorpusReader reader = new RegulationCorpusReader(properties);

		RegulationCorpusReader.ParsedCorpus corpus = reader.read();

		assertTrue(corpus.articles().size() > 300);
		assertFalse(corpus.sections().isEmpty());
		assertTrue(reader.documents().stream()
				.anyMatch(document -> "1".equals(document.getMetadata().get("article"))));
		assertTrue(reader.documents().stream()
				.allMatch(document -> UUID.fromString(document.getId()).toString().equals(document.getId())));
		assertEquals(reader.documents().getFirst().getId(), new RegulationCorpusReader(properties).documents().getFirst().getId());
	}
}
