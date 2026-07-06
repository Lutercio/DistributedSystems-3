package com.distributed_systems.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(properties = {
		"server.port=0",
		"PORT=0",
		"CONFIG_SERVER_URL=http://localhost:8888",
		"EUREKA_INSTANCE_HOSTNAME=localhost",
		"EUREKA_URL=http://localhost:8761/eureka/",
		"ZIPKIN_ENDPOINT=http://localhost:9411/api/v2/spans",
		"TRACING_SAMPLING_PROBABILITY=1.0",
		"POSTGRES_URL=jdbc:postgresql://localhost:5432/test",
		"POSTGRES_USER=test",
		"POSTGRES_PASSWORD=test",
		"OPENAI_API_KEY=test",
		"TAVILY_API_KEY=test",
		"app.rag.ai-enabled=false",
		"spring.ai.mcp.client.enabled=false",
		"spring.autoconfigure.exclude="
				+ "org.springframework.ai.model.chat.memory.repository.jdbc.autoconfigure.JdbcChatMemoryRepositoryAutoConfiguration,"
				+ "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration,"
				+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
		"spring.config.import=",
		"spring.cloud.config.enabled=false",
		"eureka.client.enabled=false"
})
@TestPropertySource(locations = "file:../config-repository/config/rag-service/application.properties")
class RagServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
