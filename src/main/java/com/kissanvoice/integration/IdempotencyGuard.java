package com.kissanvoice.integration;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Thin wrapper over {@code processed_message}. Deliberately two calls rather
 * than one atomic "claim": {@link #isProcessed} is checked before the
 * downstream call (CRM, n8n) is made, and {@link #markProcessed} is only
 * recorded after it succeeds. Marking it up front would make a message that
 * fails downstream look "done" forever, defeating the redelivery that the
 * Kafka error handler relies on to eventually get it to the CRM or the DLT.
 */
@Component
public class IdempotencyGuard {

    private final ProcessedMessageRepository repository;

    public IdempotencyGuard(ProcessedMessageRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isProcessed(UUID messageId, String consumer) {
        return repository.existsById(new ProcessedMessageId(messageId, consumer));
    }

    @Transactional
    public void markProcessed(UUID messageId, String consumer) {
        repository.save(ProcessedMessage.of(messageId, consumer));
    }
}
