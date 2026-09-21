package com.kissanvoice.corpus;

/** Interface projection for {@link QuestionRepository#coverageByCategory()}. */
public interface CategoryCoverageRow {
    String getCategory();
    long getTotalQuestions();
    long getAnsweredQuestions();
}
