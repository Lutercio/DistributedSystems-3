package com.distributed_systems.rag;

public record Source(
		String documentTitle,
		String article,
		String excerpt,
		String officialUrl
) {
}
