package com.distributed_systems.question.client;

import java.time.Duration;
import java.util.Map;

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
						&& !(error instanceof RequestNotPermitted),
						error -> new DownstreamServiceException("RagService is unavailable", error))
				.flatMap(this::decodeAnswer);
	}

	private Mono<RagAnswer> decodeAnswer(ClientGraphQlResponse response) {
		if (!response.isValid() || !response.getErrors().isEmpty()) {
			return Mono.error(new DownstreamServiceException("RagService returned GraphQL errors"));
		}
		RagAnswer answer = response.field("askQuestion").toEntity(RagAnswer.class);
		if (answer == null) {
			return Mono.error(new DownstreamServiceException("RagService returned no answer"));
		}
		return Mono.just(answer);
	}

}
