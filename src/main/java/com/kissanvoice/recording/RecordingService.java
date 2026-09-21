package com.kissanvoice.recording;

import com.kissanvoice.common.error.ConflictException;
import com.kissanvoice.common.error.ForbiddenException;
import com.kissanvoice.common.error.NotFoundException;
import com.kissanvoice.corpus.CorpusService;
import com.kissanvoice.corpus.domain.Question;
import com.kissanvoice.media.MediaStoragePort;
import com.kissanvoice.recording.domain.Recording;
import com.kissanvoice.recording.domain.RecordingSession;
import com.kissanvoice.recording.domain.RecordingStatus;
import com.kissanvoice.recording.domain.SessionStatus;
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

    private final RecordingRepository recordings;
    private final RecordingSessionRepository sessions;
    private final CorpusService corpus;
    private final MediaStoragePort media;

    public RecordingService(RecordingRepository recordings,
                            RecordingSessionRepository sessions,
                            CorpusService corpus,
                            MediaStoragePort media) {
        this.recordings = recordings;
        this.sessions = sessions;
        this.corpus = corpus;
        this.media = media;
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

        try {
            return recordings.saveAndFlush(recording);
        } catch (DataIntegrityViolationException ex) {
            // uq_recording_answer fired: another request won the race.
            throw new ConflictException("This contributor has already answered question " + questionId);
        }
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
        return recordings.save(recording);
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
