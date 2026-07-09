package com.distributed_systems.rag;

import com.openai.errors.OpenAIIoException;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class GraphQlExceptionHandlerTests {

	private final GraphQlExceptionHandler handler = new GraphQlExceptionHandler();

	@Test
	void mapsRateLimitRejectionsToGraphQlErrorCode() {
		RequestNotPermitted exception = RequestNotPermitted.createRequestNotPermitted(RateLimiter.ofDefaults("groq"));

		GraphQLError error = handler.resolveToSingleError(exception, mock(DataFetchingEnvironment.class));

		assertEquals("Question capacity is temporarily exhausted", error.getMessage());
		assertEquals(GraphQlExceptionHandler.RATE_LIMIT_CODE, error.getExtensions().get("code"));
	}

	@Test
	void mapsOpenAiConnectivityFailuresToGraphQlErrorCode() {
		OpenAIIoException exception = new OpenAIIoException(
				"Request failed",
				new java.net.UnknownHostException("api.groq.com")
		);

		GraphQLError error = handler.resolveToSingleError(exception, mock(DataFetchingEnvironment.class));

		assertEquals("AI provider is temporarily unavailable. Try again later.", error.getMessage());
		assertEquals(GraphQlExceptionHandler.AI_PROVIDER_UNAVAILABLE_CODE, error.getExtensions().get("code"));
	}

	@Test
	void mapsCircuitBreakerRejectionsToGraphQlErrorCode() {
		CallNotPermittedException exception = CallNotPermittedException.createCallNotPermittedException(
				CircuitBreaker.ofDefaults("groq")
		);

		GraphQLError error = handler.resolveToSingleError(exception, mock(DataFetchingEnvironment.class));

		assertEquals("AI provider is temporarily unavailable. Try again later.", error.getMessage());
		assertEquals(GraphQlExceptionHandler.AI_PROVIDER_UNAVAILABLE_CODE, error.getExtensions().get("code"));
	}

	@Test
	void ignoresOtherExceptions() {
		GraphQLError error = handler.resolveToSingleError(
				new IllegalStateException("boom"),
				mock(DataFetchingEnvironment.class)
		);

		assertNull(error);
	}

}
