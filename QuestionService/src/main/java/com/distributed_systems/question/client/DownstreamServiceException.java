package com.distributed_systems.question.client;

public class DownstreamServiceException extends RuntimeException {

	public DownstreamServiceException(String message) {
		super(message);
	}

	public DownstreamServiceException(String message, Throwable cause) {
		super(message, cause);
	}

}
