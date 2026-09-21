package com.kissanvoice.corpus;

import com.kissanvoice.corpus.domain.Question;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Uniform random pick from the questions this contributor has not answered.
 *
 * Known limit, stated rather than hidden: ORDER BY random() scans the candidate
 * set, so it degrades as the corpus grows. At 175 questions it is irrelevant;
 * past ~100k rows it needs a keyset or TABLESAMPLE approach.
 */
@Component
public class RandomUnansweredStrategy implements NextQuestionStrategy {

    private final QuestionRepository questions;

    public RandomUnansweredStrategy(QuestionRepository questions) {
        this.questions = questions;
    }

    @Override
    public Optional<Question> nextFor(UUID contributorId) {
        return questions.findRandomUnanswered(contributorId);
    }

    @Override
    public String name() {
        return "random-unanswered";
    }
}
