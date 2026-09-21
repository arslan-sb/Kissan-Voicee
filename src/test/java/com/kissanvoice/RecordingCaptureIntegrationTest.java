package com.kissanvoice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kissanvoice.contributor.api.dto.RegistrationResponse;
import com.kissanvoice.outbox.AggregateType;
import com.kissanvoice.outbox.OutboxEvent;
import com.kissanvoice.outbox.OutboxEventRepository;
import com.kissanvoice.recording.api.dto.NextQuestionResponse;
import com.kissanvoice.recording.api.dto.RecordingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Block 7's flagship test: register a contributor, pull a question, upload
 * an answer, all through the real REST API against a real Postgres and a
 * real Kafka (Testcontainers) - then prove the two things a reviewer cannot
 * see just from the 201 response: the outbox row was written and eventually
 * marked published, and the RecordingCaptured event actually reached Kafka.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RecordingCaptureIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private OutboxEventRepository outboxEvents;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private KafkaContainer kafkaContainer;

    @Test
    void registerNextQuestionAndUpload_writesTheOutboxRowAndPublishesToKafka() {
        RegistrationResponse registration = register();
        UUID contributorId = registration.contributor().id();
        String token = registration.accessToken();

        NextQuestionResponse question = nextQuestion(contributorId, token);

        RecordingResponse recording = uploadRecording(contributorId, question.questionId(), token);

        // The outbox row: written in the same transaction as the recording,
        // then picked up and published by the scheduled poller (async, hence
        // the wait) - this is what makes the Kafka send unable to be lost to
        // a crash between the DB commit and a direct publish.
        //
        // aggregateId on a RECORDING event is contributorId (the Kafka
        // partition key), not the recording's own id - see RecordingService -
        // so the payload itself is what pins this row to this recording.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<OutboxEvent> events = outboxEvents.findAll().stream()
                    .filter(e -> e.getAggregateType() == AggregateType.RECORDING)
                    .filter(e -> e.getAggregateId().equals(contributorId))
                    .filter(e -> "RecordingCaptured".equals(e.getEventType()))
                    .filter(e -> recording.id().toString().equals(readTree(e.getPayload())
                            .path("data").path("recordingId").asText()))
                    .toList();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).isPublished()).isTrue();
        });

        // The Kafka message itself, read back with a plain consumer - not
        // trusting the outbox row's publishedAt flag alone.
        Optional<JsonNode> published = readPublishedEnvelope(contributorId, recording.id(), Duration.ofSeconds(5));
        assertThat(published).isPresent();
        JsonNode envelope = published.get();
        assertThat(envelope.path("eventType").asText()).isEqualTo("RecordingCaptured");
        assertThat(envelope.path("data").path("recordingId").asText()).isEqualTo(recording.id().toString());
        assertThat(envelope.path("data").path("questionId").asText()).isEqualTo(question.questionId().toString());
    }

    private RegistrationResponse register() {
        Map<String, String> body = Map.of("displayName", "Integration Test Farmer", "locale", "ur-PK");
        ResponseEntity<RegistrationResponse> response =
                rest.postForEntity("/api/v1/contributors", body, RegistrationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private NextQuestionResponse nextQuestion(UUID contributorId, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<NextQuestionResponse> response = rest.exchange(
                "/api/v1/contributors/{id}/next-question", HttpMethod.GET,
                new HttpEntity<>(headers), NextQuestionResponse.class, contributorId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private RecordingResponse uploadRecording(UUID contributorId, UUID questionId, String token) {
        HttpHeaders audioPartHeaders = new HttpHeaders();
        audioPartHeaders.setContentType(MediaType.valueOf("audio/webm"));
        Resource audio = new ByteArrayResource("fake-audio-bytes-for-the-integration-test".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "answer.webm";
            }
        };

        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add("questionId", questionId.toString());
        multipart.add("durationMs", "1500");
        multipart.add("audio", new HttpEntity<>(audio, audioPartHeaders));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<RecordingResponse> response = rest.postForEntity(
                "/api/v1/contributors/{id}/recordings", new HttpEntity<>(multipart, headers),
                RecordingResponse.class, contributorId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Optional<JsonNode> readPublishedEnvelope(UUID contributorId, UUID recordingId, Duration timeout) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-reader-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(AggregateType.RECORDING.topic()));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (!contributorId.toString().equals(record.key())) {
                        continue;
                    }
                    JsonNode envelope = readTree(record.value());
                    if (recordingId.toString().equals(envelope.path("data").path("recordingId").asText())) {
                        return Optional.of(envelope);
                    }
                }
            }
            return Optional.empty();
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
