package com.kissanvoice.recording.domain;

public enum RecordingStatus {
    /** Counts towards corpus coverage and blocks the question from being re-served. */
    ACCEPTED,
    /** Withdrawn by the contributor. Row and media are retained; the prototype called os.remove(). */
    DELETED,
    /** Failed review. */
    REJECTED
}
