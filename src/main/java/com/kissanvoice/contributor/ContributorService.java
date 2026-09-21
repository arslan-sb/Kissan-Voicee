package com.kissanvoice.contributor;

import com.kissanvoice.common.error.ConflictException;
import com.kissanvoice.common.error.NotFoundException;
import com.kissanvoice.contributor.domain.Contributor;
import com.kissanvoice.outbox.AggregateType;
import com.kissanvoice.outbox.OutboxWriter;
import com.kissanvoice.outbox.events.ContributorRegisteredData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ContributorService {

    private final ContributorRepository contributors;
    private final OutboxWriter outbox;

    public ContributorService(ContributorRepository contributors, OutboxWriter outbox) {
        this.contributors = contributors;
        this.outbox = outbox;
    }

    @Transactional
    public Contributor register(String displayName, String phone, String locale) {
        if (phone != null && !phone.isBlank() && contributors.existsByPhone(phone)) {
            throw new ConflictException("A contributor with phone " + phone + " already exists.");
        }
        Contributor saved = contributors.save(Contributor.register(displayName,
                (phone == null || phone.isBlank()) ? null : phone, locale));
        outbox.append(AggregateType.CONTRIBUTOR, saved.getId(), "ContributorRegistered",
                new ContributorRegisteredData(saved.getId(), saved.getDisplayName(),
                        saved.getPhone(), saved.getLocale()));
        return saved;
    }

    public Contributor require(UUID id) {
        return contributors.findById(id)
                .orElseThrow(() -> new NotFoundException("Contributor", id));
    }
}
