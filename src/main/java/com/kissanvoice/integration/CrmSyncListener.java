package com.kissanvoice.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kissanvoice.contributor.ContributorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes the two topics that matter to the CRM and calls {@link CrmPort}.
 * Everything about surviving a flaky CRM - retry, circuit breaker, dead-letter
 * topic - lives one layer down, in {@link WireMockCrmAdapter} and
 * {@link KafkaConsumerConfig}; this class only has to be idempotent, via
 * {@link IdempotencyGuard}, and know which event types it cares about.
 */
@Component
public class CrmSyncListener {

    private static final String CONSUMER = "crm-sync";
    private static final Logger log = LoggerFactory.getLogger(CrmSyncListener.class);

    private final CrmPort crm;
    private final IdempotencyGuard idempotency;
    private final ContributorService contributors;
    private final ObjectMapper mapper;

    public CrmSyncListener(CrmPort crm, IdempotencyGuard idempotency, ContributorService contributors,
                           ObjectMapper mapper) {
        this.crm = crm;
        this.idempotency = idempotency;
        this.contributors = contributors;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "kissan.contributor.v1", groupId = "crm-sync")
    public void onContributorEvent(String payload) throws JsonProcessingException {
        JsonNode envelope = mapper.readTree(payload);
        UUID eventId = UUID.fromString(envelope.path("eventId").asText());
        if (idempotency.isProcessed(eventId, CONSUMER)) {
            log.debug("Skipping already-processed event {}", eventId);
            return;
        }

        if ("ContributorRegistered".equals(envelope.path("eventType").asText())) {
            JsonNode data = envelope.path("data");
            UUID contributorId = UUID.fromString(data.path("contributorId").asText());
            String displayName = data.path("displayName").asText();
            String phone = data.hasNonNull("phone") ? data.path("phone").asText() : null;
            String locale = data.path("locale").asText();

            String crmContactId = crm.upsertContact(contributorId, displayName, phone, locale, eventId.toString());
            contributors.linkCrmContact(contributorId, crmContactId);
        }

        idempotency.markProcessed(eventId, CONSUMER);
    }

    @KafkaListener(topics = "kissan.recording.v1", groupId = "crm-sync")
    public void onRecordingEvent(String payload) throws JsonProcessingException {
        JsonNode envelope = mapper.readTree(payload);
        UUID eventId = UUID.fromString(envelope.path("eventId").asText());
        if (idempotency.isProcessed(eventId, CONSUMER)) {
            log.debug("Skipping already-processed event {}", eventId);
            return;
        }

        // RecordingDeleted has no CRM-side effect in this MVP - a real
        // integration would likely log a "withdrawn" activity instead.
        if ("RecordingCaptured".equals(envelope.path("eventType").asText())) {
            JsonNode data = envelope.path("data");
            UUID contributorId = UUID.fromString(data.path("contributorId").asText());

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("recordingId", data.path("recordingId").asText());
            details.put("questionId", data.path("questionId").asText());
            details.put("category", data.hasNonNull("category") ? data.path("category").asText() : null);
            details.put("durationMs", data.hasNonNull("durationMs") ? data.path("durationMs").asInt() : null);

            crm.appendActivity(contributorId, "RECORDING_CAPTURED", details, eventId.toString());
        }

        idempotency.markProcessed(eventId, CONSUMER);
    }
}
