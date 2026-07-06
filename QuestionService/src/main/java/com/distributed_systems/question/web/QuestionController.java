package com.distributed_systems.question.web;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.distributed_systems.question.api.QuestionRequest;
import com.distributed_systems.question.api.QuestionResponse;
import com.distributed_systems.question.service.QuestionCoordinator;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/questions")
class QuestionController {

	private final QuestionCoordinator coordinator;

	QuestionController(QuestionCoordinator coordinator) {
		this.coordinator = coordinator;
	}

	@PostMapping
	Mono<QuestionResponse> ask(
			@Valid @RequestBody QuestionRequest request,
			ServerWebExchange exchange
	) {
		return coordinator.ask(request, CorrelationIdWebFilter.from(exchange));
	}

}
