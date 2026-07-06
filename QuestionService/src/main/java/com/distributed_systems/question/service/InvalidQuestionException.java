package com.distributed_systems.question.service;

import java.util.List;

public class InvalidQuestionException extends RuntimeException {

	private final List<String> violations;

	InvalidQuestionException(List<String> violations) {
		super("Question did not pass normalization validation");
		this.violations = List.copyOf(violations);
	}

	public List<String> getViolations() {
		return violations;
	}

}
