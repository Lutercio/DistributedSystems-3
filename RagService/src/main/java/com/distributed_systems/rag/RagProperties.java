package com.distributed_systems.rag;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
		int topK,
		double similarityThreshold,
		int maxMemoryMessages,
		int chunkCharacters,
		int chunkOverlap,
		Path corpusPath,
		Path metadataPath
) {
}
