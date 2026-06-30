package com.distributed_systems.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
		"server.port=0",
		"spring.cloud.config.enabled=false",
		"eureka.client.register-with-eureka=false",
		"eureka.client.fetch-registry=false"
})
class EurekaApplicationTests {

	@Autowired
	private Environment environment;

	@Test
	void configuresResilientConfigClientBootstrap() {
		assertEquals(
				"optional:configserver:http://localhost:8888",
				environment.getProperty("spring.config.import")
		);
		assertEquals(6, environment.getProperty("spring.cloud.config.retry.max-attempts", Integer.class));
	}

}
