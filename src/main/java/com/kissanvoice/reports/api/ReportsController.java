package com.kissanvoice.reports.api;

import com.kissanvoice.reports.ReportsService;
import com.kissanvoice.reports.api.dto.CorpusCoverageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Corpus-wide aggregates for automation, not a single contributor")
public class ReportsController {

    private final ReportsService reports;

    public ReportsController(ReportsService reports) {
        this.reports = reports;
    }

    @GetMapping("/corpus-coverage")
    @SecurityRequirements
    @Operation(summary = "Corpus-wide answer coverage by category",
            description = "Polled by the nightly n8n schedule workflow (Block 6). Not scoped to a "
                    + "contributor, so it sits outside the per-contributor bearer-token model - see "
                    + "SecurityConfig for the same scoping call already made for actuator health. A real "
                    + "deployment would put this behind a service credential, not leave it open.")
    public CorpusCoverageResponse corpusCoverage() {
        return reports.corpusCoverage();
    }
}
