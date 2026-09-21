package com.kissanvoice.corpus;

import com.kissanvoice.TestcontainersConfiguration;
import com.kissanvoice.contributor.ContributorRepository;
import com.kissanvoice.contributor.domain.Contributor;
import com.kissanvoice.corpus.domain.Question;
import com.kissanvoice.recording.RecordingRepository;
import com.kissanvoice.recording.RecordingSessionRepository;
import com.kissanvoice.recording.domain.Recording;
import com.kissanvoice.recording.domain.RecordingSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the query that replaced the prototype's
 * {@code pd.concat().drop_duplicates(keep=False).sample(1)} - a set-difference
 * implemented as a dataframe trick, which silently dropped BOTH copies of a
 * question whenever its text repeated. This is a NOT EXISTS query against a
 * real Postgres (Testcontainers), not a mock, because the bug class it
 * replaces was a SQL-shaped problem, not a Java one.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RandomUnansweredStrategyTest {

    @Autowired
    private QuestionRepository questions;
    @Autowired
    private RecordingRepository recordings;
    @Autowired
    private RecordingSessionRepository sessions;
    @Autowired
    private ContributorRepository contributors;
    @Autowired
    private TestEntityManager entityManager;

    private RandomUnansweredStrategy strategy;
    private Contributor contributor;

    @BeforeEach
    void setUp() {
        strategy = new RandomUnansweredStrategy(questions);

        // V2__seed_questions.sql has already loaded 175 real questions by the
        // time this test runs. Deactivating them gives each test a small,
        // deterministic corpus instead of a result that depends on how many
        // rows a migration happens to seed.
        questions.findAll().forEach(q -> q.setActive(false));
        entityManager.flush();

        contributor = contributors.save(Contributor.register("Test Farmer", null, "ur-PK"));
    }

    @Test
    void neverReturnsAQuestionThisContributorHasAlreadyAnswered() {
        List<Question> pool = questions.saveAll(List.of(
                newQuestion("Q1"), newQuestion("Q2"), newQuestion("Q3"), newQuestion("Q4"), newQuestion("Q5")));
        entityManager.flush();

        Question answered1 = pool.get(0);
        Question answered2 = pool.get(1);
        answer(answered1);
        answer(answered2);
        entityManager.flush();

        // Repeated because the strategy is random: a single lucky draw would
        // not have caught the prototype's bug either.
        for (int i = 0; i < 30; i++) {
            Optional<Question> next = strategy.nextFor(contributor.getId());
            assertThat(next).isPresent();
            assertThat(next.get().getId())
                    .isNotEqualTo(answered1.getId())
                    .isNotEqualTo(answered2.getId());
        }
    }

    @Test
    void returnsEmptyOnceEveryActiveQuestionHasBeenAnswered() {
        List<Question> pool = questions.saveAll(List.of(newQuestion("Q1"), newQuestion("Q2"), newQuestion("Q3")));
        entityManager.flush();

        pool.forEach(this::answer);
        entityManager.flush();

        assertThat(strategy.nextFor(contributor.getId())).isEmpty();
        assertThat(questions.countUnanswered(contributor.getId())).isZero();
    }

    private Question newQuestion(String text) {
        Question q = new Question();
        q.setId(UUID.randomUUID());
        q.setText(text);
        q.setCategory("test-category");
        q.setLanguage("ur");
        q.setActive(true);
        return q;
    }

    private void answer(Question question) {
        RecordingSession session = sessions.save(RecordingSession.open(contributor.getId()));
        recordings.save(Recording.capture(session.getId(), contributor.getId(), question.getId(),
                "recordings/test/" + question.getId() + ".webm", "audio/webm", 10L, 1_000));
    }
}
