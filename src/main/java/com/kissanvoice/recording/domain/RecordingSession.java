package com.kissanvoice.recording.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recording_session")
@Getter
@Setter
@NoArgsConstructor
public class RecordingSession {

    @Id
    private UUID id;

    @Column(name = "contributor_id", nullable = false)
    private UUID contributorId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SessionStatus status = SessionStatus.OPEN;

    public static RecordingSession open(UUID contributorId) {
        RecordingSession s = new RecordingSession();
        s.id = UUID.randomUUID();
        s.contributorId = contributorId;
        s.startedAt = Instant.now();
        s.status = SessionStatus.OPEN;
        return s;
    }
}
