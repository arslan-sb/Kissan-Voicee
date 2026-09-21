package com.kissanvoice.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Consumes {@code kissan.milestone.v1} and calls the n8n "contributor
 * milestone" webhook (Block 6, integration/n8n/workflows/contributor-milestone.json).
 * n8n takes it from there: enrich from the API, tag the contact in the CRM
 * stub, post to the mock notification webhook.
 *
 * Same idempotency contract as CrmSyncListener, and the same shared
 * DefaultErrorHandler/DLT in KafkaConsumerConfig covers this listener too -
 * n8n being unreachable at startup is exactly the kind of transient failure
 * that combination is for.
 */
@Component
public class MilestoneListener {

    private static final String CONSUMER = "n8n-milestone";
    private static final Logger log = LoggerFactory.getLogger(MilestoneListener.class);

    private final RestClient restClient;
    private final IdempotencyGuard idempotency;
    private final ObjectMapper mapper;
    private final String webhookUrl;

    public MilestoneListener(RestClient.Builder builder, IdempotencyGuard idempotency, ObjectMapper mapper,
                             @Value("${kissanvoice.n8n.milestone-webhook}") String webhookUrl) {
        this.restClient = builder.build();
        this.idempotency = idempotency;
        this.mapper = mapper;
        this.webhookUrl = webhookUrl;
    }

    // @Retry on the listener method itself, not a private helper: Resilience4j's
    // annotation only intercepts calls that arrive through the Spring proxy, and
    // self-invocation (this class calling its own method) bypasses it entirely.
    // This method is invoked externally by the Kafka listener container, so the
    // proxy is in the call path and the annotation actually applies.
    @KafkaListener(topics = "kissan.milestone.v1", groupId = "n8n-milestone")
    @Retry(name = "n8n")
    public void onMilestoneEvent(String payload) throws JsonProcessingException {
        JsonNode envelope = mapper.readTree(payload);
        UUID eventId = UUID.fromString(envelope.path("eventId").asText());
        if (idempotency.isProcessed(eventId, CONSUMER)) {
            log.debug("Skipping already-processed event {}", eventId);
            return;
        }

        if ("ContributorMilestoneReached".equals(envelope.path("eventType").asText())) {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(envelope.path("data"))
                    .retrieve()
                    .toBodilessEntity();
        }

        idempotency.markProcessed(eventId, CONSUMER);
    }
}
