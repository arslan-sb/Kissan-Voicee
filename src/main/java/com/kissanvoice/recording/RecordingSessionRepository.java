package com.kissanvoice.recording;

import com.kissanvoice.recording.domain.RecordingSession;
import com.kissanvoice.recording.domain.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecordingSessionRepository extends JpaRepository<RecordingSession, UUID> {
    Optional<RecordingSession> findFirstByContributorIdAndStatusOrderByStartedAtDesc(
            UUID contributorId, SessionStatus status);
}
