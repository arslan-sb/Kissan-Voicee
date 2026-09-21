package com.kissanvoice.integration;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumer-side idempotency record. At-least-once delivery means a consumer
 * can see the same Kafka record twice - most commonly because it committed
 * the CRM call but crashed before committing the offset. This table is what
 * turns that redelivery into a no-op instead of a duplicate CRM contact or
 * activity: see {@link IdempotencyGuard}.
 */
@Entity
@Table(name = "processed_message")
@Getter
@NoArgsConstructor
public class ProcessedMessage {

    @EmbeddedId
    private ProcessedMessageId id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    public static ProcessedMessage of(UUID messageId, String consumer) {
        ProcessedMessage m = new ProcessedMessage();
        m.id = new ProcessedMessageId(messageId, consumer);
        m.processedAt = Instant.now();
        return m;
    }
}
