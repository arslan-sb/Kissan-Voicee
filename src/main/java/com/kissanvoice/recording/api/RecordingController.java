package com.kissanvoice.recording.api;

import com.kissanvoice.common.security.CurrentContributor;
import com.kissanvoice.corpus.CorpusService;
import com.kissanvoice.corpus.domain.Question;
import com.kissanvoice.recording.RecordingService;
import com.kissanvoice.recording.api.dto.NextQuestionResponse;
import com.kissanvoice.recording.api.dto.ProgressResponse;
import com.kissanvoice.recording.api.dto.RecordingResponse;
import com.kissanvoice.recording.domain.Recording;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Recordings", description = "Question assignment, capture and withdrawal")
public class RecordingController {

    private final RecordingService recordings;
    private final CorpusService corpus;
    private final CurrentContributor current;

    public RecordingController(RecordingService recordings, CorpusService corpus,
                               CurrentContributor current) {
        this.recordings = recordings;
        this.corpus = corpus;
        this.current = current;
    }

    @GetMapping("/contributors/{id}/next-question")
    @Operation(summary = "Next unanswered question for this contributor",
            description = "204 means the corpus is complete for this contributor - the "
                    + "prototype's completed.html case. Collapses the prototype's two "
                    + "duplicate endpoints (/questions/ and /get_next_question/) into one.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A question was assigned"),
            @ApiResponse(responseCode = "204", description = "No questions remain", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<NextQuestionResponse> nextQuestion(@PathVariable UUID id) {
        UUID contributorId = current.requireSelf(id);
        Optional<Question> next = corpus.nextQuestionFor(contributorId);
        if (next.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        long answered = recordings.acceptedCount(contributorId);
        return ResponseEntity.ok(NextQuestionResponse.of(next.get(), answered,
                corpus.remainingFor(contributorId), corpus.activeCount(), corpus.strategyName()));
    }

    @PostMapping(path = "/contributors/{id}/recordings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an answer recording",
            description = "409 if this contributor already answered the question - enforced by a "
                    + "partial unique index, not by a dataframe operation.")
    public ResponseEntity<RecordingResponse> capture(
            @PathVariable UUID id,
            @RequestParam("questionId") UUID questionId,
            @RequestPart("audio") MultipartFile audio,
            @RequestParam(value = "durationMs", required = false) Integer durationMs) {

        UUID contributorId = current.requireSelf(id);
        Recording saved = recordings.capture(contributorId, questionId, audio, durationMs);
        return ResponseEntity.status(201).body(RecordingResponse.from(saved));
    }

    @DeleteMapping("/recordings/{recordingId}")
    @Operation(summary = "Withdraw a recording",
            description = "Soft delete. The question becomes available again; the row and the "
                    + "audio object are retained.")
    public RecordingResponse withdraw(@PathVariable UUID recordingId) {
        return RecordingResponse.from(recordings.withdraw(current.id(), recordingId));
    }

    @DeleteMapping("/contributors/{id}/recordings/latest")
    @Operation(summary = "Withdraw the most recent recording (the prototype's undo button)")
    public RecordingResponse withdrawLatest(@PathVariable UUID id) {
        UUID contributorId = current.requireSelf(id);
        Recording latest = recordings.latestAccepted(contributorId);
        return RecordingResponse.from(recordings.withdraw(contributorId, latest.getId()));
    }

    @GetMapping("/recordings/{recordingId}/audio")
    @Operation(summary = "Time-limited URL for the audio",
            description = "Returns a link rather than streaming bytes through the API.")
    public Map<String, String> audioUrl(@PathVariable UUID recordingId) {
        return Map.of("url", recordings.presignedUrl(current.id(), recordingId));
    }

    @GetMapping("/contributors/{id}/progress")
    @Operation(summary = "Corpus coverage for this contributor")
    public ProgressResponse progress(@PathVariable UUID id) {
        UUID contributorId = current.requireSelf(id);
        return ProgressResponse.of(contributorId,
                recordings.acceptedCount(contributorId),
                corpus.remainingFor(contributorId),
                corpus.activeCount());
    }
}
