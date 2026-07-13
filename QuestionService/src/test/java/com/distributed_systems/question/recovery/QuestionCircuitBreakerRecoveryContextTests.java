package com.distributed_systems.question.recovery;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.distributed_systems.question.QuestionServiceApplication;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
		classes = QuestionServiceApplication.class,
		properties = {
				"server.port=0",
				"PORT=0",
				"CONFIG_SERVER_URL=http://localhost:8888",
				"EUREKA_INSTANCE_HOSTNAME=localhost",
				"EUREKA_URL=http://localhost:8761/eureka/",
				"ZIPKIN_ENDPOINT=http://localhost:9411/api/v2/spans",
				"TRACING_SAMPLING_PROBABILITY=1.0",
				"NORMALIZER_URL=http://normalizer-override/normalize",
				"RAG_GRAPHQL_URL=http://rag-override/graphql",
				"app.question-service.circuit-breaker-recovery.enabled=true",
				"app.question-service.circuit-breaker-recovery.interval=1h",
				"app.question-service.circuit-breaker-recovery.timeout=1s",
				"spring.config.import=",
				"spring.cloud.config.enabled=false",
				"eureka.client.enabled=false"
		}
)
@TestPropertySource(locations = "file:../config-repository/config/question-service/application.properties")
class QuestionCircuitBreakerRecoveryContextTests {

	@Autowired
	private QuestionCircuitBreakerRecoveryProber prober;

	@Autowired
	private QuestionCircuitBreakerRecoveryProperties properties;

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	@Test
	void configuresRecoveryTargetsAndCircuitBreakers() {
		assertNotNull(prober);
		assertEquals(Duration.ofHours(1), properties.interval());
		assertEquals(Duration.ofSeconds(1), properties.timeout());
		assertEquals(2, properties.targets().size());
		assertEquals("ragService", properties.targets().getFirst().breakerName());
		assertEquals("http://rag-service/actuator/health", properties.targets().getFirst().healthUri().toString());

		assertRecoveryConfiguration(circuitBreakerRegistry.circuitBreaker("ragService").getCircuitBreakerConfig());
		assertRecoveryConfiguration(circuitBreakerRegistry.circuitBreaker("normalizer").getCircuitBreakerConfig());
	}

	private void assertRecoveryConfiguration(CircuitBreakerConfig config) {
		assertTrue(config.isAutomaticTransitionFromOpenToHalfOpenEnabled());
		assertEquals(3, config.getPermittedNumberOfCallsInHalfOpenState());
	}
}
