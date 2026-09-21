package com.kissanvoice.corpus.api;

import com.kissanvoice.corpus.CorpusService;
import com.kissanvoice.corpus.api.dto.PagedResponse;
import com.kissanvoice.corpus.api.dto.QuestionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/questions")
@Tag(name = "Corpus", description = "The question corpus")
@Validated
public class QuestionController {

    private final CorpusService corpus;

    public QuestionController(CorpusService corpus) {
        this.corpus = corpus;
    }

    @GetMapping
    @Operation(summary = "Browse the corpus")
    public PagedResponse<QuestionResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        return PagedResponse.of(
                corpus.list(category, PageRequest.of(page, size, Sort.by("category", "id"))),
                QuestionResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single question")
    public QuestionResponse get(@PathVariable UUID id) {
        return QuestionResponse.from(corpus.require(id));
    }
}
