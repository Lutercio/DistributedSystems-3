package com.distributed_systems.normalizer;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class QuestionNormalizer {

	static final int MINIMUM_LENGTH = 3;
	static final int MAXIMUM_LENGTH = 1000;

	private static final Pattern LINE_ENDINGS = Pattern.compile("\\r\\n?|\\n");
	private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	@Bean
	Function<NormalizationRequest, NormalizationResponse> normalizeQuestion() {
		return request -> normalize(request == null ? null : request.question());
	}

	NormalizationResponse normalize(String question) {
		String value = question == null ? "" : question;
		value = LINE_ENDINGS.matcher(value).replaceAll("\n");
		value = CONTROL_CHARACTERS.matcher(value).replaceAll(" ");
		value = WHITESPACE.matcher(value).replaceAll(" ").trim();

		int length = value.codePointCount(0, value.length());
		if (length < MINIMUM_LENGTH) {
			return new NormalizationResponse(value, false, List.of("QUESTION_TOO_SHORT"));
		}
		if (length > MAXIMUM_LENGTH) {
			return new NormalizationResponse(value, false, List.of("QUESTION_TOO_LONG"));
		}
		return new NormalizationResponse(value, true, List.of());
	}

}
