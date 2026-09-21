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
@Table(name = "recording")
@Getter
@Setter
@NoArgsConstructor
public class Recording {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "contributor_id", nullable = false)
    private UUID contributorId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    /** Storage key, not a filesystem path - resolved by the MediaStoragePort. */
    @Column(name = "media_key", nullable = false, length = 512)
    private String mediaKey;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RecordingStatus status = RecordingStatus.ACCEPTED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public static Recording capture(UUID sessionId, UUID contributorId, UUID questionId,
                                    String mediaKey, String contentType, long sizeBytes,
                                    Integer durationMs) {
        Recording r = new Recording();
        r.id = UUID.randomUUID();
        r.sessionId = sessionId;
        r.contributorId = contributorId;
        r.questionId = questionId;
        r.mediaKey = mediaKey;
        r.contentType = contentType;
        r.sizeBytes = sizeBytes;
        r.durationMs = durationMs;
        r.status = RecordingStatus.ACCEPTED;
        r.createdAt = Instant.now();
        return r;
    }

    /** Soft delete. The media object and the audit trail survive. */
    public void withdraw() {
        this.status = RecordingStatus.DELETED;
    }
}
