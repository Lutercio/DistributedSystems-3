package com.distributed_systems.gateway;

import java.net.URI;
import java.time.Instant;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;

@RestController
class GatewayFallbackController {

	static final String FALLBACK_PATH = "/internal/gateway-fallback";

	@RequestMapping(FALLBACK_PATH)
	ResponseEntity<GatewayFallbackResponse> serviceUnavailable(ServerWebExchange exchange) {
		String requestPath = originalRequestPath(exchange);
		GatewayFallbackResponse response = new GatewayFallbackResponse(
				Instant.now(),
				HttpStatus.SERVICE_UNAVAILABLE.value(),
				HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
				"The downstream service is temporarily unavailable",
				requestPath,
				exchange.getRequest().getId()
		);

		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
	}

	private String originalRequestPath(ServerWebExchange exchange) {
		Set<URI> originalRequestUrls = exchange.getAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
		if (originalRequestUrls == null || originalRequestUrls.isEmpty()) {
			return exchange.getRequest().getPath().value();
		}

		return originalRequestUrls.iterator().next().getPath();
	}

	record GatewayFallbackResponse(
			Instant timestamp,
			int status,
			String error,
			String message,
			String path,
			String requestId
	) {
	}
}
