package com.distributed_systems.question.client;

import java.util.List;

public record RagAnswer(
		String conversationId,
		String answer,
		boolean grounded,
		boolean verifiedOnline,
		List<RagSource> sources,
		List<String> toolsUsed,
		String timestamp
) {
}
