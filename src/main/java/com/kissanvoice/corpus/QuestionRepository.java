package com.kissanvoice.corpus;

import com.kissanvoice.corpus.domain.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    Page<Question> findByActiveTrue(Pageable pageable);

    Page<Question> findByActiveTrueAndCategoryIgnoreCase(String category, Pageable pageable);

    /**
     * One active question this contributor has not yet answered, chosen at random.
     *
     * This replaces the prototype's approach: load the whole corpus and the whole
     * per-user sheet into pandas, concat them, drop_duplicates(keep=False) to fake
     * a set difference, then .sample(1). That was O(corpus) work per request and
     * silently dropped BOTH copies whenever a question text appeared twice.
     */
    @Query(value = """
            SELECT q.* FROM question q
            WHERE q.active
              AND NOT EXISTS (
                  SELECT 1 FROM recording r
                  WHERE r.question_id = q.id
                    AND r.contributor_id = :contributorId
                    AND r.status = 'ACCEPTED')
            ORDER BY random()
            LIMIT 1
            """, nativeQuery = true)
    Optional<Question> findRandomUnanswered(@Param("contributorId") UUID contributorId);

    @Query(value = """
            SELECT count(*) FROM question q
            WHERE q.active
              AND NOT EXISTS (
                  SELECT 1 FROM recording r
                  WHERE r.question_id = q.id
                    AND r.contributor_id = :contributorId
                    AND r.status = 'ACCEPTED')
            """, nativeQuery = true)
    long countUnanswered(@Param("contributorId") UUID contributorId);

    long countByActiveTrue();

    /** Per-category totals for the nightly corpus report (Block 6). */
    @Query(value = """
            SELECT q.category AS category,
                   count(q.id) AS "totalQuestions",
                   count(DISTINCT r.question_id) AS "answeredQuestions"
            FROM question q
            LEFT JOIN recording r ON r.question_id = q.id AND r.status = 'ACCEPTED'
            WHERE q.active
            GROUP BY q.category
            ORDER BY q.category
            """, nativeQuery = true)
    List<CategoryCoverageRow> coverageByCategory();
}
