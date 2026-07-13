package com.distributed_systems.question.recovery;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.question-service.circuit-breaker-recovery")
record QuestionCircuitBreakerRecoveryProperties(
		boolean enabled,
		Duration interval,
		Duration timeout,
		List<Target> targets
) {

	private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(2);
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);
	private static final List<Target> DEFAULT_TARGETS = List.of(
			new Target("ragService", URI.create("http://rag-service/actuator/health")),
			new Target("normalizer", URI.create("http://question-normalizer/actuator/health"))
	);

	QuestionCircuitBreakerRecoveryProperties {
		interval = interval == null ? DEFAULT_INTERVAL : interval;
		timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
		targets = targets == null || targets.isEmpty() ? DEFAULT_TARGETS : List.copyOf(targets);

		if (interval.isZero() || interval.isNegative()) {
			throw new IllegalArgumentException("Recovery interval must be positive");
		}
		if (timeout.isZero() || timeout.isNegative()) {
			throw new IllegalArgumentException("Recovery timeout must be positive");
		}
	}

	record Target(String breakerName, URI healthUri) {

		Target {
			if (breakerName == null || breakerName.isBlank()) {
				throw new IllegalArgumentException("Recovery breaker name must not be blank");
			}
			if (healthUri == null || !healthUri.isAbsolute()) {
				throw new IllegalArgumentException("Recovery health URI must be absolute");
			}
		}
	}
}
