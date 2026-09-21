package com.kissanvoice.integration;

import java.util.Map;
import java.util.UUID;

/**
 * Outbound port to whatever CRM sits downstream. {@link WireMockCrmAdapter} is
 * the only implementation today, standing in for "Unite CRM" against the
 * WireMock container; a second CRM means a second adapter and no change to
 * {@link CrmSyncListener} or the domain.
 */
public interface CrmPort {

    /**
     * Idempotent upsert of a contact. {@code idempotencyKey} should be the
     * source event's id, so a redelivered event (the consumer crashed after a
     * successful call but before committing its offset) does not create a
     * duplicate contact downstream.
     *
     * @return the CRM's own identifier for the contact
     */
    String upsertContact(UUID contributorId, String displayName, String phone, String locale,
                         String idempotencyKey);

    /** Appends an activity record to a contact. Same idempotency contract as above. */
    void appendActivity(UUID contributorId, String activityType, Map<String, Object> details,
                        String idempotencyKey);
}
