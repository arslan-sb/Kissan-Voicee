package com.kissanvoice.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics this service publishes to so they exist with sane
 * settings (partition count, replication) even against a broker that has
 * auto-creation disabled. Spring's {@code KafkaAdmin} picks up every
 * {@link NewTopic} bean and reconciles it on startup.
 *
 * The milestone topic is declared now, ahead of Block 6, so its partitioning
 * is decided deliberately rather than left to whatever the broker defaults to
 * the first time something happens to publish to it.
 */
@Configuration
public class KafkaTopicConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICATION_FACTOR = 1; // single-broker Redpanda dev container

    @Bean
    NewTopic contributorTopic() {
        return TopicBuilder.name(AggregateType.CONTRIBUTOR.topic())
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    NewTopic recordingTopic() {
        return TopicBuilder.name(AggregateType.RECORDING.topic())
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    NewTopic milestoneTopic() {
        return TopicBuilder.name(AggregateType.MILESTONE.topic())
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }
}
