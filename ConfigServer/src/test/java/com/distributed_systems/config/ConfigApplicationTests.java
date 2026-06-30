package com.distributed_systems.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = {
		"server.port=0",
		"spring.profiles.active=native",
		"spring.cloud.config.server.native.search-locations=file:../config-repository/config,file:../config-repository/config/{application}"
})
class ConfigApplicationTests {

	@Autowired
	private EnvironmentRepository environmentRepository;

	@Test
	void servesCentralizedApplicationConfiguration() {
		Environment gateway = environmentRepository.findOne("gateway", "default", null);
		Environment eureka = environmentRepository.findOne("eureka-server", "default", null);

		assertFalse(gateway.getPropertySources().isEmpty());
		assertFalse(eureka.getPropertySources().isEmpty());
		assertEquals("true", property(gateway, "spring.cloud.gateway.server.webflux.discovery.locator.enabled"));
		assertEquals("true", property(eureka, "eureka.client.register-with-eureka"));
	}

	private Object property(Environment environment, String key) {
		return environment.getPropertySources().stream()
				.map(propertySource -> propertySource.getSource().get(key))
				.filter(value -> value != null)
				.findFirst()
				.orElseThrow();
	}

}
