package com.distributed_systems.normalizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionNormalizerTests {

	private final QuestionNormalizer normalizer = new QuestionNormalizer();

	@Test
	void normalizesWhitespaceLineEndingsAndControlCharacters() {
		NormalizationResponse response = normalizer.normalize("  Como\r\n funciona\t o\u0000 trancamento?  ");

		assertTrue(response.valid());
		assertEquals("Como funciona o trancamento?", response.normalizedQuestion());
		assertTrue(response.violations().isEmpty());
	}

	@Test
	void normalizationIsIdempotent() {
		String once = normalizer.normalize("Como   funciona? ").normalizedQuestion();
		String twice = normalizer.normalize(once).normalizedQuestion();

		assertEquals(once, twice);
	}

	@Test
	void rejectsQuestionsOutsideLengthBounds() {
		NormalizationResponse shortResponse = normalizer.normalize("oi");
		NormalizationResponse longResponse = normalizer.normalize("a".repeat(1001));

		assertFalse(shortResponse.valid());
		assertEquals("QUESTION_TOO_SHORT", shortResponse.violations().getFirst());
		assertFalse(longResponse.valid());
		assertEquals("QUESTION_TOO_LONG", longResponse.violations().getFirst());
	}

	@Test
	void countsUnicodeCodePointsInsteadOfUtf16Units() {
		NormalizationResponse response = normalizer.normalize("á😀");

		assertFalse(response.valid());
		assertEquals("QUESTION_TOO_SHORT", response.violations().getFirst());
	}

}
