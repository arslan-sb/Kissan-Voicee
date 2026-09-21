package com.kissanvoice.corpus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One prompt in the corpus. Seeded from the prototype's global_questions.xlsx,
 * which was re-parsed from disk with pandas on every single request.
 */
@Entity
@Table(name = "question")
@Getter
@Setter
@NoArgsConstructor
public class Question {

    @Id
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(length = 500)
    private String category;

    @Column(length = 500)
    private String subcategory;

    @Column(nullable = false, length = 16)
    private String language = "ur";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
