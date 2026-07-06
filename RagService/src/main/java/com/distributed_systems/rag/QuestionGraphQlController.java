package com.distributed_systems.rag;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Controller
@ConditionalOnProperty(name = "app.rag.ai-enabled", havingValue = "true", matchIfMissing = true)
class QuestionGraphQlController {

	private final RagQuestionService questionService;

	QuestionGraphQlController(RagQuestionService questionService) {
		this.questionService = questionService;
	}

	@QueryMapping
	String serviceStatus() {
		return "UP";
	}

	@MutationMapping
	Mono<Answer> askQuestion(@Argument AskQuestionInput input) {
		if (input == null || input.conversationId() == null || input.conversationId().isBlank()
				|| input.question() == null || input.question().isBlank()) {
			return Mono.error(new IllegalArgumentException("conversationId and question are required"));
		}
		return Mono.fromCallable(() -> questionService.ask(input)).subscribeOn(Schedulers.boundedElastic());
	}

}
