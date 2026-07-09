package com.distributed_systems.question.client;

import java.time.Duration;
import java.util.Map;

import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Component;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;

import reactor.core.publisher.Mono;

@Component
public class RagClient {

	private static final String RATE_LIMIT_CODE = "RATE_LIMIT_EXCEEDED";
	private static final String AI_PROVIDER_UNAVAILABLE_CODE = "AI_PROVIDER_UNAVAILABLE";
	private static final String AI_PROVIDER_UNAVAILABLE_MESSAGE =
			"AI provider is temporarily unavailable. Try again later.";

	private static final String ASK_QUESTION_MUTATION = """
			mutation AskQuestion($input: AskQuestionInput!) {
				askQuestion(input: $input) {
					conversationId
					answer
					grounded
					verifiedOnline
					sources {
						documentTitle
						article
						excerpt
						officialUrl
					}
					toolsUsed
					timestamp
				}
			}
			""";

	private final HttpGraphQlClient graphQlClient;
	private final Duration timeout;
	private final MeterRegistry meterRegistry;

	RagClient(HttpGraphQlClient graphQlClient, DownstreamProperties properties, MeterRegistry meterRegistry) {
		this.graphQlClient = graphQlClient;
		this.timeout = properties.ragTimeout();
		this.meterRegistry = meterRegistry;
	}

	@CircuitBreaker(name = "ragService")
	@Bulkhead(name = "ragService")
	@RateLimiter(name = "ragService")
	public Mono<RagAnswer> ask(
			String conversationId,
			String question,
			boolean verifyOfficialSource,
			String correlationId
	) {
		Map<String, Object> input = Map.of(
				"conversationId", conversationId,
				"question", question,
				"verifyOfficialSource", verifyOfficialSource
		);

		return graphQlClient.mutate()
				.header(CorrelationHeaders.CORRELATION_ID, correlationId)
				.build()
				.document(ASK_QUESTION_MUTATION)
				.variable("input", input)
				.execute()
					.timeout(timeout)
					.doOnError(error -> meterRegistry.counter("question.downstream.failures", "service", "rag").increment())
					.onErrorMap(error -> !(error instanceof DownstreamServiceException)
							&& !(error instanceof DownstreamAiProviderUnavailableException)
							&& !(error instanceof DownstreamRateLimitException)
							&& !(error instanceof RequestNotPermitted),
							error -> new DownstreamServiceException("RagService is unavailable", error))
				.flatMap(this::decodeAnswer);
	}

	private Mono<RagAnswer> decodeAnswer(ClientGraphQlResponse response) {
		if (!response.getErrors().isEmpty()) {
			if (hasRateLimitError(response)) {
				return Mono.error(new DownstreamRateLimitException("Question capacity is temporarily exhausted"));
			}
			if (hasAiProviderUnavailableError(response)) {
				return Mono.error(new DownstreamAiProviderUnavailableException(AI_PROVIDER_UNAVAILABLE_MESSAGE));
			}
			return Mono.error(new DownstreamServiceException("RagService returned GraphQL errors"));
		}
		if (!response.isValid()) {
			return Mono.error(new DownstreamServiceException("RagService returned GraphQL errors"));
		}
		RagAnswer answer = response.field("askQuestion").toEntity(RagAnswer.class);
		if (answer == null) {
			return Mono.error(new DownstreamServiceException("RagService returned no answer"));
		}
		return Mono.just(answer);
	}

	private boolean hasRateLimitError(ClientGraphQlResponse response) {
		return hasErrorCode(response, RATE_LIMIT_CODE);
	}

	private boolean hasAiProviderUnavailableError(ClientGraphQlResponse response) {
		return hasErrorCode(response, AI_PROVIDER_UNAVAILABLE_CODE);
	}

	private boolean hasErrorCode(ClientGraphQlResponse response, String code) {
		return response.getErrors().stream().anyMatch(error -> hasErrorCode(error, code));
	}

	private boolean hasErrorCode(ResponseError error, String code) {
		Map<String, Object> extensions = error.getExtensions();
		return extensions != null && code.equals(extensions.get("code"));
	}

}
