package com.distributed_systems.normalizer;

import java.util.List;

public record NormalizationResponse(
		String normalizedQuestion,
		boolean valid,
		List<String> violations
) {
}
