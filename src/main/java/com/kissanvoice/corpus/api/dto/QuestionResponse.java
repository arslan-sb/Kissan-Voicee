package com.kissanvoice.corpus.api.dto;

import com.kissanvoice.corpus.domain.Question;

import java.util.UUID;

public record QuestionResponse(
        UUID id,
        String text,
        String category,
        String subcategory,
        String language
) {
    public static QuestionResponse from(Question q) {
        return new QuestionResponse(q.getId(), q.getText(), q.getCategory(),
                q.getSubcategory(), q.getLanguage());
    }
}
