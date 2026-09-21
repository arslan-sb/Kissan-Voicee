package com.kissanvoice.contributor;

import com.kissanvoice.contributor.domain.Contributor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContributorRepository extends JpaRepository<Contributor, UUID> {
    Optional<Contributor> findByPhone(String phone);
    boolean existsByPhone(String phone);
}
