package com.kissanvoice.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A row written in the same transaction as the domain change it announces.
 * {@link OutboxPublisher} is the only thing that ever reads {@code payload}
 * back out and sends it to Kafka - the HTTP request that created this row
 * never talks to the broker directly, which is the entire point of the
 * pattern: a crash between the DB commit and a direct publish can no longer
 * lose the event.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private AggregateType aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    /** The full envelope from docs/ROADMAP.md §7, serialised as JSON text. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts = 0;

    public static OutboxEvent create(AggregateType aggregateType, UUID aggregateId,
                                     String eventType, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.id = UUID.randomUUID();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.createdAt = Instant.now();
        return event;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }
}
