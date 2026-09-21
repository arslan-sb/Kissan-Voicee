package com.kissanvoice.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls {@code outbox_event} for unpublished rows and sends them to Kafka.
 *
 * This is the other half of the outbox pattern: the HTTP request that wrote
 * the row already committed and returned - it never waits on the broker. If
 * Kafka is down, this method's own transaction is what fails, the row stays
 * unpublished with its attempt count bumped, and the next tick tries again.
 * The API stays responsive throughout.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;
    private final long sendTimeoutMillis;

    public OutboxPublisher(OutboxEventRepository repository,
                           KafkaTemplate<String, String> kafka,
                           @Value("${kissanvoice.outbox.batch-size:100}") int batchSize,
                           @Value("${kissanvoice.outbox.send-timeout-ms:5000}") long sendTimeoutMillis) {
        this.repository = repository;
        this.kafka = kafka;
        this.batchSize = batchSize;
        this.sendTimeoutMillis = sendTimeoutMillis;
    }

    @Scheduled(fixedDelayString = "${kissanvoice.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.lockNextBatch(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEvent event : batch) {
            try {
                // Keyed by aggregateId (contributorId for both topics today):
                // per-contributor ordering, not global ordering, which is the
                // guarantee that actually matters here.
                kafka.send(event.getAggregateType().topic(), event.getAggregateId().toString(),
                                event.getPayload())
                        .get(sendTimeoutMillis, TimeUnit.MILLISECONDS);
                event.markPublished();
            } catch (Exception ex) {
                event.incrementAttempts();
                log.warn("Outbox event {} ({}) not published, attempt {}: {}",
                        event.getId(), event.getEventType(), event.getAttempts(), ex.toString());
            }
        }

        repository.saveAll(batch);
    }
}
