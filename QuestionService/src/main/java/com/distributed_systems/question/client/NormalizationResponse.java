package com.distributed_systems.question.client;

import java.util.List;

public record NormalizationResponse(
		String normalizedQuestion,
		boolean valid,
		List<String> violations
) {

	public static NormalizationResponse fallback(String question) {
		String trimmed = question.trim();
		int length = trimmed.codePointCount(0, trimmed.length());
		if (length < 3) {
			return new NormalizationResponse(trimmed, false, List.of("QUESTION_TOO_SHORT"));
		}
		if (length > 1000) {
			return new NormalizationResponse(trimmed, false, List.of("QUESTION_TOO_LONG"));
		}
		return new NormalizationResponse(trimmed, true, List.of());
	}

}
