package com.distributed_systems.question;

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
		"spring.config.import=",
		"spring.cloud.config.enabled=false",
		"eureka.client.enabled=false"
})
@TestPropertySource(locations = "file:../config-repository/config/question-service/application.properties")
class QuestionServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
