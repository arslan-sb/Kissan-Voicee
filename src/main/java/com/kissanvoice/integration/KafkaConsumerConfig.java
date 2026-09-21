package com.kissanvoice.integration;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * The outer safety net for every {@code @KafkaListener} in this service.
 * Resilience4j (see {@link WireMockCrmAdapter}, {@link MilestoneListener})
 * handles quick, in-process retries for a transient downstream failure; this
 * is what happens when that is not enough - the CRM is down for a while, or
 * its circuit breaker is open. Two redeliveries a second apart, then the
 * record is published to {@code <source-topic>.dlt} and the offset is
 * committed, so one bad message never blocks the partition behind it.
 *
 * Spring Boot's auto-configured listener container factory picks up the
 * single {@code CommonErrorHandler} bean in the context automatically, so
 * every listener gets this without further wiring.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        // -1 partition: let the producer choose. The DLT topics (DlqTopicConfig)
        // have one partition each - these are for a human to read, not for
        // throughput - while the source topics have three, so preserving the
        // source partition number would routinely ask for a partition the DLT
        // topic doesn't have.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".dlt", -1));

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 2L));
        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Redelivering {}-{}@{} (attempt {}): {}",
                        record.topic(), record.partition(), record.offset(), deliveryAttempt, ex.toString()));
        return handler;
    }
}
