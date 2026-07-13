package com.distributed_systems.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CircuitBreakerRecoveryProperties.class)
@ConditionalOnProperty(
		prefix = "app.gateway.circuit-breaker-recovery",
		name = "enabled",
		havingValue = "true"
)
class CircuitBreakerRecoveryConfiguration {

	@Bean
	CircuitBreakerRecoveryProber circuitBreakerRecoveryProber(
			ReactorLoadBalancerExchangeFilterFunction loadBalancerFilter,
			ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory,
			CircuitBreakerRegistry circuitBreakerRegistry,
			CircuitBreakerRecoveryProperties properties,
			MeterRegistry meterRegistry
	) {
		WebClient webClient = WebClient.builder()
				.filter(loadBalancerFilter)
				.build();
		return new CircuitBreakerRecoveryProber(
				webClient,
				circuitBreakerFactory,
				circuitBreakerRegistry,
				properties,
				meterRegistry
		);
	}
}
