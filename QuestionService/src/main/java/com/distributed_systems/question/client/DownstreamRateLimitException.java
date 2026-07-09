package com.distributed_systems.question.client;

public class DownstreamRateLimitException extends RuntimeException {

	public DownstreamRateLimitException(String message) {
		super(message);
	}

}
