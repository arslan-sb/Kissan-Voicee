package com.kissanvoice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.kissanvoice.TestcontainersConfiguration;
import com.kissanvoice.outbox.AggregateType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The resilience story for the CRM facade (Block 5), end to end through the
 * real Kafka listener - not a mocked CrmPort - against a WireMock instance
 * this test controls directly:
 *
 * <ol>
 *   <li>a transient 500 is absorbed by Resilience4j's retry and never reaches
 *       the dead-letter topic;</li>
 *   <li>a persistent 500 exhausts the retry, trips through the Kafka-level
 *       DefaultErrorHandler's redeliveries, and lands on
 *       {@code kissan.recording.v1.dlt}.</li>
 * </ol>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CrmResilienceTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void crmBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("kissanvoice.crm.base-url", wireMock::baseUrl);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired
    private KafkaContainer kafkaContainer;

    @BeforeEach
    void resetState() {
        wireMock.resetAll();
        // Each test's failure/success count is small; without this a breaker
        // left OPEN by a previous test would fail this one for an unrelated
        // reason.
        circuitBreakerRegistry.circuitBreaker("crm").reset();
    }

    @Test
    void transientFailureIsRetriedAndNeverReachesTheDeadLetterTopic() throws Exception {
        UUID contributorId = UUID.randomUUID();

        wireMock.stubFor(patch(urlPathMatching("/crm/v1/contacts/.*/activity"))
                .inScenario("retry-then-ok")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("recovered"));
        wireMock.stubFor(patch(urlPathMatching("/crm/v1/contacts/.*/activity"))
                .inScenario("retry-then-ok")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"LOGGED\"}")));

        publishRecordingCaptured(contributorId);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                wireMock.verify(2, patchRequestedFor(urlPathMatching("/crm/v1/contacts/.*/activity"))));

        assertThat(dltHasRecordFor(contributorId, Duration.ofSeconds(3)))
                .as("a retry that succeeds should never produce a dead letter")
                .isFalse();
    }

    @Test
    void persistentFailureExhaustsRetryAndLandsOnTheDeadLetterTopic() throws Exception {
        UUID contributorId = UUID.randomUUID();

        wireMock.stubFor(patch(urlPathMatching("/crm/v1/contacts/.*/activity"))
                .willReturn(aResponse().withStatus(500)));

        publishRecordingCaptured(contributorId);

        assertThat(dltHasRecordFor(contributorId, Duration.ofSeconds(20)))
                .as("a persistently failing CRM should end on kissan.recording.v1.dlt, not block the consumer forever")
                .isTrue();
    }

    private void publishRecordingCaptured(UUID contributorId) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordingId", UUID.randomUUID().toString());
        data.put("contributorId", contributorId.toString());
        data.put("questionId", UUID.randomUUID().toString());
        data.put("category", "test-category");
        data.put("mediaKey", "recordings/test/resilience-test.webm");
        data.put("durationMs", 1_000);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", "RecordingCaptured");
        envelope.put("schemaVersion", 1);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("aggregateId", data.get("recordingId"));
        envelope.put("data", data);

        kafkaTemplate.send(AggregateType.RECORDING.topic(), contributorId.toString(),
                        objectMapper.writeValueAsString(envelope))
                .get(5, TimeUnit.SECONDS);
    }

    /** Polls the DLT topic for up to {@code timeout} for a record keyed by this contributor. */
    private boolean dltHasRecordFor(UUID contributorId, Duration timeout) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(dltConsumerProps())) {
            consumer.subscribe(List.of(AggregateType.RECORDING.topic() + ".dlt"));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (contributorId.toString().equals(record.key())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private Map<String, Object> dltConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-dlt-reader-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return props;
    }
}
