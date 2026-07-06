package com.distributed_systems.rag;

import java.time.Instant;
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

	static Answer temporary(AskQuestionInput input) {
		return new Answer(
				input.conversationId(),
				"Resposta temporária do RagService para: " + input.question(),
				false,
				false,
				List.of(),
				List.of(),
				Instant.now().toString()
		);
	}

}
