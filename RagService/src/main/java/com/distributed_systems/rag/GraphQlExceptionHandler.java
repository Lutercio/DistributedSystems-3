package com.distributed_systems.rag;

import java.util.Map;

import com.openai.errors.OpenAIIoException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

@Component
class GraphQlExceptionHandler extends DataFetcherExceptionResolverAdapter {

	static final String RATE_LIMIT_CODE = "RATE_LIMIT_EXCEEDED";
	static final String AI_PROVIDER_UNAVAILABLE_CODE = "AI_PROVIDER_UNAVAILABLE";

	@Override
	protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
		if (exception instanceof RequestNotPermitted) {
			return error("Question capacity is temporarily exhausted", RATE_LIMIT_CODE);
		}
		if (hasCause(exception, OpenAIIoException.class) || hasCause(exception, CallNotPermittedException.class)) {
			return error("AI provider is temporarily unavailable. Try again later.", AI_PROVIDER_UNAVAILABLE_CODE);
		}
		return null;
	}

	private GraphQLError error(String message, String code) {
		return GraphqlErrorBuilder.newError()
				.message(message)
				.errorType(ErrorType.INTERNAL_ERROR)
				.extensions(Map.of("code", code))
				.build();
	}

	private boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
		Throwable current = exception;
		while (current != null) {
			if (type.isInstance(current)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}
}
