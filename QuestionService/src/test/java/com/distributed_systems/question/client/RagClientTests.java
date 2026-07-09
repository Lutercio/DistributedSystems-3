package com.distributed_systems.question.client;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagClientTests {

	private final RagClient client = new RagClient(
			mock(HttpGraphQlClient.class),
			new DownstreamProperties(
					URI.create("http://normalizer/normalize"),
					URI.create("http://rag/graphql"),
					Duration.ofSeconds(1),
					Duration.ofSeconds(1)
			),
			new SimpleMeterRegistry()
	);

	@Test
	void mapsGraphQlRateLimitErrorToDownstreamRateLimitException() throws Exception {
		ClientGraphQlResponse response = response(error(Map.of("code", "RATE_LIMIT_EXCEEDED")));

		Mono<RagAnswer> answer = decodeAnswer(response);

		assertThrows(DownstreamRateLimitException.class, answer::block);
	}

	@Test
	void mapsGraphQlAiProviderErrorToDownstreamAiProviderUnavailableException() throws Exception {
		ClientGraphQlResponse response = response(error(Map.of("code", "AI_PROVIDER_UNAVAILABLE")));

		Mono<RagAnswer> answer = decodeAnswer(response);

		assertThrows(DownstreamAiProviderUnavailableException.class, answer::block);
	}

	@Test
	void mapsOtherGraphQlErrorsToDownstreamServiceException() throws Exception {
		ClientGraphQlResponse response = response(error(Map.of("code", "INTERNAL_ERROR")));

		Mono<RagAnswer> answer = decodeAnswer(response);

		assertThrows(DownstreamServiceException.class, answer::block);
	}

	@SuppressWarnings("unchecked")
	private Mono<RagAnswer> decodeAnswer(ClientGraphQlResponse response) throws Exception {
		Method method = RagClient.class.getDeclaredMethod("decodeAnswer", ClientGraphQlResponse.class);
		method.setAccessible(true);
		return (Mono<RagAnswer>) method.invoke(client, response);
	}

	private ClientGraphQlResponse response(ResponseError error) {
		ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
		when(response.getErrors()).thenReturn(List.of(error));
		return response;
	}

	private ResponseError error(Map<String, Object> extensions) {
		ResponseError error = mock(ResponseError.class);
		when(error.getExtensions()).thenReturn(extensions);
		return error;
	}

}
