package com.distributed_systems.rag;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_CONTAINER_TESTS", matches = "true")
class PgVectorIntegrationTests {

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:0.8.2-pg17-trixie");

	@Test
	void pgVectorExtensionCanBeCreated() throws Exception {
		try (var connection = DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
				var statement = connection.createStatement()) {
			statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
			try (var result = statement.executeQuery("SELECT extversion FROM pg_extension WHERE extname = 'vector'")) {
				result.next();
				assertNotNull(result.getString(1));
			}
		}
	}
}
