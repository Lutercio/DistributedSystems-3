package com.distributed_systems.rag;

public record AskQuestionInput(
		String conversationId,
		String question,
		boolean verifyOfficialSource
) {
}
