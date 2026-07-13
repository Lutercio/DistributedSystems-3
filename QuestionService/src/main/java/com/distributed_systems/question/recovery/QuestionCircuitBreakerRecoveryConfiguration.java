package com.distributed_systems.question.recovery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(QuestionCircuitBreakerRecoveryProperties.class)
@ConditionalOnProperty(
		prefix = "app.question-service.circuit-breaker-recovery",
		name = "enabled",
		havingValue = "true"
)
class QuestionCircuitBreakerRecoveryConfiguration {

	private static final int HALF_OPEN_PROBE_CALLS = 3;

	@Bean
	CircuitBreakerConfigCustomizer ragServiceRecoveryCircuitBreakerCustomizer() {
		return recoveryCircuitBreakerCustomizer("ragService");
	}

	@Bean
	CircuitBreakerConfigCustomizer normalizerRecoveryCircuitBreakerCustomizer() {
		return recoveryCircuitBreakerCustomizer("normalizer");
	}

	@Bean
	QuestionCircuitBreakerRecoveryProber questionCircuitBreakerRecoveryProber(
			ReactorLoadBalancerExchangeFilterFunction loadBalancerFilter,
			CircuitBreakerRegistry circuitBreakerRegistry,
			QuestionCircuitBreakerRecoveryProperties properties,
			MeterRegistry meterRegistry
	) {
		WebClient webClient = WebClient.builder()
				.filter(loadBalancerFilter)
				.build();
		return new QuestionCircuitBreakerRecoveryProber(
				webClient,
				circuitBreakerRegistry,
				properties,
				meterRegistry
		);
	}

	private CircuitBreakerConfigCustomizer recoveryCircuitBreakerCustomizer(String breakerName) {
		return CircuitBreakerConfigCustomizer.of(
				breakerName,
				builder -> builder
						.automaticTransitionFromOpenToHalfOpenEnabled(true)
						.permittedNumberOfCallsInHalfOpenState(HALF_OPEN_PROBE_CALLS)
		);
	}
}
