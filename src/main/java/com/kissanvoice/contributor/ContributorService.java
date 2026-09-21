package com.kissanvoice.contributor;

import com.kissanvoice.common.error.ConflictException;
import com.kissanvoice.common.error.NotFoundException;
import com.kissanvoice.contributor.domain.Contributor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ContributorService {

    private final ContributorRepository contributors;

    public ContributorService(ContributorRepository contributors) {
        this.contributors = contributors;
    }

    @Transactional
    public Contributor register(String displayName, String phone, String locale) {
        if (phone != null && !phone.isBlank() && contributors.existsByPhone(phone)) {
            throw new ConflictException("A contributor with phone " + phone + " already exists.");
        }
        return contributors.save(Contributor.register(displayName,
                (phone == null || phone.isBlank()) ? null : phone, locale));
    }

    public Contributor require(UUID id) {
        return contributors.findById(id)
                .orElseThrow(() -> new NotFoundException("Contributor", id));
    }
}
