package com.distributed_systems.rag;

import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ingestion.enabled", havingValue = "true")
class CorpusIngestionRunner implements ApplicationRunner {

	private final RegulationCorpusReader corpusReader;
	private final VectorStore vectorStore;
	private final JdbcClient jdbcClient;
	private final MeterRegistry meterRegistry;
	private final ConfigurableApplicationContext applicationContext;
	private final boolean exitOnCompletion;

	CorpusIngestionRunner(
			RegulationCorpusReader corpusReader,
			VectorStore vectorStore,
			JdbcClient jdbcClient,
			MeterRegistry meterRegistry,
			ConfigurableApplicationContext applicationContext,
			org.springframework.core.env.Environment environment
	) {
		this.corpusReader = corpusReader;
		this.vectorStore = vectorStore;
		this.jdbcClient = jdbcClient;
		this.meterRegistry = meterRegistry;
		this.applicationContext = applicationContext;
		this.exitOnCompletion = environment.getProperty("app.ingestion.exit-on-completion", Boolean.class, true);
	}

	@Override
	public void run(ApplicationArguments arguments) {
		RegulationCorpusReader.ParsedCorpus corpus = corpusReader.read();
		jdbcClient.sql("""
				CREATE TABLE IF NOT EXISTS corpus_ingestion (
					checksum VARCHAR(64) PRIMARY KEY,
					corpus_version VARCHAR(128) NOT NULL,
					ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
				)
				""").update();
		boolean alreadyIngested = jdbcClient.sql("SELECT COUNT(*) FROM corpus_ingestion WHERE checksum = :checksum")
				.param("checksum", corpus.metadata().sha256())
				.query(Integer.class)
				.single() > 0;
		if (!alreadyIngested) {
			List<Document> documents = corpusReader.documents();
			vectorStore.add(documents);
			jdbcClient.sql("""
					INSERT INTO corpus_ingestion(checksum, corpus_version)
					VALUES (:checksum, :version)
					""")
					.param("checksum", corpus.metadata().sha256())
					.param("version", corpus.metadata().version())
					.update();
			meterRegistry.counter("rag.ingestion.documents").increment(documents.size());
		}
		if (exitOnCompletion) {
			applicationContext.close();
		}
	}
}
