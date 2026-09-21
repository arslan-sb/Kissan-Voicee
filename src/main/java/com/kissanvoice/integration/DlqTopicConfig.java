package com.kissanvoice.integration;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import com.kissanvoice.outbox.AggregateType;

/**
 * Declares the dead-letter topic for each source topic this service consumes
 * from (see KafkaConsumerConfig). One partition is enough - these are for a
 * human to look at, not for throughput.
 */
@Configuration
public class DlqTopicConfig {

    @Bean
    NewTopic contributorDlt() {
        return TopicBuilder.name(AggregateType.CONTRIBUTOR.topic() + ".dlt").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic recordingDlt() {
        return TopicBuilder.name(AggregateType.RECORDING.topic() + ".dlt").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic milestoneDlt() {
        return TopicBuilder.name(AggregateType.MILESTONE.topic() + ".dlt").partitions(1).replicas(1).build();
    }
}
