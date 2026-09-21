package com.kissanvoice.contributor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A field contributor recording answers.
 *
 * Replaces the prototype's notion of identity, which was a lowercase username
 * in a session cookie plus a directory on disk named after it.
 */
@Entity
@Table(name = "contributor")
@Getter
@Setter
@NoArgsConstructor
public class Contributor {

    @Id
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(length = 32, unique = true)
    private String phone;

    @Column(nullable = false, length = 16)
    private String locale = "ur-PK";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ContributorStatus status = ContributorStatus.ACTIVE;

    /** Populated by the CRM sync consumer once the contact exists downstream. */
    @Column(name = "crm_contact_id", length = 64)
    private String crmContactId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public static Contributor register(String displayName, String phone, String locale) {
        Contributor c = new Contributor();
        c.id = UUID.randomUUID();
        c.displayName = displayName;
        c.phone = phone;
        if (locale != null && !locale.isBlank()) {
            c.locale = locale;
        }
        c.status = ContributorStatus.ACTIVE;
        c.createdAt = Instant.now();
        return c;
    }
}
