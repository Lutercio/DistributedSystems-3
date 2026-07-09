package com.distributed_systems.gateway;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.discovery.DiscoveryClientRouteDefinitionLocator;
import org.springframework.cloud.gateway.discovery.DiscoveryLocatorProperties;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.test.context.TestPropertySource;

import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
		"server.port=0",
		"PORT=0",
		"CONFIG_SERVER_URL=http://localhost:8888",
		"EUREKA_INSTANCE_HOSTNAME=localhost",
		"EUREKA_URL=http://localhost:8761/eureka/",
		"ZIPKIN_ENDPOINT=http://localhost:9411/api/v2/spans",
		"TRACING_SAMPLING_PROBABILITY=1.0",
		"spring.cloud.config.enabled=false",
		"eureka.client.enabled=false"
})
@TestPropertySource(locations = "file:../config-repository/config/gateway/application.properties")
class GatewayApplicationTests {

	@Autowired
	private Environment environment;

	@Autowired
	private LoadBalancerClientFactory loadBalancerClientFactory;

	@Autowired
	private ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;

	@Autowired
	private DiscoveryLocatorProperties discoveryProperties;

	@Autowired
	private List<RoutePredicateFactory> routePredicateFactories;

	@Autowired
	private List<GatewayFilterFactory> gatewayFilterFactories;

	@Autowired
	private GatewayProperties gatewayProperties;

	@Autowired
	private ConfigurationService configurationService;

	@Test
	void configuresConfigClientAndLoadBalancer() throws IOException {
		Properties applicationProperties = PropertiesLoaderUtils.loadProperties(
				new FileSystemResource("src/main/resources/application.properties")
		);
		assertEquals(
				"configserver:${CONFIG_SERVER_URL}",
				applicationProperties.getProperty("spring.config.import")
		);
		assertEquals(2000, environment.getProperty("spring.cloud.config.request-connect-timeout", Integer.class));
		assertNotNull(loadBalancerClientFactory);
		assertNotNull(circuitBreakerFactory);
		assertTrue(discoveryProperties.isEnabled());
		assertEquals("metadata['gateway-exposed'] == 'true'", discoveryProperties.getIncludeExpression());
	}

	@Test
	void exposesOnlyOptedInServicesAsLoadBalancedRoutes() {
		ReactiveDiscoveryClient discoveryClient = new StubDiscoveryClient();
		DiscoveryClientRouteDefinitionLocator locator =
				new DiscoveryClientRouteDefinitionLocator(discoveryClient, discoveryProperties);

		List<RouteDefinition> routes = locator.getRouteDefinitions().collectList().block();

		assertNotNull(routes);
		assertEquals(1, routes.size());
		RouteDefinition route = routes.get(0);
		assertEquals("lb://ORDERS-SERVICE", route.getUri().toString());
		assertTrue(route.getPredicates().get(0).getArgs().containsValue("/orders-service/**"));
		assertEquals("CircuitBreaker", route.getFilters().get(0).getName());
		assertEquals("orders-service", route.getFilters().get(0).getArgs().get("name"));
		assertTrue(!route.getFilters().get(0).getArgs().containsKey("statusCodes"));
		assertEquals("Retry", route.getFilters().get(1).getName());
		assertEquals("StripPrefix", route.getFilters().get(2).getName());
		assertEquals("1", route.getFilters().get(2).getArgs().get("parts"));

		RouteDefinitionRouteLocator executableLocator = new RouteDefinitionRouteLocator(
				() -> Flux.just(route),
				routePredicateFactories,
				gatewayFilterFactories,
				gatewayProperties,
				configurationService
		);
		List<Route> executableRoutes = executableLocator.getRoutes().collectList().block();
		assertNotNull(executableRoutes);
		assertEquals(1, executableRoutes.size());
	}

	private static final class StubDiscoveryClient implements ReactiveDiscoveryClient {

		@Override
		public String description() {
			return "Gateway route test discovery client";
		}

		@Override
		public Flux<ServiceInstance> getInstances(String serviceId) {
			Map<String, String> metadata = "ORDERS-SERVICE".equals(serviceId)
					? Map.of("gateway-exposed", "true")
					: Map.of();
			return Flux.just(new DefaultServiceInstance(
					serviceId + "-1",
					serviceId,
					"localhost",
					9090,
					false,
					metadata
			));
		}

		@Override
		public Flux<String> getServices() {
			return Flux.just("ORDERS-SERVICE", "INTERNAL-SERVICE");
		}
	}

}
