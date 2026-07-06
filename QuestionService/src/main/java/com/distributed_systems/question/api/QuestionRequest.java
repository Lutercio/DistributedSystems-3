package com.distributed_systems.question.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record QuestionRequest(
		@Size(max = 128)
		@Pattern(regexp = "^$|[A-Za-z0-9._:-]+", message = "must contain only safe identifier characters")
		String conversationId,
		@NotBlank
		@Size(max = 1000)
		String question,
		Boolean verifyOfficialSource
) {
}
