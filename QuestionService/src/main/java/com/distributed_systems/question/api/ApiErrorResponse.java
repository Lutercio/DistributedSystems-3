package com.distributed_systems.question.api;

import java.util.List;

public record ApiErrorResponse(
		String timestamp,
		int status,
		String code,
		String message,
		String correlationId,
		List<String> details
) {
}
