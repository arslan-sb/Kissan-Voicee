package com.kissanvoice.recording;

import com.kissanvoice.common.error.ConflictException;
import com.kissanvoice.common.error.ForbiddenException;
import com.kissanvoice.common.error.NotFoundException;
import com.kissanvoice.common.error.PayloadTooLargeException;
import com.kissanvoice.corpus.CorpusService;
import com.kissanvoice.corpus.domain.Question;
import com.kissanvoice.media.MediaStoragePort;
import com.kissanvoice.outbox.AggregateType;
import com.kissanvoice.outbox.OutboxWriter;
import com.kissanvoice.outbox.events.ContributorMilestoneReachedData;
import com.kissanvoice.outbox.events.RecordingCapturedData;
import com.kissanvoice.outbox.events.RecordingDeletedData;
import com.kissanvoice.recording.domain.Recording;
import com.kissanvoice.recording.domain.RecordingSession;
import com.kissanvoice.recording.domain.RecordingStatus;
import com.kissanvoice.recording.domain.SessionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RecordingService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "audio/webm", "audio/ogg", "audio/mpeg", "audio/mp4",
            "audio/wav", "audio/x-wav", "audio/x-m4a", "audio/aac");

    /**
     * Belt-and-braces alongside the servlet multipart limit (application.yml):
     * that one rejects at the container before this method even runs, but it
     * depends on multipart infrastructure being present, which this business
     * rule does not.
     */
    private static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    private final RecordingRepository recordings;
    private final RecordingSessionRepository sessions;
    private final CorpusService corpus;
    private final MediaStoragePort media;
    private final OutboxWriter outbox;
    private final int milestoneThreshold;

    public RecordingService(RecordingRepository recordings,
                            RecordingSessionRepository sessions,
                            CorpusService corpus,
                            MediaStoragePort media,
                            OutboxWriter outbox,
                            @Value("${kissanvoice.milestone.threshold:25}") int milestoneThreshold) {
        this.recordings = recordings;
        this.sessions = sessions;
        this.corpus = corpus;
        this.media = media;
        this.outbox = outbox;
        this.milestoneThreshold = milestoneThreshold;
    }

    @Transactional
    public Recording capture(UUID contributorId, UUID questionId, MultipartFile audio,
                             Integer durationMs) {
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("No audio file was uploaded.");
        }
        String contentType = audio.getContentType() == null
                ? "application/octet-stream"
                : audio.getContentType().split(";")[0].trim().toLowerCase();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported audio type '" + contentType + "'. Allowed: " + ALLOWED_TYPES);
        }
        if (audio.getSize() > MAX_AUDIO_BYTES) {
            throw new PayloadTooLargeException(
                    "Audio file is " + audio.getSize() + " bytes; the maximum is " + MAX_AUDIO_BYTES + ".");
        }

        Question question = corpus.require(questionId);

        // Fast path for the common case; the partial unique index below is the
        // real guarantee, since two concurrent uploads both pass this check.
        if (recordings.existsByContributorIdAndQuestionIdAndStatus(
                contributorId, questionId, RecordingStatus.ACCEPTED)) {
            throw new ConflictException("This contributor has already answered question " + questionId);
        }

        RecordingSession session = sessions
                .findFirstByContributorIdAndStatusOrderByStartedAtDesc(contributorId, SessionStatus.OPEN)
                .orElseGet(() -> sessions.save(RecordingSession.open(contributorId)));

        UUID recordingId = UUID.randomUUID();
        String key = "recordings/%s/%s/%s.%s".formatted(
                contributorId, question.getId(), recordingId, extensionFor(contentType));

        try (var in = audio.getInputStream()) {
            media.store(key, in, audio.getSize(), contentType);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to store audio for recording " + recordingId, ex);
        }

        Recording recording = Recording.capture(session.getId(), contributorId, question.getId(),
                key, contentType, audio.getSize(), durationMs);
        recording.setId(recordingId);

        Recording saved;
        try {
            saved = recordings.saveAndFlush(recording);
        } catch (DataIntegrityViolationException ex) {
            // uq_recording_answer fired: another request won the race.
            throw new ConflictException("This contributor has already answered question " + questionId);
        }

        // Written in the same transaction as the row above: either both commit
        // or neither does, so the Kafka message can never be lost to a crash
        // between the DB write and a direct publish.
        //
        // aggregateId is contributorId, not the recording's own id: per
        // docs/ROADMAP.md §7 this is the Kafka partition key
        // (OutboxPublisher), and per-contributor ordering is the guarantee
        // that matters here, not per-recording ordering. The recording's own
        // id still travels in the event payload (data.recordingId).
        outbox.append(AggregateType.RECORDING, contributorId, "RecordingCaptured",
                new RecordingCapturedData(saved.getId(), contributorId, question.getId(),
                        question.getCategory(), saved.getMediaKey(), durationMs));

        // Fires exactly once, the request that takes the count from
        // threshold-1 to threshold. A withdrawal that later drops the count
        // back down and a fresh capture that brings it back up would fire it
        // again - acceptable for an MVP milestone notification, worth a line
        // in the README rather than a dedup table for a case this rare.
        long acceptedCount = recordings.countByContributorIdAndStatus(contributorId, RecordingStatus.ACCEPTED);
        if (acceptedCount == milestoneThreshold) {
            outbox.append(AggregateType.MILESTONE, contributorId, "ContributorMilestoneReached",
                    new ContributorMilestoneReachedData(contributorId, acceptedCount, milestoneThreshold));
        }
        return saved;
    }

    /**
     * Withdraw a recording. Soft delete: the row keeps its audit trail and the
     * media object survives, which is why the question becomes available again
     * without anything being destroyed. The prototype deleted the spreadsheet
     * row and called os.remove() on the audio.
     */
    @Transactional
    public Recording withdraw(UUID contributorId, UUID recordingId) {
        Recording recording = recordings.findById(recordingId)
                .orElseThrow(() -> new NotFoundException("Recording", recordingId));
        if (!recording.getContributorId().equals(contributorId)) {
            throw new ForbiddenException("Recording " + recordingId + " belongs to another contributor.");
        }
        if (recording.getStatus() != RecordingStatus.ACCEPTED) {
            throw new ConflictException("Recording " + recordingId + " is already " + recording.getStatus());
        }
        recording.withdraw();
        Recording saved = recordings.save(recording);
        outbox.append(AggregateType.RECORDING, contributorId, "RecordingDeleted",
                new RecordingDeletedData(saved.getId(), contributorId, saved.getQuestionId()));
        return saved;
    }

    /** Most recent accepted recording - what the prototype's "undo" button acted on. */
    public Recording latestAccepted(UUID contributorId) {
        return recordings
                .findFirstByContributorIdAndStatusOrderByCreatedAtDesc(contributorId, RecordingStatus.ACCEPTED)
                .orElseThrow(() -> new NotFoundException("Recording for contributor", contributorId));
    }

    public long acceptedCount(UUID contributorId) {
        return recordings.countByContributorIdAndStatus(contributorId, RecordingStatus.ACCEPTED);
    }

    public String presignedUrl(UUID contributorId, UUID recordingId) {
        Recording recording = recordings.findById(recordingId)
                .orElseThrow(() -> new NotFoundException("Recording", recordingId));
        if (!recording.getContributorId().equals(contributorId)) {
            throw new ForbiddenException("Recording " + recordingId + " belongs to another contributor.");
        }
        return media.presignedUrl(recording.getMediaKey());
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "audio/webm" -> "webm";
            case "audio/ogg" -> "ogg";
            case "audio/mpeg" -> "mp3";
            case "audio/mp4", "audio/x-m4a" -> "m4a";
            case "audio/aac" -> "aac";
            case "audio/wav", "audio/x-wav" -> "wav";
            default -> "bin";
        };
    }
}
