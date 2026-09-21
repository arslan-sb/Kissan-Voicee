package com.kissanvoice.recording;

import com.kissanvoice.recording.domain.Recording;
import com.kissanvoice.recording.domain.RecordingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordingRepository extends JpaRepository<Recording, UUID> {

    long countByContributorIdAndStatus(UUID contributorId, RecordingStatus status);

    long countByStatus(RecordingStatus status);

    boolean existsByContributorIdAndQuestionIdAndStatus(UUID contributorId, UUID questionId,
                                                        RecordingStatus status);

    List<Recording> findByContributorIdAndStatusOrderByCreatedAtDesc(UUID contributorId,
                                                                      RecordingStatus status);

    Optional<Recording> findFirstByContributorIdAndStatusOrderByCreatedAtDesc(UUID contributorId,
                                                                               RecordingStatus status);
}
