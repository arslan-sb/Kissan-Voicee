package com.kissanvoice.integration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, ProcessedMessageId> {
}
