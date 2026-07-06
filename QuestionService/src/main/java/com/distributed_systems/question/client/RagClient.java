package com.distributed_systems.question.client;

import java.time.Duration;
import java.util.Map;

import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Component;

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

	RagClient(HttpGraphQlClient graphQlClient, DownstreamProperties properties) {
		this.graphQlClient = graphQlClient;
		this.timeout = properties.ragTimeout();
	}

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
				.onErrorMap(error -> !(error instanceof DownstreamServiceException),
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
