package com.distributed_systems.question.api;

import java.util.List;

public record QuestionResponse(
		String conversationId,
		String answer,
		boolean grounded,
		boolean verifiedOnline,
		List<QuestionSource> sources,
		List<String> toolsUsed,
		String timestamp
) {
}
