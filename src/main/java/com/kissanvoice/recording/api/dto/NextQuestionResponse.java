package com.kissanvoice.recording.api.dto;

import com.kissanvoice.corpus.domain.Question;

import java.util.UUID;

public record NextQuestionResponse(
        UUID questionId,
        String text,
        String category,
        String language,
        int questionNumber,
        long answered,
        long remaining,
        long corpusSize,
        String strategy
) {
    public static NextQuestionResponse of(Question q, long answered, long remaining,
                                          long corpusSize, String strategy) {
        return new NextQuestionResponse(q.getId(), q.getText(), q.getCategory(), q.getLanguage(),
                (int) answered + 1, answered, remaining, corpusSize, strategy);
    }
}
