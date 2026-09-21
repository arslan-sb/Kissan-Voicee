package com.kissanvoice.corpus;

import com.kissanvoice.common.error.NotFoundException;
import com.kissanvoice.corpus.domain.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CorpusService {

    private final QuestionRepository questions;
    private final NextQuestionStrategy strategy;

    public CorpusService(QuestionRepository questions, NextQuestionStrategy strategy) {
        this.questions = questions;
        this.strategy = strategy;
    }

    public Optional<Question> nextQuestionFor(UUID contributorId) {
        return strategy.nextFor(contributorId);
    }

    public String strategyName() {
        return strategy.name();
    }

    public Question require(UUID questionId) {
        return questions.findById(questionId)
                .orElseThrow(() -> new NotFoundException("Question", questionId));
    }

    public Page<Question> list(String category, Pageable pageable) {
        return (category == null || category.isBlank())
                ? questions.findByActiveTrue(pageable)
                : questions.findByActiveTrueAndCategoryIgnoreCase(category, pageable);
    }

    public long activeCount() {
        return questions.countByActiveTrue();
    }

    public long remainingFor(UUID contributorId) {
        return questions.countUnanswered(contributorId);
    }
}
