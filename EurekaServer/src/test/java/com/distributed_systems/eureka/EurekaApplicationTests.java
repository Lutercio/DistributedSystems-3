package com.distributed_systems.eureka;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
		"server.port=0",
		"PORT=0",
		"CONFIG_SERVER_URL=http://localhost:8888",
		"spring.cloud.config.enabled=false",
		"eureka.client.register-with-eureka=false",
		"eureka.client.fetch-registry=false"
})
class EurekaApplicationTests {

	@Autowired
	private Environment environment;

	@Test
	void configuresResilientConfigClientBootstrap() throws IOException {
		Properties applicationProperties = PropertiesLoaderUtils.loadProperties(
				new FileSystemResource("src/main/resources/application.properties")
		);
		assertEquals(
				"configserver:${CONFIG_SERVER_URL}",
				applicationProperties.getProperty("spring.config.import")
		);
		assertEquals(6, environment.getProperty("spring.cloud.config.retry.max-attempts", Integer.class));
	}

}
