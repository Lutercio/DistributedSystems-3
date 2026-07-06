package com.distributed_systems.rag;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
class QuestionGraphQlController {

	@QueryMapping
	String serviceStatus() {
		return "UP";
	}

	@MutationMapping
	Answer askQuestion(@Argument AskQuestionInput input) {
		return Answer.temporary(input);
	}

}
