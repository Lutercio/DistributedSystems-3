package com.distributed_systems.regulationmcp;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "regulation")
record RegulationProperties(Path corpusPath, Path metadataPath) {
}
