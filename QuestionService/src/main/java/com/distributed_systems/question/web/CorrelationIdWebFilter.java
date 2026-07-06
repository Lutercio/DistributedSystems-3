package com.distributed_systems.question.web;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.distributed_systems.question.client.CorrelationHeaders;

import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdWebFilter implements WebFilter {

	static final String ATTRIBUTE = CorrelationIdWebFilter.class.getName() + ".correlationId";
	private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String supplied = exchange.getRequest().getHeaders().getFirst(CorrelationHeaders.CORRELATION_ID);
		String correlationId = supplied != null && SAFE_VALUE.matcher(supplied).matches()
				? supplied
				: UUID.randomUUID().toString();

		ServerWebExchange correlatedExchange = exchange.mutate()
				.request(request -> request.headers(headers ->
						headers.set(CorrelationHeaders.CORRELATION_ID, correlationId)))
				.build();
		correlatedExchange.getAttributes().put(ATTRIBUTE, correlationId);
		correlatedExchange.getResponse().getHeaders().set(CorrelationHeaders.CORRELATION_ID, correlationId);
		return chain.filter(correlatedExchange);
	}

	static String from(ServerWebExchange exchange) {
		return exchange.getAttributeOrDefault(ATTRIBUTE, "unknown");
	}

}
