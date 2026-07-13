package com.distributed_systems.gateway;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gateway.circuit-breaker-recovery")
record CircuitBreakerRecoveryProperties(
		boolean enabled,
		Duration interval,
		Duration timeout,
		String healthPath
) {

	private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(2);
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);
	private static final String DEFAULT_HEALTH_PATH = "/actuator/health";

	CircuitBreakerRecoveryProperties {
		interval = interval == null ? DEFAULT_INTERVAL : interval;
		timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
		healthPath = healthPath == null || healthPath.isBlank() ? DEFAULT_HEALTH_PATH : healthPath;

		if (interval.isZero() || interval.isNegative()) {
			throw new IllegalArgumentException("Recovery interval must be positive");
		}
		if (timeout.isZero() || timeout.isNegative()) {
			throw new IllegalArgumentException("Recovery timeout must be positive");
		}
		if (!healthPath.startsWith("/")) {
			throw new IllegalArgumentException("Recovery health path must start with '/'");
		}
	}
}
