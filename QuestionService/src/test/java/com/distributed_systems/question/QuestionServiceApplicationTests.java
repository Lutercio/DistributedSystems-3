package com.distributed_systems.question;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
		"server.port=0",
		"PORT=0",
		"CONFIG_SERVER_URL=http://localhost:8888",
		"EUREKA_INSTANCE_HOSTNAME=localhost",
		"EUREKA_URL=http://localhost:8761/eureka/",
		"ZIPKIN_ENDPOINT=http://localhost:9411/api/v2/spans",
		"TRACING_SAMPLING_PROBABILITY=1.0",
		"NORMALIZER_URL=http://normalizer-override/normalize",
		"RAG_GRAPHQL_URL=http://rag-override/graphql",
		"spring.config.import=",
		"spring.cloud.config.enabled=false",
		"eureka.client.enabled=false"
})
@TestPropertySource(locations = "file:../config-repository/config/question-service/application.properties")
class QuestionServiceApplicationTests {
	@Autowired
	private Environment environment;

	@Test
	void contextLoads() {
		assertEquals("http://normalizer-override/normalize", environment.getProperty("downstream.normalizer-url"));
		assertEquals("http://rag-override/graphql", environment.getProperty("downstream.rag-graphql-url"));
	}

}
