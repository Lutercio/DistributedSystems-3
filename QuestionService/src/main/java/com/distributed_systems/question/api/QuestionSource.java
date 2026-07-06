package com.distributed_systems.question.api;

public record QuestionSource(
		String documentTitle,
		String article,
		String excerpt,
		String officialUrl
) {
}
