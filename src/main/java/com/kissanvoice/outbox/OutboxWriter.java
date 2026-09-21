package com.kissanvoice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Appends a row to {@code outbox_event}. Callers do this inside the same
 * {@code @Transactional} method that made the domain change - the write is
 * just another INSERT against the same connection, so it commits or rolls
 * back atomically with the change it describes. Nothing in this class talks
 * to Kafka; {@link OutboxPublisher} does that later, out of band.
 */
@Component
public class OutboxWriter {

    private static final int SCHEMA_VERSION = 1;

    private final OutboxEventRepository repository;
    private final ObjectMapper mapper;

    public OutboxWriter(OutboxEventRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public void append(AggregateType aggregateType, UUID aggregateId, String eventType, Object data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", eventType);
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("aggregateId", aggregateId.toString());
        envelope.put("data", data);

        String payload;
        try {
            payload = mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            // A programming error (an unserialisable data record), not a
            // runtime condition - fail loudly and roll back the transaction
            // rather than silently dropping the event.
            throw new IllegalStateException("Failed to serialise outbox event " + eventType, ex);
        }

        repository.save(OutboxEvent.create(aggregateType, aggregateId, eventType, payload));
    }
}
