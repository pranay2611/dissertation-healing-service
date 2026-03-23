package com.dissertation.fixsuggestion.controller;

import com.dissertation.fixsuggestion.model.internal.*;
import com.dissertation.fixsuggestion.model.request.FixSuggestionRequest;
import com.dissertation.fixsuggestion.model.request.SourceFile;
import com.dissertation.fixsuggestion.model.response.CodeChange;
import com.dissertation.fixsuggestion.model.response.FailureFix;
import com.dissertation.fixsuggestion.model.response.FixSuggestionResponse;
import com.dissertation.fixsuggestion.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration-style controller test for POST /api/fix-suggestions.
 *
 * Uses @WebMvcTest to load only the controller layer with all services mocked.
 * Tests cover the four failure types from the actual dissertation test run (2026-03-01):
 *   1. CONTRACT_VIOLATION   — wrong request body → 400
 *   2. INVALID_INPUT_HANDLING — BigDecimal parse error → 500
 *   3. WRONG_ASSERTION      — correct service, wrong expected status code
 *   4. WRONG_ENDPOINT       — test called /api/users/register instead of /api/auth/register
 */
@WebMvcTest(FixSuggestionController.class)
class FixSuggestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private TestResultParserService parserService;
    @MockBean private FailureCategorizationService categorizationService;
    @MockBean private CodeSageEmbeddingService embeddingService;
    @MockBean private ClaudePromptBuilderService promptBuilderService;
    @MockBean private ClaudeApiService claudeApiService;

    private FixSuggestionRequest sampleRequest;
    private List<TestCase> sampleFailures;

    @BeforeEach
    void setUp() {
        // ── Build 4 failed test cases matching the real dissertation test run ──

        sampleFailures = List.of(
                buildFailedTest(
                        "testUserRegistrationWrongContract_IntendedToFail",
                        "expected [200] but found [400]. Bad Request /api/auth/register",
                        "java.lang.AssertionError\n\tat org.testng.Assert.fail\n\tat MicroservicesIntegrationTest.java:1139"
                ),
                buildFailedTest(
                        "testOrderCreationInternalError_IntendedToFail",
                        "expected [201] but found [500]. JSON parse error: Cannot deserialize BigDecimal from 'not_a_number'",
                        "java.lang.AssertionError\n\tat org.testng.Assert.fail\n\tat MicroservicesIntegrationTest.java:1175"
                ),
                buildFailedTest(
                        "testUserRegistrationWrongAssertion_IntendedToFail",
                        "registration returns 200, not 201. expected [201] but found [200]",
                        "java.lang.AssertionError\n\tat org.testng.Assert.fail\n\tat MicroservicesIntegrationTest.java:1192"
                ),
                buildFailedTest(
                        "testUserRegistrationWrongEndpoint_IntendedToFail",
                        "expected [200] but found [404]. Not Found /api/users/register",
                        "java.lang.AssertionError\n\tat org.testng.Assert.fail\n\tat MicroservicesIntegrationTest.java:1219"
                )
        );

        // ── Build source files ──
        SourceFile authController = new SourceFile();
        authController.setFilePath("src/main/java/.../AuthController.java");
        authController.setContent("@RestController @RequestMapping(\"/api/auth\") public class AuthController { ... }");

        SourceFile orderController = new SourceFile();
        orderController.setFilePath("src/main/java/.../OrderController.java");
        orderController.setContent("@RestController @RequestMapping(\"/api/orders\") public class OrderController { ... }");

        FixSuggestionRequest.MicroserviceContext ctx = new FixSuggestionRequest.MicroserviceContext();
        ctx.setServiceName("user-service");
        ctx.setBuildTool("maven");
        ctx.setSpringBootVersion("3.2.3");
        ctx.setSourceFiles(List.of(authController, orderController));

        TestResultReport report = new TestResultReport();
        TestSuite suite = new TestSuite();
        suite.setTestCases(sampleFailures);
        report.setSuites(List.of(suite));

        sampleRequest = new FixSuggestionRequest();
        sampleRequest.setTestResultJson(report);
        sampleRequest.setMicroserviceContext(ctx);
    }

    // ─────────────────────────────────────────────────────────────────
    // Happy path: all 4 failures processed, response shape is correct
    // ─────────────────────────────────────────────────────────────────

    @Test
    void shouldReturn200WithFixesForAllFourFailures() throws Exception {
        // Arrange
        when(parserService.extractFailures(any())).thenReturn(sampleFailures);
        when(embeddingService.generateEmbeddings(any())).thenReturn(Map.of());
        when(embeddingService.findRelevantFiles(any(), any(), anyInt())).thenReturn(List.of());
        when(categorizationService.categorize(any())).thenReturn(FailureCategory.WRONG_ASSERTION);
        when(promptBuilderService.buildPrompt(any(), any(), any(), any())).thenReturn("mock-prompt");
        when(claudeApiService.getFixSuggestion(any(), any(), any())).thenReturn(buildMockFix(
                "testUserRegistrationWrongAssertion_IntendedToFail",
                FailureCategory.WRONG_ASSERTION, "HIGH", 0.92, true));

        // Act + Assert
        mockMvc.perform(post("/api/fix-suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFailed").value(4))
                .andExpect(jsonPath("$.totalProcessed").value(4))
                .andExpect(jsonPath("$.fixes").isArray())
                .andExpect(jsonPath("$.analysedAt").exists());
    }

    // ─────────────────────────────────────────────────────────────────
    // CONTRACT_VIOLATION: 400 bad request → fix in TEST
    // ─────────────────────────────────────────────────────────────────

    @Test
    void shouldCategorizeContractViolationCorrectly() throws Exception {
        TestCase contractFail = sampleFailures.get(0); // testUserRegistrationWrongContract

        when(parserService.extractFailures(any())).thenReturn(List.of(contractFail));
        when(embeddingService.generateEmbeddings(any())).thenReturn(Map.of());
        when(embeddingService.findRelevantFiles(any(), any(), anyInt())).thenReturn(List.of());
        when(categorizationService.categorize(contractFail)).thenReturn(FailureCategory.CONTRACT_VIOLATION);
        when(promptBuilderService.buildPrompt(any(), any(), any(), any())).thenReturn("prompt-contract");
        when(claudeApiService.getFixSuggestion(any(), any(), any())).thenReturn(
                buildMockFix(contractFail.getName(), FailureCategory.CONTRACT_VIOLATION, "HIGH", 0.88, true));

        mockMvc.perform(post("/api/fix-suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixes[0].failureCategory").value("CONTRACT_VIOLATION"))
                .andExpect(jsonPath("$.fixes[0].confidence").value("HIGH"))
                .andExpect(jsonPath("$.fixes[0].automationEligible").value(true));
    }

    // ─────────────────────────────────────────────────────────────────
    // INVALID_INPUT_HANDLING: 500 JSON parse error → fix in SERVICE
    // ─────────────────────────────────────────────────────────────────

    @Test
    void shouldCategorizeInvalidInputHandlingCorrectly() throws Exception {
        TestCase parseErrorFail = sampleFailures.get(1); // testOrderCreationInternalError

        when(parserService.extractFailures(any())).thenReturn(List.of(parseErrorFail));
        when(embeddingService.generateEmbeddings(any())).thenReturn(Map.of());
        when(embeddingService.findRelevantFiles(any(), any(), anyInt())).thenReturn(List.of());
        when(categorizationService.categorize(parseErrorFail)).thenReturn(FailureCategory.INVALID_INPUT_HANDLING);
        when(promptBuilderService.buildPrompt(any(), any(), any(), any())).thenReturn("prompt-invalid-input");
        when(claudeApiService.getFixSuggestion(any(), any(), any())).thenReturn(
                buildMockFix(parseErrorFail.getName(), FailureCategory.INVALID_INPUT_HANDLING, "HIGH", 0.91, true));

        mockMvc.perform(post("/api/fix-suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixes[0].failureCategory").value("INVALID_INPUT_HANDLING"))
                .andExpect(jsonPath("$.fixes[0].automationEligible").value(true))
                .andExpect(jsonPath("$.fixes[0].codeChanges[0].filePath").exists());
    }

    // ─────────────────────────────────────────────────────────────────
    // WRONG_ENDPOINT: 404 → fix in TEST (wrong URL path)
    // ─────────────────────────────────────────────────────────────────

    @Test
    void shouldCategorizeWrongEndpointCorrectly() throws Exception {
        TestCase endpointFail = sampleFailures.get(3); // testUserRegistrationWrongEndpoint

        when(parserService.extractFailures(any())).thenReturn(List.of(endpointFail));
        when(embeddingService.generateEmbeddings(any())).thenReturn(Map.of());
        when(embeddingService.findRelevantFiles(any(), any(), anyInt())).thenReturn(List.of());
        when(categorizationService.categorize(endpointFail)).thenReturn(FailureCategory.WRONG_ENDPOINT);
        when(promptBuilderService.buildPrompt(any(), any(), any(), any())).thenReturn("prompt-endpoint");
        when(claudeApiService.getFixSuggestion(any(), any(), any())).thenReturn(
                buildMockFix(endpointFail.getName(), FailureCategory.WRONG_ENDPOINT, "HIGH", 0.95, true));

        mockMvc.perform(post("/api/fix-suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixes[0].failureCategory").value("WRONG_ENDPOINT"))
                .andExpect(jsonPath("$.fixes[0].confidenceScore").value(0.95));
    }

    // ─────────────────────────────────────────────────────────────────
    // Low confidence → automationEligible = false
    // ─────────────────────────────────────────────────────────────────

    @Test
    void shouldSetAutomationEligibleFalseForLowConfidence() throws Exception {
        TestCase anyFail = sampleFailures.get(0);

        when(parserService.extractFailures(any())).thenReturn(List.of(anyFail));
        when(embeddingService.generateEmbeddings(any())).thenReturn(Map.of());
        when(embeddingService.findRelevantFiles(any(), any(), anyInt())).thenReturn(List.of());
        when(categorizationService.categorize(any())).thenReturn(FailureCategory.UNKNOWN);
        when(promptBuilderService.buildPrompt(any(), any(), any(), any())).thenReturn("prompt-low");
        when(claudeApiService.getFixSuggestion(any(), any(), any())).thenReturn(
                buildMockFix(anyFail.getName(), FailureCategory.UNKNOWN, "LOW", 0.40, false));

        mockMvc.perform(post("/api/fix-suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixes[0].confidence").value("LOW"))
                .andExpect(jsonPath("$.fixes[0].automationEligible").value(false));
    }

    // ─────────────────────────────────────────────────────────────────
    // Validation: missing testResultJson → 400
    // ─────────────────────────────────────────────────────────────────

    @Test
    void shouldReturn400WhenTestResultJsonMissing() throws Exception {
        FixSuggestionRequest badRequest = new FixSuggestionRequest();
        // testResultJson intentionally left null

        mockMvc.perform(post("/api/fix-suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ─────────────────────────────────────────────────────────────────
    // No failures in report → empty fixes list, totalFailed = 0
    // ─────────────────────────────────────────────────────────────────

    @Test
    void shouldReturnEmptyFixesWhenNoFailures() throws Exception {
        when(parserService.extractFailures(any())).thenReturn(List.of());
        when(embeddingService.generateEmbeddings(any())).thenReturn(Map.of());

        mockMvc.perform(post("/api/fix-suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFailed").value(0))
                .andExpect(jsonPath("$.fixes").isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Builds a failed TestCase with the given name, message, and stack trace.
     */
    private TestCase buildFailedTest(String name, String message, String stackTrace) {
        TestCase tc = new TestCase();
        tc.setName(name);
        tc.setStatus("FAILED");
        TestFailure failure = new TestFailure();
        failure.setMessage(message);
        failure.setType("java.lang.AssertionError");
        failure.setStackTrace(stackTrace);
        tc.setFailure(failure);
        return tc;
    }

    /**
     * Builds a mock FailureFix for stubbing ClaudeApiService responses.
     */
    private FailureFix buildMockFix(String testName, FailureCategory category,
                                    String confidence, double score, boolean eligible) {
        return FailureFix.builder()
                .testName(testName)
                .failureCategory(category)
                .rootCause("Mock root cause for " + category)
                .explanation("Mock Claude explanation")
                .fixLocation("TEST")
                .codeChanges(List.of(
                        CodeChange.builder()
                                .filePath("src/test/java/.../MicroservicesIntegrationTest.java")
                                .changeDescription("Update assertion to match actual service contract")
                                .suggestedCode("assertEquals(200, response.getStatusCode());")
                                .build()
                ))
                .confidence(confidence)
                .confidenceScore(score)
                .automationEligible(eligible)
                .build();
    }
}