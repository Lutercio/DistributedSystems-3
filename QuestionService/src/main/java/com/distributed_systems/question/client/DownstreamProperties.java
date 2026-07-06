package com.distributed_systems.question.client;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "downstream")
public record DownstreamProperties(
		URI normalizerUrl,
		URI ragGraphqlUrl,
		Duration normalizerTimeout,
		Duration ragTimeout
) {
}
