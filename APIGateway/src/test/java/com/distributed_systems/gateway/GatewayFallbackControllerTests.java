package com.distributed_systems.gateway;

import java.net.URI;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;

class GatewayFallbackControllerTests {

	@Test
	void returnsAStableServiceUnavailableResponse() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get(GatewayFallbackController.FALLBACK_PATH).build()
		);
		LinkedHashSet<URI> originalUrls = new LinkedHashSet<>();
		originalUrls.add(URI.create("http://localhost:8080/orders-service/orders/42"));
		exchange.getAttributes().put(GATEWAY_ORIGINAL_REQUEST_URL_ATTR, originalUrls);

		ResponseEntity<GatewayFallbackController.GatewayFallbackResponse> response =
				new GatewayFallbackController().serviceUnavailable(exchange);

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(503, response.getBody().status());
		assertEquals("/orders-service/orders/42", response.getBody().path());
		assertEquals(exchange.getRequest().getId(), response.getBody().requestId());
		assertNotNull(response.getBody().timestamp());
	}
}
