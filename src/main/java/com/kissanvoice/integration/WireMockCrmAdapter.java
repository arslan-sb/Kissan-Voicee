package com.kissanvoice.integration;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter for the WireMock stand-in ("Unite CRM"). Every call is guarded by
 * the {@code crm} Resilience4j instance (application.yml): a handful of fast,
 * in-process retries with exponential backoff for a transient 5xx/429, and a
 * circuit breaker that trips once the CRM is reliably down so the next few
 * calls fail immediately instead of each waiting out its own retry budget.
 *
 * {@code @Retry} is applied outside {@code @CircuitBreaker} (Resilience4j's
 * default aspect order), and the retry instance only retries the exceptions
 * Spring's RestClient throws for 5xx/429 - not {@code CallNotPermittedException} -
 * so once the breaker opens, calls fail fast with no wasted retry attempts.
 * Whatever escapes this class propagates out of the Kafka listener, where
 * {@link KafkaConsumerConfig} takes over: a couple of redeliveries, then the
 * dead-letter topic.
 */
@Component
public class WireMockCrmAdapter implements CrmPort {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final RestClient restClient;

    public WireMockCrmAdapter(RestClient.Builder builder,
                              @Value("${kissanvoice.crm.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    @Retry(name = "crm")
    @CircuitBreaker(name = "crm")
    public String upsertContact(UUID contributorId, String displayName, String phone, String locale,
                                String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalId", contributorId.toString());
        body.put("displayName", displayName);
        body.put("phone", phone);
        body.put("locale", locale);

        CrmContactResponse response = restClient.post()
                .uri("/crm/v1/contacts")
                .header(IDEMPOTENCY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(CrmContactResponse.class);

        return response == null ? contributorId.toString() : response.id();
    }

    @Override
    @Retry(name = "crm")
    @CircuitBreaker(name = "crm")
    public void appendActivity(UUID contributorId, String activityType, Map<String, Object> details,
                               String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", activityType);
        body.put("details", details);

        // Keyed by our own contributorId rather than the CRM-assigned contact
        // id from upsertContact(): resolving that would mean this call
        // depends on ContributorRegistered having already been consumed on a
        // different, independently-partitioned topic. WireMock does not care
        // either way, and a real adapter would look the mapping up from the
        // contributor row instead of introducing that ordering dependency.
        restClient.patch()
                .uri("/crm/v1/contacts/{id}/activity", contributorId)
                .header(IDEMPOTENCY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private record CrmContactResponse(String id) {
    }
}
