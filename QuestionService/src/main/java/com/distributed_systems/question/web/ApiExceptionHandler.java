package com.distributed_systems.question.web;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import com.distributed_systems.question.api.ApiErrorResponse;
import com.distributed_systems.question.client.DownstreamAiProviderUnavailableException;
import com.distributed_systems.question.client.DownstreamRateLimitException;
import com.distributed_systems.question.client.DownstreamServiceException;
import com.distributed_systems.question.service.InvalidQuestionException;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;

@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(WebExchangeBindException.class)
	ResponseEntity<ApiErrorResponse> handleBinding(WebExchangeBindException exception, ServerWebExchange exchange) {
		List<String> details = exception.getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.toList();
		return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed", details, exchange);
	}

	@ExceptionHandler(ServerWebInputException.class)
	ResponseEntity<ApiErrorResponse> handleInput(ServerWebInputException exception, ServerWebExchange exchange) {
		return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is invalid", List.of(), exchange);
	}

	@ExceptionHandler(InvalidQuestionException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidQuestion(
			InvalidQuestionException exception,
			ServerWebExchange exchange
	) {
		return response(
				HttpStatus.BAD_REQUEST,
				"INVALID_QUESTION",
				exception.getMessage(),
				exception.getViolations(),
				exchange
		);
	}

	@ExceptionHandler(DownstreamServiceException.class)
	ResponseEntity<ApiErrorResponse> handleDownstream(
			DownstreamServiceException exception,
			ServerWebExchange exchange
	) {
		return response(
				HttpStatus.SERVICE_UNAVAILABLE,
				"RAG_SERVICE_UNAVAILABLE",
				exception.getMessage(),
				List.of(),
				exchange
		);
	}

	@ExceptionHandler(DownstreamAiProviderUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleAiProviderUnavailable(
			DownstreamAiProviderUnavailableException exception,
			ServerWebExchange exchange
	) {
		return response(
				HttpStatus.SERVICE_UNAVAILABLE,
				"AI_PROVIDER_UNAVAILABLE",
				exception.getMessage(),
				List.of(),
				exchange
		);
	}

	@ExceptionHandler(RequestNotPermitted.class)
	ResponseEntity<ApiErrorResponse> handleRateLimit(RequestNotPermitted exception, ServerWebExchange exchange) {
		return rateLimitResponse(exchange);
	}

	@ExceptionHandler(DownstreamRateLimitException.class)
	ResponseEntity<ApiErrorResponse> handleDownstreamRateLimit(
			DownstreamRateLimitException exception,
			ServerWebExchange exchange
	) {
		return rateLimitResponse(exchange);
	}

	private ResponseEntity<ApiErrorResponse> rateLimitResponse(ServerWebExchange exchange) {
		return response(
				HttpStatus.TOO_MANY_REQUESTS,
				"RATE_LIMIT_EXCEEDED",
				"Question capacity is temporarily exhausted",
				List.of(),
				exchange
		);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, ServerWebExchange exchange) {
		return response(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"INTERNAL_ERROR",
				"An unexpected error occurred",
				List.of(),
				exchange
		);
	}

	private ResponseEntity<ApiErrorResponse> response(
			HttpStatus status,
			String code,
			String message,
			List<String> details,
			ServerWebExchange exchange
	) {
		return ResponseEntity.status(status).body(new ApiErrorResponse(
				Instant.now().toString(),
				status.value(),
				code,
				message,
				CorrelationIdWebFilter.from(exchange),
				details
		));
	}

}
