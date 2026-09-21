package com.kissanvoice.contributor.api;

import com.kissanvoice.common.config.SecurityConfig;
import com.kissanvoice.common.security.CurrentContributor;
import com.kissanvoice.common.security.TokenService;
import com.kissanvoice.contributor.ContributorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Block 7's slice test: a bad request never reaches ContributorService (both
 * dependencies here are mocked and never invoked) because Bean Validation on
 * the request DTO short-circuits it first, and what comes back is a
 * well-formed RFC 9457 ProblemDetail - not the prototype's HTTP 200 with
 * {@code {'success': False}} in the body, which no HTTP-level client code
 * could distinguish from success without parsing.
 */
@WebMvcTest(ContributorController.class)
@Import(SecurityConfig.class)
class ContributorControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContributorService contributors;
    @MockitoBean
    private TokenService tokens;
    @MockitoBean
    private CurrentContributor current;

    @Test
    void blankDisplayNameReturnsAWellFormedProblemDetail() throws Exception {
        String body = """
                {"displayName": "", "phone": "not-a-valid-number", "locale": "ur-PK"}
                """;

        mockMvc.perform(post("/api/v1/contributors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.type").value("https://kissanvoice.dev/problems/validation-failed"))
                .andExpect(jsonPath("$.errors.displayName").exists())
                .andExpect(jsonPath("$.errors.phone").exists());
    }
}
