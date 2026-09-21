package com.kissanvoice.corpus;

import com.kissanvoice.corpus.domain.Question;

import java.util.Optional;
import java.util.UUID;

/**
 * How the next prompt is chosen for a contributor.
 *
 * Behind an interface because the choice is a product decision, not a technical
 * one: uniform random is the MVP, coverage-weighted selection (favour categories
 * with the least recorded audio) is the obvious successor.
 */
public interface NextQuestionStrategy {

    Optional<Question> nextFor(UUID contributorId);

    String name();
}
