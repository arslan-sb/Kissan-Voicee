package com.kissanvoice.contributor.api;

import com.kissanvoice.common.security.CurrentContributor;
import com.kissanvoice.common.security.TokenService;
import com.kissanvoice.contributor.ContributorService;
import com.kissanvoice.contributor.api.dto.ContributorResponse;
import com.kissanvoice.contributor.api.dto.RegisterContributorRequest;
import com.kissanvoice.contributor.api.dto.RegistrationResponse;
import com.kissanvoice.contributor.domain.Contributor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contributors")
@Tag(name = "Contributors", description = "Registration and profile")
public class ContributorController {

    private final ContributorService contributors;
    private final TokenService tokens;
    private final CurrentContributor current;

    public ContributorController(ContributorService contributors, TokenService tokens,
                                 CurrentContributor current) {
        this.contributors = contributors;
        this.tokens = tokens;
        this.current = current;
    }

    @PostMapping
    @SecurityRequirements
    @Operation(summary = "Register a contributor and receive a bearer token",
            description = "The only unauthenticated endpoint. Replaces the prototype's "
                    + "username-in-a-cookie, which carried no credential at all.")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegisterContributorRequest request,
            UriComponentsBuilder uri) {

        Contributor saved = contributors.register(
                request.displayName().trim(), request.phone(), request.locale());
        TokenService.IssuedToken token = tokens.issue(saved.getId(), saved.getDisplayName());

        RegistrationResponse body = new RegistrationResponse(
                ContributorResponse.from(saved),
                token.accessToken(), token.tokenType(), token.expiresAt());

        return ResponseEntity
                .created(uri.path("/api/v1/contributors/{id}").buildAndExpand(saved.getId()).toUri())
                .body(body);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a contributor profile")
    public ContributorResponse get(@PathVariable UUID id) {
        current.requireSelf(id);
        return ContributorResponse.from(contributors.require(id));
    }

    @GetMapping("/me")
    @Operation(summary = "Fetch the authenticated contributor")
    public ContributorResponse me() {
        return ContributorResponse.from(contributors.require(current.id()));
    }
}
