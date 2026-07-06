package com.distributed_systems.rag;

import java.util.List;

public record Answer(
		String conversationId,
		String answer,
		boolean grounded,
		boolean verifiedOnline,
		List<Source> sources,
		List<String> toolsUsed,
		String timestamp
) {
}
